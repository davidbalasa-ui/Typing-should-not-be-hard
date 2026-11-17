/*
 * Copyright (C) 2026 TypeBetter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Whether dictation can work right now, and the three Whisper models offered when it cannot (TypeBetter).
 *
 * A fresh install transcribes on the device, so until a voice model is downloaded the mic is drawn greyed
 * out, and tapping it opens a picker of Tiny, Base and Small instead of starting a recording that could
 * only fail. Picking one downloads it and makes it the active engine, so dictation then works offline with
 * nothing else to set up. Someone with a cloud API key in place is never sent through this.
 *
 * Plain Kotlin on purpose, so every rule here is unit tested without Android (DictationReadinessTest).
 */
object DictationReadiness {

    /**
     * True when the active transcription provider [activeProviderId] cannot transcribe yet: the on-device
     * engine without a model on disk, or a provider that needs a key without one.
     *
     * The same two checks the controller makes when the mic is pressed ([ProviderListing.isSetUp] for the
     * on-device engine; `requiresCredential && !hasKey` for the rest, as in `refuseIfNoCredential`), so the
     * greyed-out mic and the error a press would have produced never disagree. A custom endpoint and a
     * keyless server (Ollama) count as ready, exactly as they do there.
     */
    fun needsSetup(
        activeProviderId: String,
        accounts: ProviderAccounts,
        isModelInstalled: (String) -> Boolean,
    ): Boolean {
        val preset = ProviderRegistry.byId(activeProviderId)
        if (preset != null && preset.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE) {
            return !ProviderListing.isSetUp(preset, accounts, isModelInstalled)
        }
        val account = accounts.getOrEmpty(activeProviderId)
        return account.requiresCredential && !account.hasKey
    }

    /**
     * The keyring after pointing the on-device engine at [modelId]. The caller also makes
     * [ProviderRegistry.LOCAL] the active transcription provider; this is the setup wizard's own recipe
     * (SetupScreen.activateLocalModel), kept in one place so the picker and the wizard cannot drift.
     */
    fun withLocalModel(accounts: ProviderAccounts, modelId: String): ProviderAccounts =
        accounts.edit(ProviderRegistry.LOCAL.id) { it.copy(transcriptionModel = modelId) }

    /**
     * Whether this is a fresh install, for the one-time switch to the on-device engine
     * (DictateLegacyMigrator.adoptLocalDefaultOnFreshInstallIfNeeded): setup not finished and no API key
     * stored for any provider. A keyboard already in use keeps the provider it had, whatever it is: the
     * stored setting cannot tell "never touched" from "chosen", so it is not second-guessed.
     */
    fun isFreshInstall(isImeSetUp: Boolean, accounts: ProviderAccounts): Boolean =
        !isImeSetUp && accounts.accounts.values.none { it.hasKey }

    // --- The picker's three choices ------------------------------------------------------------------

    /** Steps on the speed and accuracy bars. */
    const val SCALE = 5

    /**
     * One row of the picker. [speed] and [accuracy] are on a 1..[SCALE] scale and are relative to each
     * other, not absolute claims: Whisper's published figures put Tiny at roughly twice Base's speed and
     * Small at well under half of it, with the error rate falling in the same order.
     */
    data class Choice(
        val spec: LocalModelSpec,
        val speed: Int,
        val accuracy: Int,
        val recommended: Boolean,
    ) {
        val megabytes: Int get() = megabytesOf(spec.totalBytes)
    }

    /** Tiny, Base and Small, smallest first, Base recommended. Multilingual models, so any language works. */
    val choices: List<Choice> = listOf(
        Choice(LocalModelCatalog.WHISPER_TINY, speed = 5, accuracy = 2, recommended = false),
        Choice(LocalModelCatalog.WHISPER_BASE, speed = 4, accuracy = 3, recommended = true),
        Choice(LocalModelCatalog.WHISPER_SMALL, speed = 2, accuracy = 5, recommended = false),
    )

    val recommended: Choice get() = choices.single { it.recommended }

    /** How full the size bar is for [choice]: its download against the biggest one offered. */
    fun sizeFraction(choice: Choice): Float =
        choice.spec.totalBytes.toFloat() / choices.maxOf { it.spec.totalBytes }.toFloat()

    /** Whole megabytes (10^6 bytes, as phone storage counts them), rounded. */
    fun megabytesOf(bytes: Long): Int = ((bytes + 500_000L) / 1_000_000L).toInt()

    /**
     * Whether [usableBytes] of free storage can take [spec]. The download is staged next to the final
     * folder and then moved, so it needs its own size plus a margin for the rest of the phone to keep
     * working.
     */
    fun hasRoomFor(spec: LocalModelSpec, usableBytes: Long): Boolean =
        usableBytes >= spec.totalBytes + STORAGE_MARGIN_BYTES

    /** Left free after a download, so the phone is not filled to the last byte. */
    const val STORAGE_MARGIN_BYTES: Long = 50L * 1_000_000L

    // --- Why a download failed -----------------------------------------------------------------------

    /** What a failed download tells the user to do. */
    enum class Failure {
        /** No connection, or it dropped: connect and retry. The app has no network-state permission. */
        NETWORK,
        /** The phone ran out of space. */
        STORAGE,
        /** Anything else (a corrupted file failing its checksum, a server error): retry. */
        OTHER,
    }

    /** Sorts a download exception into a [Failure], looking through its causes. */
    fun classify(t: Throwable): Failure {
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 8) {
            val msg = cur.message.orEmpty()
            if (msg.contains("ENOSPC") || msg.contains("No space left", ignoreCase = true)) return Failure.STORAGE
            when (cur) {
                is UnknownHostException, is ConnectException, is NoRouteToHostException,
                is SocketTimeoutException, is SocketException, is SSLException -> return Failure.NETWORK
            }
            // OkHttp reports a body cut short by a dropped connection as a plain IOException.
            if (cur is IOException && (msg.contains("unexpected end of stream", ignoreCase = true) ||
                    msg.contains("connection", ignoreCase = true))
            ) return Failure.NETWORK
            cur = cur.cause
            depth++
        }
        return Failure.OTHER
    }
}
