/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.typebetter.keyboard.PersonalWords
import com.typebetter.keyboard.settings.CheckForUpdatesRow
import com.typebetter.keyboard.settings.TbDisplayTitle
import com.typebetter.keyboard.settings.TbIntro
import com.typebetter.keyboard.settings.TbRow
import com.typebetter.keyboard.settings.TbSectionLabel
import com.typebetter.keyboard.settings.TbSwitchRow
import com.typebetter.keyboard.settings.TryKeyboardCard
import com.typebetter.keyboard.settings.WordPeek
import com.typebetter.keyboard.settings.buildWordEntries
import com.typebetter.keyboard.settings.exampleFix
import com.typebetter.keyboard.settings.peekEntries
import dev.patrickgold.florisboard.dictate.provider.LocalModelCatalog
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.ui.rememberDictationNeedsSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.PreferenceRecovery
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.data.stats.DictateStats
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.IconButton
import dev.patrickgold.florisboard.dictate.importer.TranscribeShareActivity
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import java.text.NumberFormat
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    // The "Try your keyboard" card below is the test box here (David, 2026-11-26).
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    actions {
        FlorisIconButton(
            onClick = { navController.navigate(Routes.Settings.Search) },
            icon = Icons.Default.Search,
        )
    }

    content {
        val isCollapsed by prefs.internal.homeIsBetaToolboxCollapsed.collectAsState()

        val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        if (!isFlorisBoardEnabled) {
            FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_enabled),
                onClick = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isFlorisBoardSelected) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_selected),
                onClick = { InputMethodUtils.showImePicker(context) },
            )
        }

        // Settings that could not be read at start were kept instead of written over (#383). This is the
        // way back to them, offered until they are restored or discarded.
        val settingsUnreadable by PreferenceRecovery.pending.collectAsState()
        var showRecoveryDialog by remember { mutableStateOf(false) }
        val recoveryScope = rememberCoroutineScope()
        if (settingsUnreadable) {
            FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.dictate__settings_unreadable_card),
                onClick = { showRecoveryDialog = true },
            )
        }
        if (showRecoveryDialog) {
            JetPrefAlertDialog(
                title = stringRes(R.string.dictate__settings_unreadable_title),
                confirmLabel = stringRes(R.string.action__restore),
                onConfirm = {
                    showRecoveryDialog = false
                    recoveryScope.launch {
                        PreferenceRecovery.restore(context).onFailure {
                            context.showLongToast(R.string.dictate__settings_restore_failed)
                        }
                    }
                },
                // Delete, not Discard: several translations of Discard read the same as Cancel beside it.
                neutralLabel = stringRes(R.string.action__delete),
                onNeutral = {
                    showRecoveryDialog = false
                    PreferenceRecovery.discard(context)
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showRecoveryDialog = false },
            ) {
                Text(stringRes(R.string.dictate__settings_unreadable_message))
            }
        }

        // TypeBetter: the personal home (David, 2026-11-26; docs/decisions/2026-11-26-personal-settings-page.md).
        // His words and voice first, the keyboard switches next, everything else folded below. Nothing that
        // was here is gone: each old row is still on this screen or one tap away.
        val rules by PersonalWords.rules.collectAsState()
        val wordChanges by PersonalWords.changes.collectAsState()
        var myWords by remember { mutableStateOf(PersonalWords.myWordsCached()) }
        LaunchedEffect(wordChanges) {
            myWords = withContext(Dispatchers.IO) { PersonalWords.myWords() }
        }
        val wordEntries = remember(myWords, rules) { buildWordEntries(myWords, rules) }

        TbDisplayTitle(
            text = stringRes(R.string.typebetter__home_greeting),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
        TbIntro(
            text = stringRes(R.string.typebetter__home_intro),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        )
        TryKeyboardCard(
            example = remember(rules.fixes) { exampleFix(rules.fixes) },
            onAddFix = { navController.navigate(Routes.Settings.TypeBetterCorrections) },
            onOpenWords = { navController.navigate(Routes.Settings.TypeBetterWords) },
        )

        // Passive dictation-stats summary (issue #142): appears once the user has dictated, taps through
        // to the full statistics screen. No interruption — just a glanceable "time saved".
        val statDictations by prefs.dictate.statsDictations.collectAsState()
        val statWords by prefs.dictate.statsWords.collectAsState()
        val statSpoken by prefs.dictate.statsSpokenSeconds.collectAsState()
        if (statDictations > 0L) {
            val saved = DictateStats.savedSeconds(statWords, statSpoken)
            Card(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .clickable { navController.navigate(Routes.Settings.DictateStats) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // Small "saved" label so it's clear the figure is time saved, not usage time.
                        Text(
                            stringRes(R.string.dictate__stats_time_saved),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                        // Emphasized time + count on one line.
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                homeDuration(saved.toLong()),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "· ${NumberFormat.getIntegerInstance().format(statDictations)} ${stringRes(R.string.dictate__stats_dictations)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }

        // Milestone celebrations are shown on the keyboard (Smartbar nudge), consistent with rate/donate
        // (issue #142) — see DictateController.showMilestoneNudge. Not surfaced here.

        /*Card(modifier = Modifier.padding(8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Welcome to the 0.4 alpha series!",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1.0f))
                    IconButton(onClick = { this@content.prefs.internal.homeIsBetaToolboxCollapsed.set(!isCollapsed) }) {
                        Icon(
                            painter = painterResource(if (isCollapsed) {
                                R.drawable.ic_keyboard_arrow_down
                            } else {
                                R.drawable.ic_keyboard_arrow_up
                            }),
                            contentDescription = null,
                        )
                    }
                }
                if (!isCollapsed) {
                    Text("0.4 will be quite a big release and finally work on adding support for word suggestion and inline autocorrect within the keyboard UI, at first for Latin-based languages. Additionally general improvements and bug fixes will also be made.\n")
                    Text("Currently the alpha releases are preparations for the suggestions implementation and general improvements and bug fixes.\n")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Note that this release does not contain support for word suggestions (will show the current word plus numbers as a placeholder).", color = Color.Red)
                    Text("Please DO NOT file an issue for this. It is already more than known and a major goal for implementation in 0.4.0. Thank you!\n")
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }*/
        // "Transcribe a file" (issue #301) reachable in one tap from the top, without going through
        // the Dictate screen first. The labelled row stays there as well: this icon is what the
        // settings search cannot find and what nobody discovers who does not already know about it.
        val dictateContext = LocalContext.current
        val transcribePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                dictateContext.startActivity(TranscribeShareActivity.intentFor(dictateContext, uri))
            }
        }
        TbSectionLabel(stringRes(R.string.typebetter__home_section_words))
        TbRow(
            title = stringRes(R.string.typebetter__words_title),
            summary = stringRes(
                R.string.typebetter__home_words_summary,
                "words" to wordEntries.size,
                "typos" to rules.fixes.size,
            ),
            icon = Icons.Default.MenuBook,
            onClick = { navController.navigate(Routes.Settings.TypeBetterWords) },
        )
        WordPeek(
            entries = remember(wordEntries) { peekEntries(wordEntries) },
            onClick = { navController.navigate(Routes.Settings.TypeBetterWords) },
        )
        val needsVoiceSetup = rememberDictationNeedsSetup()
        val providerId by prefs.dictate.transcriptionProviderId.collectAsState()
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        val localModelName = remember(providerId, accounts) {
            if (providerId == ProviderRegistry.LOCAL.id) {
                LocalModelCatalog.byId(accounts.getOrEmpty(ProviderRegistry.LOCAL.id).transcriptionModel)?.displayName
            } else {
                null
            }
        }
        TbRow(
            title = stringRes(R.string.dictate__title),
            summary = when {
                needsVoiceSetup -> stringRes(R.string.typebetter__home_voice_setup)
                localModelName != null -> stringRes(R.string.typebetter__home_voice_local, "model" to localModelName)
                else -> stringRes(R.string.typebetter__home_voice_other)
            },
            icon = Icons.Default.Mic,
            onClick = { navController.navigate(Routes.Settings.Dictate) },
            trailing = {
                IconButton(onClick = { transcribePicker.launch(TranscribeShareActivity.MIME_TYPES) }) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = stringRes(R.string.dictate__import_menu),
                    )
                }
            },
        )

        TbSectionLabel(stringRes(R.string.typebetter__home_section_keyboard))
        val homeScope = rememberCoroutineScope()
        val suggestionsOn by prefs.suggestion.enabled.collectAsState()
        val autoCorrectOn by prefs.suggestion.autoCorrect.collectAsState()
        TbSwitchRow(
            title = stringRes(R.string.typebetter__home_autocorrect),
            summary = stringRes(R.string.typebetter__home_autocorrect_summary),
            icon = Icons.Default.Spellcheck,
            // Autocorrect needs suggestions on; turning it on here turns on both, so the switch never lies.
            checked = suggestionsOn && autoCorrectOn,
            onCheckedChange = { on ->
                homeScope.launch {
                    if (on) prefs.suggestion.enabled.set(true)
                    prefs.suggestion.autoCorrect.set(on)
                }
            },
            onOpen = { navController.navigate(Routes.Settings.Typing) },
        )
        val glideOn by prefs.glide.enabled.collectAsState()
        TbSwitchRow(
            title = stringRes(R.string.typebetter__home_glide),
            summary = stringRes(R.string.typebetter__home_glide_summary),
            icon = Icons.Default.Gesture,
            checked = glideOn,
            onCheckedChange = { on -> homeScope.launch { prefs.glide.enabled.set(on) } },
            onOpen = { navController.navigate(Routes.Settings.Gestures) },
        )
        val nextWordOn by prefs.suggestion.nextWordPrediction.collectAsState()
        TbSwitchRow(
            title = stringRes(R.string.typebetter__home_next_word),
            summary = stringRes(R.string.typebetter__home_next_word_summary),
            icon = Icons.AutoMirrored.Filled.ShortText,
            checked = nextWordOn,
            onCheckedChange = { on -> homeScope.launch { prefs.suggestion.nextWordPrediction.set(on) } },
            onOpen = { navController.navigate(Routes.Settings.Typing) },
        )
        val gifOn by prefs.gif.enabled.collectAsState()
        TbSwitchRow(
            title = stringRes(R.string.typebetter__home_gif),
            summary = stringRes(R.string.typebetter__home_gif_summary),
            icon = Icons.Outlined.Gif,
            checked = gifOn,
            onCheckedChange = { on -> homeScope.launch { prefs.gif.enabled.set(on) } },
            onOpen = { navController.navigate(Routes.Settings.Media) },
        )
        TbRow(
            title = stringRes(R.string.typebetter__kb_title),
            summary = stringRes(R.string.typebetter__kb_home_summary),
            icon = Icons.Default.SportsEsports,
            onClick = { navController.navigate(Routes.Settings.TypeBetterKeys) },
        )

        TbSectionLabel(stringRes(R.string.typebetter__home_section_else))
        TbRow(
            title = stringRes(R.string.settings__typing__title),
            icon = Icons.Default.Spellcheck,
            onClick = { navController.navigate(Routes.Settings.Typing) },
        )
        TbRow(
            title = stringRes(R.string.settings__gestures__title),
            icon = Icons.Default.Gesture,
            onClick = { navController.navigate(Routes.Settings.Gestures) },
        )
        TbRow(
            title = stringRes(R.string.settings__localization__title),
            icon = Icons.Default.Language,
            onClick = { navController.navigate(Routes.Settings.Localization) },
        )
        TbRow(
            title = stringRes(R.string.settings__theme__title),
            icon = Icons.Outlined.Palette,
            onClick = { navController.navigate(Routes.Settings.Theme) },
        )
        // The rest one tap away (docs/decisions/2026-11-25-keys-and-buttons-editor.md). Nothing is
        // removed, and the search above still finds every setting wherever it sits.
        var moreSettingsExpanded by rememberSaveable { mutableStateOf(false) }
        TbRow(
            title = stringRes(
                if (moreSettingsExpanded) R.string.typebetter__home_fewer_settings else R.string.typebetter__home_more_settings,
            ),
            summary = if (moreSettingsExpanded) null else stringRes(R.string.typebetter__home_more_settings_summary),
            icon = if (moreSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            onClick = { moreSettingsExpanded = !moreSettingsExpanded },
            trailing = {},
        )
        if (moreSettingsExpanded) {
            TbRow(
                title = stringRes(R.string.settings__keyboard__title),
                icon = Icons.Outlined.Keyboard,
                onClick = { navController.navigate(Routes.Settings.Keyboard) },
            )
            TbRow(
                title = stringRes(R.string.settings__smartbar__title),
                icon = Icons.Default.SmartButton,
                onClick = { navController.navigate(Routes.Settings.Smartbar) },
            )
            TbRow(
                title = stringRes(R.string.settings__clipboard__title),
                icon = Icons.AutoMirrored.Outlined.Assignment,
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            TbRow(
                title = stringRes(R.string.settings__media__title),
                icon = Icons.Default.SentimentSatisfiedAlt,
                onClick = { navController.navigate(Routes.Settings.Media) },
            )
            // With the other tools the Smartbar opens (issue #424): it is one, and not a dictation feature.
            TbRow(
                title = stringRes(R.string.settings__translation__title),
                icon = Icons.Outlined.Translate,
                onClick = { navController.navigate(Routes.Settings.Translation) },
            )
            TbRow(
                title = stringRes(R.string.ext__home__title),
                icon = Icons.Default.Extension,
                onClick = { navController.navigate(Routes.Ext.Home) },
            )
            TbRow(
                title = stringRes(R.string.settings__other__title),
                icon = Icons.Outlined.Build,
                onClick = { navController.navigate(Routes.Settings.Other) },
            )
        }
        // The user guide (Dictate): every feature and setting explained with pictures, on the GitHub wiki.
        TbRow(
            title = stringRes(R.string.settings__help__title),
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            onClick = { context.launchUrl(R.string.dictate__wiki_url) },
        )
        CheckForUpdatesRow()
        TbRow(
            title = stringRes(R.string.about__title),
            summary = stringRes(R.string.typebetter__home_about_summary),
            icon = Icons.Outlined.Info,
            onClick = { navController.navigate(Routes.Settings.About) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Compact duration for the home stats card / milestone text: `3h`, `3h 12m`, `12m`, `45s`. */
private fun homeDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}
