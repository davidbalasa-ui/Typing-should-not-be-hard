/*
 * Copyright (C) 2026 TypeBetter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.provider.DictationReadiness
import dev.patrickgold.florisboard.dictate.provider.LocalModelDownloads
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.LocalModelSpec
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState as collectPrefAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.onAccent
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.curlyFormat
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Whether the keyboard's mic should be drawn greyed out because dictation cannot work yet, observed so it
 * changes the moment a model download finishes, a model is deleted, or a provider or key changes.
 * See [DictationReadiness.needsSetup].
 */
@Composable
fun rememberDictationNeedsSetup(): Boolean {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val activeId by prefs.dictate.transcriptionProviderId.collectPrefAsState()
    val accounts by prefs.dictate.providerAccounts.collectPrefAsState()
    val installedTick by LocalModelDownloads.installedTick.collectAsState()
    return remember(activeId, accounts, installedTick) {
        DictationReadiness.needsSetup(activeId, accounts) { LocalModelManager.isInstalled(context, it) }
    }
}

/**
 * The keyboard's dictation setup sheet (TypeBetter): Whisper Tiny, Base and Small side by side, each with
 * bar graphs for speed, accuracy and size, Base marked recommended. A tap on a card downloads that model
 * and, once it is on disk, makes it the active engine — the sheet can be closed meanwhile, the download
 * and the switch-over carry on in the app's own scope. Opened by a tap on the greyed-out mic.
 *
 * Built like the transcription provider sheet next to it ([SelectTranscriptionProviderPanel]): same
 * bottom sheet, same header, the panel's own themed colours, so it looks like part of the keyboard.
 */
@Composable
fun DictationSetupPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val keyboardManager by context.keyboardManager()
    val inputFeedbackController = LocalInputFeedbackController.current
    val scope = rememberCoroutineScope()
    val accent by prefs.theme.accentColor.collectPrefAsState()

    val downloads by LocalModelDownloads.state.collectAsState()
    val installedTick by LocalModelDownloads.installedTick.collectAsState()
    val installed = remember(installedTick) {
        DictationReadiness.choices.filter { LocalModelManager.isInstalled(context, it.spec.id) }.map { it.spec.id }.toSet()
    }
    val needsSetup = rememberDictationNeedsSetup()
    /** The choice the phone had no room for, with the free megabytes measured at the tap. */
    var noRoom by remember { mutableStateOf<Pair<DictationReadiness.Choice, Int>?>(null) }

    fun close() {
        keyboardManager.activeState.isDictationSetupVisible = false
    }

    // Dictation works now (a model landed, or a key was added elsewhere): the job of this sheet is done.
    LaunchedEffect(needsSetup) {
        if (!needsSetup) close()
    }

    val pickerIds = DictationReadiness.choices.map { it.spec.id }.toSet()
    val activeDownload = downloads.values.firstOrNull { it.modelId in pickerIds && it.error == null }
    val failed = downloads.values.firstOrNull { it.modelId in pickerIds && it.error != null }

    fun onCardTap(choice: DictationReadiness.Choice) {
        val id = choice.spec.id
        val state = downloads[id]
        when {
            state != null && state.error == null -> LocalModelDownloads.cancel(id)
            activeDownload != null -> Unit // another model is downloading; the card says so
            id in installed -> {
                noRoom = null
                scope.launch { useModel(choice.spec) }
            }
            else -> {
                val free = context.filesDir.usableSpace
                if (!DictationReadiness.hasRoomFor(choice.spec, free)) {
                    noRoom = choice to DictationReadiness.megabytesOf(free)
                    return
                }
                noRoom = null
                // A retry is a fresh start: the manager restarts each file from its first byte.
                downloads.values.filter { it.error != null && it.modelId in pickerIds }
                    .forEach { LocalModelDownloads.clearError(it.modelId) }
                val appContext = context.applicationContext
                LocalModelDownloads.start(appContext, choice.spec) {
                    useDownloadedModel(appContext, choice.spec)
                }
            }
        }
    }

    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    SnyggColumn(FlorisImeUi.SubtypePanel.elementName, modifier = modifier.safeDrawingPadding()) {
        SnyggRow(
            elementName = FlorisImeUi.SmartbarActionsEditorHeader.elementName,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(48.dp)) {
                SnyggIconButton(
                    elementName = FlorisImeUi.SmartbarActionsEditorHeaderButton.elementName,
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    onClick = { close() },
                ) {
                    SnyggIcon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                }
            }
            SnyggText(
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.dictate__setup_title),
            )
            Spacer(Modifier.size(48.dp))
        }

        SnyggBox(FlorisImeUi.SubtypePanelList.elementName) {
            val content = LocalContentColor.current
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringRes(R.string.dictate__setup_intro),
                    color = content.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DictationReadiness.choices.forEach { choice ->
                        val id = choice.spec.id
                        val state = downloads[id]
                        val status = when {
                            state != null && state.error == null -> CardStatus.Downloading(state.percent)
                            state?.error != null -> CardStatus.Failed
                            activeDownload != null -> CardStatus.Busy
                            id in installed -> CardStatus.Installed
                            else -> CardStatus.Idle
                        }
                        ModelCard(
                            choice = choice,
                            status = status,
                            accent = accent,
                            content = content,
                            onTap = {
                                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                                onCardTap(choice)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val message = when {
                    noRoom != null -> {
                        val (choice, freeMb) = noRoom!!
                        stringRes(
                            R.string.dictate__setup_no_room,
                            "model" to choice.spec.displayName,
                            "needed" to DictationReadiness.megabytesOf(
                                choice.spec.totalBytes + DictationReadiness.STORAGE_MARGIN_BYTES,
                            ),
                            "free" to freeMb,
                        )
                    }
                    failed != null -> when (failed.failure) {
                        DictationReadiness.Failure.NETWORK -> stringRes(R.string.dictate__setup_failed_network)
                        DictationReadiness.Failure.STORAGE -> stringRes(R.string.dictate__setup_failed_storage)
                        else -> stringRes(R.string.dictate__setup_failed_other)
                    }
                    activeDownload != null -> stringRes(R.string.dictate__setup_background_hint)
                    else -> null
                }
                if (message != null) {
                    Text(
                        text = message,
                        color = content,
                        fontSize = 13.sp,
                        modifier = Modifier.semantics { contentDescription = message },
                    )
                }
                Text(
                    text = stringRes(R.string.dictate__setup_use_cloud),
                    color = content.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                            close()
                            DictateController.openProviderSettings(context, addNew = true)
                        }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** What a model card shows under its bars. */
private sealed interface CardStatus {
    data object Idle : CardStatus
    data object Installed : CardStatus
    data object Busy : CardStatus
    data object Failed : CardStatus
    data class Downloading(val percent: Int) : CardStatus
}

@Composable
private fun ModelCard(
    choice: DictationReadiness.Choice,
    status: CardStatus,
    accent: Color,
    content: Color,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val track = content.copy(alpha = 0.16f)
    val name = choice.spec.displayName.removePrefix("Whisper ").ifBlank { choice.spec.displayName }
    val blurb = stringRes(
        when (choice.spec.id) {
            "whisper-tiny" -> R.string.dictate__setup_blurb_tiny
            "whisper-small" -> R.string.dictate__setup_blurb_small
            else -> R.string.dictate__setup_blurb_base
        },
    )
    val speedLabel = stringRes(R.string.dictate__setup_speed)
    val accuracyLabel = stringRes(R.string.dictate__setup_accuracy)
    val sizeLabel = stringRes(R.string.dictate__setup_size)
    val sizeText = stringRes(R.string.dictate__setup_size_mb, "mb" to choice.megabytes)
    val statusText = when (status) {
        CardStatus.Idle -> stringRes(R.string.dictate__setup_action_download)
        CardStatus.Installed -> stringRes(R.string.dictate__setup_action_use)
        CardStatus.Busy -> stringRes(R.string.dictate__setup_busy)
        CardStatus.Failed -> stringRes(R.string.dictate__setup_action_retry)
        is CardStatus.Downloading -> stringRes(R.string.dictate__setup_action_cancel)
    }
    val recommendedLabel = stringRes(R.string.dictate__setup_recommended)
    val levelA11y = stringRes(R.string.dictate__setup_level_a11y)
    val a11y = buildList {
        add(choice.spec.displayName)
        if (choice.recommended) add(recommendedLabel)
        add(blurb)
        add(levelA11y.curlyFormat("label" to speedLabel, "level" to choice.speed, "max" to DictationReadiness.SCALE))
        add(levelA11y.curlyFormat("label" to accuracyLabel, "level" to choice.accuracy, "max" to DictationReadiness.SCALE))
        add("$sizeLabel: $sizeText")
        if (status is CardStatus.Downloading) add("${status.percent}%")
        add(statusText)
    }.joinToString(". ")
    val dimmed = status == CardStatus.Busy

    Column(
        modifier = modifier
            .clip(shape)
            .border(
                width = if (choice.recommended) 2.dp else 1.dp,
                color = if (choice.recommended) accent else content.copy(alpha = 0.25f),
                shape = shape,
            )
            .clickable(enabled = !dimmed, role = Role.Button, onClick = onTap)
            .clearAndSetSemantics { contentDescription = a11y }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val textAlpha = if (dimmed) 0.45f else 1f
        // The badge row is always there, empty on the other two, so the three names line up.
        Box(Modifier.fillMaxWidth().height(20.dp)) {
            if (choice.recommended) {
                Text(
                    text = recommendedLabel,
                    color = accent.onAccent(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = name,
            color = content.copy(alpha = textAlpha),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = blurb,
            color = content.copy(alpha = 0.75f * textAlpha),
            fontSize = 12.sp,
            maxLines = 2,
            modifier = Modifier.heightIn(min = 30.dp),
        )
        BarLabel(speedLabel, content, textAlpha)
        LevelBar(choice.speed, accent.copy(alpha = textAlpha), track)
        BarLabel(accuracyLabel, content, textAlpha)
        LevelBar(choice.accuracy, accent.copy(alpha = textAlpha), track)
        // Size is drawn in the neutral text colour, not the accent: more of it is not better.
        Row(verticalAlignment = Alignment.Bottom) {
            BarLabel(sizeLabel, content, textAlpha, Modifier.weight(1f))
            Text(text = sizeText, color = content.copy(alpha = 0.75f * textAlpha), fontSize = 11.sp, maxLines = 1)
        }
        FractionBar(DictationReadiness.sizeFraction(choice), content.copy(alpha = 0.6f * textAlpha), track)
        Spacer(Modifier.height(2.dp))
        if (status is CardStatus.Downloading) {
            FractionBar(status.percent / 100f, accent, track, minFraction = 0f)
            Text(
                text = stringRes(R.string.dictate__setup_downloading, "percent" to status.percent),
                color = content,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = statusText,
            color = if (status == CardStatus.Failed) accent else content.copy(alpha = 0.85f * textAlpha),
            fontSize = 12.sp,
            fontWeight = if (status == CardStatus.Idle || status == CardStatus.Installed) FontWeight.Bold else null,
            textAlign = TextAlign.Start,
            maxLines = 2,
        )
    }
}

@Composable
private fun BarLabel(text: String, content: Color, alpha: Float, modifier: Modifier = Modifier) {
    Text(text = text, color = content.copy(alpha = 0.8f * alpha), fontSize = 11.sp, maxLines = 1, modifier = modifier)
}

/** [level] filled segments out of [DictationReadiness.SCALE]. */
@Composable
private fun LevelBar(level: Int, fill: Color, track: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(DictationReadiness.SCALE) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < level) fill else track),
            )
        }
    }
}

/** A continuous bar filled to [fraction]; a sliver always shows so the smallest size still reads. */
@Composable
private fun FractionBar(fraction: Float, fill: Color, track: Color, minFraction: Float = 0.04f) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(track),
    ) {
        val f = fraction.coerceIn(minFraction, 1f)
        if (f > 0f) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(fill))
        }
    }
}

/**
 * Points the on-device engine at [spec] and makes it the active transcription provider, the setup
 * wizard's own recipe ([DictationReadiness.withLocalModel]).
 */
private suspend fun useModel(spec: LocalModelSpec) {
    val prefs by FlorisPreferenceStore
    prefs.dictate.providerAccounts.set(DictationReadiness.withLocalModel(prefs.dictate.providerAccounts.get(), spec.id))
    prefs.dictate.transcriptionProviderId.set(ProviderRegistry.LOCAL.id)
}

/**
 * [useModel] once a download has finished, perhaps with the sheet long closed, on the main thread, plus a
 * toast saying dictation is ready.
 */
private suspend fun useDownloadedModel(context: Context, spec: LocalModelSpec) {
    withContext(Dispatchers.Main) {
        useModel(spec)
        val text = context.getString(R.string.dictate__setup_ready_toast).curlyFormat("model" to spec.displayName)
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
