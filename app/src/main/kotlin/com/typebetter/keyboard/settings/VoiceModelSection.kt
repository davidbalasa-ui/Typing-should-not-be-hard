package com.typebetter.keyboard.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typebetter.keyboard.PersonalWords
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.customWordList
import dev.patrickgold.florisboard.dictate.provider.DictationReadiness
import dev.patrickgold.florisboard.dictate.provider.LocalModelDownloads
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.ui.deviceLanguage
import dev.patrickgold.florisboard.dictate.ui.useDownloadedModel
import dev.patrickgold.florisboard.dictate.ui.useModel
import dev.patrickgold.jetpref.datastore.model.collectAsState as collectPrefAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes

/**
 * The top of the Dictation settings (David, 2026-11-26): the voice models for the dictation language
 * (Parakeet first for English, David 2026-11-27; Whisper Tiny, Base and Small otherwise), with real
 * sizes, speed and accuracy bars and the download state, plus the names dictation listens for. The same
 * models and the same switch-over as the keyboard's setup sheet (DictationSetupPanel), so either place
 * can be used and they always agree. Every other on-device model sits under AI providers, On-device.
 */
@Composable
internal fun VoiceModelSection(onOpenWords: () -> Unit) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val providerId by prefs.dictate.transcriptionProviderId.collectPrefAsState()
    val accounts by prefs.dictate.providerAccounts.collectPrefAsState()
    val activeLanguage by prefs.dictate.activeInputLanguage.collectPrefAsState()
    val downloads by LocalModelDownloads.state.collectAsState()
    val installedTick by LocalModelDownloads.installedTick.collectAsState()
    val installed = remember(installedTick) {
        DictationReadiness.choices.filter { LocalModelManager.isInstalled(context, it.spec.id) }.map { it.spec.id }.toSet()
    }
    val activeId = if (providerId == ProviderRegistry.LOCAL.id) {
        accounts.getOrEmpty(ProviderRegistry.LOCAL.id).transcriptionModel
    } else {
        null
    }
    val language = DictationReadiness.pickerLanguage(activeLanguage, deviceLanguage(context))
    val shown = remember(language, installed) { DictationReadiness.visibleChoices(language, installed) }
    val pickerIds = DictationReadiness.choices.map { it.spec.id }.toSet()
    val activeDownload = downloads.values.firstOrNull { it.modelId in pickerIds && it.error == null }
    val failed = downloads.values.firstOrNull { it.modelId in pickerIds && it.error != null }
    var noRoom by remember { mutableStateOf<Pair<DictationReadiness.Choice, Int>?>(null) }

    fun onPick(choice: DictationReadiness.Choice) {
        val id = choice.spec.id
        val state = downloads[id]
        when {
            state != null && state.error == null -> LocalModelDownloads.cancel(id)
            activeDownload != null -> Unit // one download at a time; the hint below says so
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
                downloads.values.filter { it.error != null && it.modelId in pickerIds }
                    .forEach { LocalModelDownloads.clearError(it.modelId) }
                val appContext = context.applicationContext
                LocalModelDownloads.start(appContext, choice.spec) { useDownloadedModel(appContext, choice.spec) }
            }
        }
    }

    TbSectionLabel(stringRes(R.string.typebetter__voice_model_section))
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { choice ->
            val id = choice.spec.id
            val state = downloads[id]
            val status = when {
                state != null && state.error == null ->
                    stringRes(R.string.typebetter__voice_downloading, "percent" to state.percent)
                state?.error != null -> stringRes(R.string.typebetter__voice_failed)
                id == activeId && id in installed -> stringRes(R.string.typebetter__voice_in_use)
                id in installed -> stringRes(R.string.typebetter__voice_downloaded)
                else -> stringRes(R.string.typebetter__voice_download, "mb" to choice.megabytes)
            }
            ModelChoice(
                choice = choice,
                selected = id == activeId && id in installed,
                status = status,
                onClick = { onPick(choice) },
            )
        }
        val message = when {
            noRoom != null -> {
                val (choice, freeMb) = noRoom!!
                stringRes(
                    R.string.dictate__setup_no_room,
                    "model" to choice.spec.displayName,
                    "needed" to DictationReadiness.megabytesOf(choice.spec.totalBytes + DictationReadiness.STORAGE_MARGIN_BYTES),
                    "free" to freeMb,
                )
            }
            failed != null -> when (failed.failure) {
                DictationReadiness.Failure.NETWORK -> stringRes(R.string.dictate__setup_failed_network)
                DictationReadiness.Failure.STORAGE -> stringRes(R.string.dictate__setup_failed_storage)
                else -> stringRes(R.string.dictate__setup_failed_other)
            }
            activeDownload != null -> stringRes(R.string.typebetter__voice_downloading_hint)
            // An online service is in use: dictation works, these models are the offline alternative.
            providerId != ProviderRegistry.LOCAL.id -> stringRes(R.string.typebetter__voice_online_hint)
            // Another on-device model (picked under Providers) counts as ready too.
            (activeId != null && activeId in installed) || (!activeId.isNullOrBlank() && activeId !in pickerIds) ->
                stringRes(R.string.typebetter__voice_offline_hint)
            else -> stringRes(R.string.typebetter__voice_pick_hint)
        }
        Text(message, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    NamesItListensFor(onOpenWords = onOpenWords)
}

@Composable
private fun ModelChoice(
    choice: DictationReadiness.Choice,
    selected: Boolean,
    status: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerLowest)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.secondary else scheme.outlineVariant),
                shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(choice.spec.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                if (choice.recommended) {
                    Text(
                        text = stringRes(
                            if (choice.englishOnly) R.string.typebetter__voice_best_english
                            else R.string.typebetter__voice_recommended,
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimary,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                val languages = stringRes(
                    if (choice.englishOnly) R.string.typebetter__voice_english_only
                    else R.string.typebetter__voice_any_language,
                )
                Text("$languages · $status", fontSize = 13.sp, color = scheme.onSurfaceVariant)
            }
            Text(
                text = stringRes(R.string.typebetter__voice_size, "mb" to choice.megabytes),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LevelBar(stringRes(R.string.typebetter__voice_speed), choice.speed, Modifier.weight(1f))
            LevelBar(stringRes(R.string.typebetter__voice_accuracy), choice.accuracy, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LevelBar(label: String, level: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 11.sp, color = scheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.outlineVariant),
        ) {
            val f = (level.toFloat() / DictationReadiness.SCALE).coerceIn(0.05f, 1f)
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(scheme.primary))
        }
    }
}

/** The words dictation is primed with: what the transcription prompt really carries, first ones first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NamesItListensFor(onOpenWords: () -> Unit) {
    val prefs by FlorisPreferenceStore
    val changes by PersonalWords.changes.collectAsState()
    val customWords by prefs.dictate.customWords.collectPrefAsState()
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(changes, customWords) {
        names = withContext(Dispatchers.IO) { runCatching { customWordList(prefs) }.getOrDefault(emptyList()) }
    }
    TbSectionLabel(stringRes(R.string.typebetter__voice_names_section))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            text = stringRes(R.string.typebetter__voice_names_summary),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            names.take(MAX_NAMES_SHOWN).forEach { name ->
                Text(
                    text = name,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringRes(R.string.typebetter__voice_names_add),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = onOpenWords)
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 12.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (names.size > MAX_NAMES_SHOWN) {
            Text(
                text = stringRes(R.string.typebetter__voice_names_more, "count" to (names.size - MAX_NAMES_SHOWN)),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_NAMES_SHOWN = 16
