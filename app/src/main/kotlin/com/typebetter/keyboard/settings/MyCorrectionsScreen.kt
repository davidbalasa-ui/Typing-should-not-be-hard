package com.typebetter.keyboard.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.typebetter.keyboard.PersonalWords
import com.typebetter.personal.Fix
import com.typebetter.personal.FixMode
import com.typebetter.personal.Source
import com.typebetter.personal.isUsableFix
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * "My corrections": the one-screen way to say "when I type dor, I mean for" (David, 2026-11-25).
 *
 * The same fixes as the My fixes group of [MyWordsScreen], without the rest of that screen: two boxes and
 * an Add button at the top, the list (newest first) below, a tap on a row to edit or delete it. A new
 * correction is always made by itself on Space; the per-fix "suggest only" switch stays in the edit
 * dialog for the rare real-word case. If the keyboard settings stop corrections from being made (Auto-
 * correct or suggestions off), the screen says so and offers the switch, instead of silently not working.
 */
@Composable
fun MyCorrectionsScreen() = FlorisScreen {
    title = stringRes(R.string.typebetter__corrections_title)
    // The keyboard's own test box at the bottom: type the word and a space to see the correction happen.
    previewFieldVisible = true
    iconSpaceReserved = true

    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val prefs by FlorisPreferenceStore
    val rules by PersonalWords.rules.collectAsState()

    var typed by rememberSaveable { mutableStateOf("") }
    var meant by rememberSaveable { mutableStateOf("") }
    var justAdded by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Fix?>(null) }

    // Newest first: the one just added is the one you look for.
    val fixes = remember(rules.fixes) { newestFirst(rules.fixes) }

    content {
        val suggestionsOn by prefs.suggestion.enabled.collectAsState()
        val autoCorrectOn by prefs.suggestion.autoCorrect.collectAsState()
        when {
            !suggestionsOn -> SettingWarning(
                text = stringRes(R.string.typebetter__corrections_suggestions_off),
                onTurnOn = { scope.launch { prefs.suggestion.enabled.set(true) } },
            )
            !autoCorrectOn -> SettingWarning(
                text = stringRes(R.string.typebetter__corrections_autocorrect_off),
                onTurnOn = { scope.launch { prefs.suggestion.autoCorrect.set(true) } },
            )
        }

        val typedFocus = remember { FocusRequester() }
        val valid = isUsableFix(typed, meant)
        val replaced = typed.takeIf { it.isNotBlank() }?.let { rules.fixFor(it) }
        fun add() {
            if (!valid) return
            val fix = Fix(typed = typed.trim(), meant = meant.trim(), mode = FixMode.AUTO, source = Source.USER)
            PersonalWords.setFix(fix)
            justAdded = "${fix.typed}  →  ${fix.meant}"
            typed = ""
            meant = ""
            // Ready for the next one without a tap.
            runCatching { typedFocus.requestFocus() }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringRes(R.string.typebetter__corrections_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it; justAdded = null },
                modifier = Modifier.fillMaxWidth().focusRequester(typedFocus),
                singleLine = true,
                label = { Text(stringRes(R.string.typebetter__corrections_typed)) },
                placeholder = { Text("dor") },
                // The misspelling is typed on purpose here: no capital, no autocorrect from the app's side.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = meant,
                onValueChange = { meant = it; justAdded = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(R.string.typebetter__corrections_meant)) },
                placeholder = { Text("for") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    when {
                        replaced != null -> HintText(
                            stringRes(R.string.typebetter__fix_replaces, "fix" to "${replaced.typed}  →  ${replaced.meant}"),
                        )
                        !valid && (typed.isNotEmpty() || meant.isNotEmpty()) -> HintText(
                            stringRes(R.string.typebetter__fix_invalid),
                        )
                        justAdded != null -> {
                            HintText(stringRes(R.string.typebetter__corrections_added, "fix" to justAdded))
                            HintText(stringRes(R.string.typebetter__corrections_try))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { add() }, enabled = valid) {
                    Text(stringRes(R.string.typebetter__corrections_add))
                }
            }
        }

        PreferenceGroup(title = stringRes(R.string.typebetter__corrections_list, "count" to fixes.size)) {
            Text(
                text = stringRes(
                    if (fixes.isEmpty()) R.string.typebetter__corrections_empty else R.string.typebetter__corrections_edit_hint,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val auto = stringRes(R.string.typebetter__fix_mode_auto)
            val suggest = stringRes(R.string.typebetter__fix_mode_suggest)
            val phrase = stringRes(R.string.typebetter__fix_phrase)
            val starter = stringRes(R.string.typebetter__corrections_from_starter)
            fixes.forEach { fix ->
                Preference(
                    icon = Icons.Default.AutoFixHigh,
                    title = "${fix.typed}  →  ${fix.meant}",
                    summary = listOfNotNull(
                        if (fix.mode == FixMode.AUTO) auto else suggest,
                        phrase.takeIf { fix.isPhrase },
                        starter.takeIf { fix.source == Source.STARTER },
                    ).joinToString(" · "),
                    onClick = { editing = fix.typed },
                    trailing = {
                        IconButton(onClick = { pendingDelete = fix }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringRes(R.string.action__delete),
                            )
                        }
                    },
                )
            }
        }

        // Everything else that used to share a screen with the fixes is one tap away, not gone.
        Preference(
            icon = Icons.AutoMirrored.Filled.List,
            title = stringRes(R.string.typebetter__corrections_more),
            summary = stringRes(R.string.typebetter__corrections_more_summary),
            onClick = { navController.navigate(Routes.Settings.TypeBetterWords) },
        )

        // Edited by its typed word, so the dialog follows the saved list (and closes if the fix is gone).
        editing?.let { key ->
            val fix = rules.fixFor(key)
            if (fix == null) {
                // Deleted or renamed elsewhere (another screen, an import): nothing left to edit.
                LaunchedEffect(key) { editing = null }
            } else {
                FixDialog(
                    fix = fix,
                    onDismiss = { editing = null },
                    existing = { candidate -> rules.fixFor(candidate)?.takeIf { it.key != fix.key } },
                    onSave = { updated ->
                        PersonalWords.setFix(updated, replacing = fix.typed)
                        editing = null
                    },
                    onDelete = {
                        editing = null
                        pendingDelete = fix
                    },
                )
            }
        }

        pendingDelete?.let { fix ->
            JetPrefAlertDialog(
                title = stringRes(R.string.typebetter__delete_confirm_title),
                confirmLabel = stringRes(R.string.action__delete),
                dismissLabel = stringRes(R.string.action__cancel),
                onConfirm = {
                    pendingDelete = null
                    PersonalWords.removeFix(fix.typed)
                },
                onDismiss = { pendingDelete = null },
            ) {
                Column {
                    Text("${fix.typed}  →  ${fix.meant}")
                    HintText(stringRes(R.string.typebetter__delete_confirm_hint))
                }
            }
        }
    }
}

/** Newest first; entries saved before dates were kept (addedAt 0) go last, in A–Z order. */
internal fun newestFirst(fixes: List<Fix>): List<Fix> =
    fixes.sortedWith(compareByDescending<Fix> { it.addedAt }.thenBy { it.key })

@Composable
private fun SettingWarning(text: String, onTurnOn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onTurnOn) {
                Text(stringRes(R.string.typebetter__corrections_turn_on))
            }
        }
    }
}
