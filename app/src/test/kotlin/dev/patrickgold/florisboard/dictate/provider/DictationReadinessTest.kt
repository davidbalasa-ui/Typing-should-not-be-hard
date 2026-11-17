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

import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the keyboard's mic is greyed out waiting for a voice model, what the Whisper picker offers, and the
 * upgrade rule that keeps an OpenAI user on OpenAI (DictationReadiness).
 */
class DictationReadinessTest {

    private fun accountsOf(vararg accounts: ProviderAccount) =
        ProviderAccounts(accounts.associateBy { it.providerId })

    private val nothingInstalled: (String) -> Boolean = { false }
    private fun only(vararg ids: String): (String) -> Boolean = { it in ids }

    private val local = ProviderRegistry.LOCAL.id
    private val openai = ProviderRegistry.OPENAI.id

    // --- needsSetup ---------------------------------------------------------------------------------

    @Test
    fun `a fresh install on the on-device engine needs a model`() {
        assertTrue(DictationReadiness.needsSetup(local, accountsOf(), nothingInstalled))
    }

    @Test
    fun `the default model being on disk is enough`() {
        // A blank pick means the preset default, Whisper Base.
        assertFalse(DictationReadiness.needsSetup(local, accountsOf(), only("whisper-base")))
    }

    @Test
    fun `a model picked in the picker counts once it is on disk, and not before`() {
        val accounts = DictationReadiness.withLocalModel(accountsOf(), "whisper-small")
        assertTrue(DictationReadiness.needsSetup(local, accounts, only("whisper-base")))
        assertFalse(DictationReadiness.needsSetup(local, accounts, only("whisper-small")))
    }

    @Test
    fun `an installed streaming model also counts, because dictation falls back to it`() {
        val accounts = accountsOf(ProviderAccount(providerId = local, realtimeModel = "kroko-en"))
        assertFalse(DictationReadiness.needsSetup(local, accounts, only("kroko-en")))
    }

    @Test
    fun `a cloud provider with a key is ready, without one it is not`() {
        assertTrue(DictationReadiness.needsSetup(openai, accountsOf(), nothingInstalled))
        assertTrue(DictationReadiness.needsSetup(openai, accountsOf(ProviderAccount(providerId = openai)), nothingInstalled))
        assertFalse(
            DictationReadiness.needsSetup(openai, accountsOf(ProviderAccount(providerId = openai, apiKey = "sk-x")), nothingInstalled),
        )
    }

    // Mirrors the controller's refuseIfNoCredential: a keyless server or the user's own endpoint never asks
    // for a key, so its mic is never greyed out.
    @Test
    fun `keyless and custom providers are never greyed out`() {
        assertFalse(DictationReadiness.needsSetup(ProviderRegistry.OLLAMA.id, accountsOf(), nothingInstalled))
        assertFalse(DictationReadiness.needsSetup("custom:abc", accountsOf(), nothingInstalled))
    }

    // --- withLocalModel -----------------------------------------------------------------------------

    @Test
    fun `picking a model changes only the on-device account's one-shot model`() {
        val before = accountsOf(
            ProviderAccount(providerId = openai, apiKey = "sk-x"),
            ProviderAccount(providerId = local, realtimeModel = "kroko-en"),
        )
        val after = DictationReadiness.withLocalModel(before, "whisper-tiny")
        assertEquals("whisper-tiny", after.getOrEmpty(local).transcriptionModel)
        assertEquals("kroko-en", after.getOrEmpty(local).realtimeModel)
        assertEquals(before[openai], after[openai])
    }

    // --- isFreshInstall ----------------------------------------------------------------------------

    @Test
    fun `a fresh install is switched to the on-device engine`() {
        assertTrue(DictationReadiness.isFreshInstall(isImeSetUp = false, accounts = accountsOf()))
        // An account saved without a key (a keyless server, say) is not a reason to stay off on-device.
        assertTrue(DictationReadiness.isFreshInstall(false, accountsOf(ProviderAccount(providerId = openai))))
    }

    @Test
    fun `a keyboard already in use keeps its provider`() {
        assertFalse(DictationReadiness.isFreshInstall(isImeSetUp = true, accounts = accountsOf()))
    }

    @Test
    fun `a stored key anywhere means someone already set up dictation`() {
        val groq = ProviderRegistry.GROQ.id
        assertFalse(DictationReadiness.isFreshInstall(false, accountsOf(ProviderAccount(providerId = groq, apiKey = "gsk_x"))))
    }

    // --- the picker's choices -----------------------------------------------------------------------

    @Test
    fun `the picker offers Tiny, Base and Small with Base recommended`() {
        assertEquals(
            listOf("whisper-tiny", "whisper-base", "whisper-small"),
            DictationReadiness.choices.map { it.spec.id },
        )
        assertEquals("whisper-base", DictationReadiness.recommended.spec.id)
        // The recommendation is also what a blank on-device pick resolves to.
        assertEquals(ProviderRegistry.LOCAL.defaultTranscriptionModel, DictationReadiness.recommended.spec.id)
    }

    @Test
    fun `the bars trade speed against accuracy and size`() {
        val (tiny, base, small) = DictationReadiness.choices
        assertTrue(tiny.speed > base.speed && base.speed > small.speed)
        assertTrue(tiny.accuracy < base.accuracy && base.accuracy < small.accuracy)
        assertTrue(tiny.spec.totalBytes < base.spec.totalBytes && base.spec.totalBytes < small.spec.totalBytes)
        DictationReadiness.choices.forEach {
            assertTrue(it.speed in 1..DictationReadiness.SCALE && it.accuracy in 1..DictationReadiness.SCALE)
        }
        assertEquals(1f, DictationReadiness.sizeFraction(small))
        assertTrue(DictationReadiness.sizeFraction(tiny) in 0.2f..0.35f)
    }

    // The sizes on the cards come from the files actually downloaded, the VAD included.
    @Test
    fun `the card sizes are the real download sizes`() {
        assertEquals(listOf(104, 161, 376), DictationReadiness.choices.map { it.megabytes })
    }

    @Test
    fun `a download needs its own size plus a margin free`() {
        val base = LocalModelCatalog.WHISPER_BASE
        assertFalse(DictationReadiness.hasRoomFor(base, base.totalBytes))
        assertTrue(DictationReadiness.hasRoomFor(base, base.totalBytes + DictationReadiness.STORAGE_MARGIN_BYTES))
    }

    // --- classify -----------------------------------------------------------------------------------

    @Test
    fun `failures are sorted into what the user can do about them`() {
        assertEquals(DictationReadiness.Failure.NETWORK, DictationReadiness.classify(UnknownHostException("github.com")))
        assertEquals(DictationReadiness.Failure.NETWORK, DictationReadiness.classify(SocketTimeoutException("timeout")))
        assertEquals(
            DictationReadiness.Failure.NETWORK,
            DictationReadiness.classify(IOException("unexpected end of stream on https://github.com/...")),
        )
        assertEquals(
            DictationReadiness.Failure.NETWORK,
            DictationReadiness.classify(RuntimeException("wrapped", UnknownHostException("x"))),
        )
        assertEquals(
            DictationReadiness.Failure.STORAGE,
            DictationReadiness.classify(IOException("write failed: ENOSPC (No space left on device)")),
        )
        assertEquals(
            DictationReadiness.Failure.OTHER,
            DictationReadiness.classify(IllegalStateException("checksum mismatch for encoder.onnx")),
        )
    }

    // --- the greyed-out mic in every bundled theme --------------------------------------------------

    // A theme without the rule would keep the mic in its bright accent while it cannot record.
    @Test
    fun `every bundled theme greys out the mic while it needs setup`() {
        val dir = File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets")
        val sheets = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue(sheets.isNotEmpty(), "no stylesheets found in ${dir.absolutePath}")
        sheets.forEach { sheet ->
            val text = sheet.readText()
            assertTrue("\"smartbar-action-key[code=-214]:disabled\"" in text, "${sheet.name} lacks the disabled mic")
            assertTrue("\"smartbar-action-key-icon[code=-214]:disabled\"" in text, "${sheet.name} lacks the disabled mic icon")
        }
    }
}
