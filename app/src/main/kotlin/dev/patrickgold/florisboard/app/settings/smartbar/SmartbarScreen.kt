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

package dev.patrickgold.florisboard.app.settings.smartbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.smartbar.CandidatesDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.ExtendedActionsPlacement
import dev.patrickgold.florisboard.ime.smartbar.SmartbarLayout
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.stringRes

@Composable
fun SmartbarScreen() = FlorisScreen {
    title = stringRes(R.string.settings__smartbar__title)
    previewFieldVisible = true

    content {
        val navController = LocalNavController.current

        SwitchPreference(
            prefs.smartbar.enabled,
            modifier = Modifier.settingsSearchAnchor("pref__smartbar__enabled__label"),
            title = stringRes(R.string.pref__smartbar__enabled__label),
            summary = stringRes(R.string.pref__smartbar__enabled__summary),
        )
        ListPreference(
            listPref = prefs.smartbar.layout,
            modifier = Modifier.settingsSearchAnchor("pref__smartbar__layout__label"),
            title = stringRes(R.string.pref__smartbar__layout__label),
            entries = enumDisplayEntriesOf(SmartbarLayout::class),
            enabledIf = { prefs.smartbar.enabled isEqualTo true },
        )
        // TypeBetter: the buttons themselves are chosen on the Keys & buttons screen, which draws the bar
        // and lets each button be tapped and changed. Before, the only way in was a reorder grid hidden
        // behind the keyboard's ⋯ menu.
        Preference(
            icon = Icons.Default.SportsEsports,
            title = stringRes(R.string.typebetter__kb_top_bar_entry),
            summary = stringRes(R.string.typebetter__kb_top_bar_entry_summary),
            enabledIf = { prefs.smartbar.enabled isEqualTo true },
            onClick = { navController.navigate(Routes.Settings.TypeBetterKeys) },
        )
        Preference(
            icon = Icons.Default.TouchApp,
            modifier = Modifier.settingsSearchAnchor("settings__smartbar__second_actions__title"),
            title = stringRes(R.string.settings__smartbar__second_actions__title),
            summary = stringRes(R.string.settings__smartbar__second_actions__summary),
            enabledIf = { prefs.smartbar.enabled isEqualTo true },
            onClick = { navController.navigate(Routes.Settings.SmartbarSecondActions) },
        )

        PreferenceGroup(title = stringRes(R.string.pref__smartbar__group_layout_specific__label)) {
            ListPreference(
                prefs.suggestion.displayMode,
                modifier = Modifier.settingsSearchAnchor("pref__suggestion__display_mode__label"),
                title = stringRes(R.string.pref__suggestion__display_mode__label),
                entries = enumDisplayEntriesOf(CandidatesDisplayMode::class),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
                visibleIf = { prefs.smartbar.layout isNotEqualTo SmartbarLayout.ACTIONS_ONLY },
            )
            // Shown in the suggestion strip, so it is offered exactly where that strip exists (issue #335).
            SwitchPreference(
                prefs.smartbar.selectionMetrics,
                modifier = Modifier.settingsSearchAnchor("pref__smartbar__selection_metrics__label"),
                title = stringRes(R.string.pref__smartbar__selection_metrics__label),
                summary = stringRes(R.string.pref__smartbar__selection_metrics__summary),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
                visibleIf = { prefs.smartbar.layout isNotEqualTo SmartbarLayout.ACTIONS_ONLY },
            )
            SwitchPreference(
                prefs.smartbar.flipToggles,
                modifier = Modifier.settingsSearchAnchor("pref__smartbar__flip_toggles__label"),
                title = stringRes(R.string.pref__smartbar__flip_toggles__label),
                summary = stringRes(R.string.pref__smartbar__flip_toggles__summary),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
                visibleIf = {
                    prefs.smartbar.layout isEqualTo SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED ||
                        prefs.smartbar.layout isEqualTo SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED
                },
            )
            ListPreference(
                listPref = prefs.smartbar.extendedActionsPlacement,
                modifier = Modifier.settingsSearchAnchor("pref__smartbar__extended_actions_placement__label"),
                title = stringRes(R.string.pref__smartbar__extended_actions_placement__label),
                entries = enumDisplayEntriesOf(ExtendedActionsPlacement::class),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
                visibleIf = { prefs.smartbar.layout isEqualTo SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED },
            )
        }
    }
}
