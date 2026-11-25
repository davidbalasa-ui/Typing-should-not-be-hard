package com.typebetter.keyboard.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typebetter.keyboard.keys.BottomKeyAction
import com.typebetter.keyboard.keys.BottomRow
import com.typebetter.keyboard.keys.KeySlot
import com.typebetter.keyboard.keys.RowSide
import com.typebetter.keyboard.keys.TopBarEditor
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.computeImageVector
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.smartbar.quickaction.computeDisplayName
import dev.patrickgold.florisboard.ime.smartbar.quickaction.filterDictateHidden
import dev.patrickgold.florisboard.ime.smartbar.quickaction.keyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Keys & buttons: the keyboard drawn as a picture, like a game controller's button-mapping screen. Tap a
 * top-bar button or a bottom-row key, pick what it does from a list; when that job already sits on
 * another button the screen says so and the two swap. Buttons and keys can be added and removed, and
 * the text box under the screen shows the real keyboard with every change at once.
 * docs/decisions/2026-11-25-keys-and-buttons-editor.md
 */
@Composable
fun KeysAndButtonsScreen() = FlorisScreen {
    title = stringRes(R.string.typebetter__kb_title)
    previewFieldVisible = true
    iconSpaceReserved = false

    content {
        val context = LocalContext.current
        val prefs by FlorisPreferenceStore
        val keyboardManager by context.keyboardManager()
        val subtypeManager by context.subtypeManager()

        // ---- Top bar state ----
        val arrangement by prefs.smartbar.actionArrangement.collectAsState()
        val storedCount by prefs.smartbar.visibleActionsCount.collectAsState()
        val promptsLayout by prefs.dictate.promptsLayout.collectAsState()
        val isOnBar: (QuickAction) -> Boolean = { listOf(it).filterDictateHidden(promptsLayout).isNotEmpty() }
        val fits = keyboardManager.smartbarFittingDynamicActionsCount.takeIf { it > 0 }
        val count = if (storedCount > 0) storedCount else (fits ?: DEFAULT_FIT_GUESS)
        val bar = TopBarEditor.onBar(arrangement, count, isOnBar)
        val more = TopBarEditor.inMore(arrangement, count, isOnBar)

        // ---- Bottom row state ----
        val storedRow by prefs.keyboard.bottomRowKeys.collectAsState()
        val utilityEnabled by prefs.keyboard.utilityKeyEnabled.collectAsState()
        val utilityAction by prefs.keyboard.utilityKeyAction.collectAsState()
        val row = BottomRow.parse(storedRow) ?: BottomRow.stock(
            utilityEnabled, utilityAction, multipleLanguages = subtypeManager.subtypes.size > 1,
        )

        var status by remember { mutableStateOf<Status?>(null) }
        var topPicker by remember { mutableStateOf<QuickAction?>(null) }
        var stickyPicker by remember { mutableStateOf(false) }
        var pendingTopSwap by remember { mutableStateOf<Pair<QuickAction, QuickAction>?>(null) }
        var keyPicker by remember { mutableStateOf<KeySlot?>(null) }
        var addPicker by remember { mutableStateOf<RowSide?>(null) }
        var pendingKeySwap by remember { mutableStateOf<Pair<KeySlot, BottomKeyAction>?>(null) }
        var showMoreInfo by remember { mutableStateOf(false) }

        // Preference writes are suspending; each edit is one short write on the screen's scope.
        val scope = rememberCoroutineScope()
        fun saveArrangement(value: QuickActionArrangement) {
            scope.launch { prefs.smartbar.actionArrangement.set(value) }
        }
        fun saveCount(value: Int) {
            // 0 = as many as fit; any other number stays within what the stepper offers.
            val bounded = if (value == 0) 0 else value.coerceIn(1, TopBarEditor.MAX_COUNT)
            scope.launch { prefs.smartbar.visibleActionsCount.set(bounded) }
        }
        // An edit that moves a button and changes the count writes both in one go, so leaving the
        // screen in between can never keep one without the other.
        fun saveArrangementAndCount(value: QuickActionArrangement, newCount: Int) {
            val bounded = if (newCount == 0) 0 else newCount.coerceIn(1, TopBarEditor.MAX_COUNT)
            scope.launch {
                prefs.smartbar.actionArrangement.set(value)
                prefs.smartbar.visibleActionsCount.set(bounded)
            }
        }
        fun saveRow(value: BottomRow?) {
            scope.launch { prefs.keyboard.bottomRowKeys.set(value?.serialize() ?: "") }
        }

        Text(
            text = stringRes(R.string.typebetter__kb_intro),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )

        // ---- The picture of the keyboard ----
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(6.dp),
        ) {
            SectionLabel(R.string.typebetter__kb_top_bar)
            Row(
                modifier = Modifier.fillMaxWidth().settingsSearchAnchor("typebetter__kb_top_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val sticky = arrangement.stickyAction
                BarButton(
                    action = sticky,
                    pinned = true,
                    onClick = { stickyPicker = true },
                )
                bar.forEach { action ->
                    BarButton(action = action, pinned = false, onClick = { topPicker = action })
                }
                Spacer(Modifier.weight(1f))
                PictureKey(
                    icon = Icons.Default.MoreHoriz,
                    label = null,
                    description = stringRes(R.string.typebetter__kb_more_button),
                    modifier = Modifier.size(BUTTON_SIZE),
                    onClick = { showMoreInfo = true },
                )
            }

            // The letters, only to show where the rows are; they are not editable here.
            Spacer(Modifier.height(6.dp))
            Column(modifier = Modifier.alpha(0.35f)) {
                LETTER_ROWS.forEach { letters ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        letters.forEach { ch ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(ch, fontSize = 13.sp) }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }
            }

            SectionLabel(R.string.typebetter__kb_bottom_row)
            Row(
                modifier = Modifier.fillMaxWidth().settingsSearchAnchor("typebetter__kb_bottom_row"),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PictureKey(
                    icon = null, label = "?123",
                    description = stringRes(R.string.typebetter__kb_locked_symbols),
                    modifier = Modifier.weight(1.4f).height(KEY_HEIGHT), locked = true,
                    onClick = { status = Status(R.string.typebetter__kb_locked_symbols) },
                )
                row.left.forEachIndexed { i, action ->
                    UserKey(action, Modifier.weight(1f)) { keyPicker = KeySlot(RowSide.LEFT, i) }
                }
                PictureKey(
                    icon = null, label = "space",
                    description = stringRes(R.string.typebetter__kb_locked_space),
                    modifier = Modifier.weight((8 - row.size).coerceAtLeast(2).toFloat()).height(KEY_HEIGHT),
                    locked = true,
                    onClick = { status = Status(R.string.typebetter__kb_locked_space) },
                )
                row.right.forEachIndexed { i, action ->
                    UserKey(action, Modifier.weight(1f)) { keyPicker = KeySlot(RowSide.RIGHT, i) }
                }
                PictureKey(
                    icon = Icons.AutoMirrored.Filled.KeyboardReturn, label = null,
                    description = stringRes(R.string.typebetter__kb_locked_enter),
                    modifier = Modifier.weight(1.4f).height(KEY_HEIGHT), locked = true,
                    onClick = { status = Status(R.string.typebetter__kb_locked_enter) },
                )
            }
        }

        // The result of the last tap, so every tap shows what it did.
        status?.let { s ->
            Text(
                text = s.render(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // ---- Bottom row: add keys ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(RowSide.LEFT to R.string.typebetter__kb_add_left, RowSide.RIGHT to R.string.typebetter__kb_add_right)
                .forEach { (side, label) ->
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (row.canAdd) {
                                addPicker = side
                            } else {
                                status = Status(R.string.typebetter__kb_add_max, "max" to BottomRow.MAX_KEYS)
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringRes(label), maxLines = 2, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
        }

        // ---- Top bar: how many buttons ----
        val maxCount = minOf(TopBarEditor.MAX_COUNT, bar.size + more.size)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (storedCount > 0) {
                    stringRes(R.string.typebetter__kb_count_label, "count" to count)
                } else {
                    stringRes(R.string.typebetter__kb_count_label, "count" to stringRes(R.string.typebetter__kb_count_auto, "count" to count))
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            StepButton(Icons.Default.Remove, stringRes(R.string.typebetter__kb_count_fewer)) {
                if (count <= 1) {
                    status = Status(R.string.typebetter__kb_count_min)
                } else {
                    saveCount((count - 1).coerceAtLeast(1))
                    status = Status(R.string.typebetter__kb_status_count, "count" to count - 1)
                }
            }
            StepButton(Icons.Default.Add, stringRes(R.string.typebetter__kb_count_more)) {
                when {
                    count >= TopBarEditor.MAX_COUNT ->
                        status = Status(R.string.typebetter__kb_count_max, "count" to TopBarEditor.MAX_COUNT)
                    count >= maxCount -> status = Status(R.string.typebetter__kb_count_more_empty)
                    else -> {
                        saveCount(count + 1)
                        status = if (fits != null && count + 1 > fits) {
                            Status(R.string.typebetter__kb_count_fit_note, "count" to fits)
                        } else {
                            Status(R.string.typebetter__kb_status_count, "count" to count + 1)
                        }
                    }
                }
            }
        }
        if (storedCount > 0) {
            TextButton(
                modifier = Modifier.padding(horizontal = 8.dp),
                onClick = {
                    saveCount(0)
                    status = Status(R.string.typebetter__kb_status_count, "count" to (fits ?: DEFAULT_FIT_GUESS))
                },
            ) { Text(stringRes(R.string.typebetter__kb_count_auto_button)) }
        }
        if (fits != null && count > fits) {
            Hint(stringRes(R.string.typebetter__kb_count_fit_note, "count" to fits))
        }

        // ---- Top bar: the ⋯ menu and the hidden buttons ----
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListHeader(R.string.typebetter__kb_more_section)
        Hint(stringRes(R.string.typebetter__kb_more_section_hint))
        if (more.isEmpty()) Hint(stringRes(R.string.typebetter__kb_more_empty))
        more.forEach { action ->
            val name = action.computeDisplayName(DefaultComputingEvaluator)
            ActionRow(
                action = action,
                onClick = {
                    if (count >= TopBarEditor.MAX_COUNT) {
                        status = Status(R.string.typebetter__kb_count_max, "count" to TopBarEditor.MAX_COUNT)
                    } else {
                        saveArrangementAndCount(TopBarEditor.bringToBar(arrangement, action, count, isOnBar), count + 1)
                        status = Status(R.string.typebetter__kb_status_on_bar, "a" to name)
                    }
                },
                trailingLabel = stringRes(R.string.typebetter__kb_hide),
                onTrailing = {
                    saveArrangement(TopBarEditor.hide(arrangement, action))
                    status = Status(R.string.typebetter__kb_status_hidden, "a" to name)
                },
            )
        }
        ListHeader(R.string.typebetter__kb_hidden_section)
        if (arrangement.hiddenActions.isEmpty()) Hint(stringRes(R.string.typebetter__kb_hidden_empty))
        arrangement.hiddenActions.forEach { action ->
            val name = action.computeDisplayName(DefaultComputingEvaluator)
            ActionRow(
                action = action,
                dimmed = true,
                onClick = null,
                trailingLabel = stringRes(R.string.typebetter__kb_show),
                onTrailing = {
                    saveArrangement(TopBarEditor.unhide(arrangement, action))
                    status = Status(R.string.typebetter__kb_status_shown, "a" to name)
                },
            )
        }

        // ---- Reset ----
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                saveRow(null)
                status = Status(R.string.typebetter__kb_status_reset_keys)
            }) { Text(stringRes(R.string.typebetter__kb_reset_keys)) }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                saveArrangementAndCount(QuickActionArrangement.Default, 0)
                status = Status(R.string.typebetter__kb_status_reset_top)
            }) { Text(stringRes(R.string.typebetter__kb_reset_top)) }
        }
        Spacer(Modifier.height(16.dp))

        // ---- Dialogs ----
        val allActions = buildList {
            arrangement.stickyAction?.let { add(it) }
            addAll(arrangement.dynamicActions)
            addAll(arrangement.hiddenActions)
        }.distinct()

        topPicker?.let { current ->
            val currentName = current.computeDisplayName(DefaultComputingEvaluator)
            ActionPickerDialog(
                title = stringRes(R.string.typebetter__kb_picker_button_title),
                // A button another setting keeps off the bar (the magic wand while the prompts have their
                // own row) would vanish from the bar the moment it was picked, so it is not offered.
                actions = allActions.filter(isOnBar),
                current = current,
                placeLabel = { placeLabel(TopBarEditor.placeOf(arrangement, it, count, isOnBar)) },
                onPick = { picked ->
                    topPicker = null
                    if (TopBarEditor.replaceMovesAnotherVisibleButton(arrangement, current, picked, count, isOnBar)) {
                        pendingTopSwap = current to picked
                    } else if (picked != current) {
                        saveArrangement(TopBarEditor.replace(arrangement, current, picked))
                        status = Status(R.string.typebetter__kb_status_button_set, "a" to picked)
                    }
                },
                footer = {
                    TextButton(onClick = {
                        topPicker = null
                        saveArrangementAndCount(
                            TopBarEditor.moveToMore(arrangement, current, count, isOnBar),
                            (count - 1).coerceAtLeast(1),
                        )
                        status = Status(R.string.typebetter__kb_status_moved_more, "a" to currentName)
                    }) { Text(stringRes(R.string.typebetter__kb_take_off_bar)) }
                },
                onDismiss = { topPicker = null },
            )
        }

        if (stickyPicker) {
            val current = arrangement.stickyAction
            ActionPickerDialog(
                title = stringRes(R.string.typebetter__kb_picker_sticky_title),
                actions = allActions,
                current = current,
                placeLabel = { placeLabel(TopBarEditor.placeOf(arrangement, it, count, isOnBar)) },
                onPick = { picked ->
                    stickyPicker = false
                    if (current != null &&
                        TopBarEditor.replaceMovesAnotherVisibleButton(arrangement, current, picked, count, isOnBar)
                    ) {
                        pendingTopSwap = current to picked
                    } else if (picked != current) {
                        saveArrangement(
                            if (current != null) TopBarEditor.replace(arrangement, current, picked)
                            else TopBarEditor.setSticky(arrangement, picked),
                        )
                        status = Status(R.string.typebetter__kb_status_button_set, "a" to picked)
                    }
                },
                footer = {
                    if (current != null) {
                        TextButton(onClick = {
                            stickyPicker = false
                            saveArrangement(TopBarEditor.setSticky(arrangement, null))
                            status = Status(R.string.typebetter__kb_status_sticky_none)
                        }) { Text(stringRes(R.string.typebetter__kb_no_sticky)) }
                    }
                },
                onDismiss = { stickyPicker = false },
            )
        }

        pendingTopSwap?.let { (current, picked) ->
            val oldName = current.computeDisplayName(DefaultComputingEvaluator)
            val newName = picked.computeDisplayName(DefaultComputingEvaluator)
            AlertDialog(
                onDismissRequest = { pendingTopSwap = null },
                title = { Text(stringRes(R.string.typebetter__kb_swap_buttons_title)) },
                text = { Text(stringRes(R.string.typebetter__kb_swap_buttons_text, "new" to newName, "old" to oldName)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingTopSwap = null
                        saveArrangement(TopBarEditor.replace(arrangement, current, picked))
                        status = Status(R.string.typebetter__kb_status_swapped, "a" to newName, "b" to oldName)
                    }) { Text(stringRes(R.string.typebetter__kb_swap)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTopSwap = null }) { Text(stringRes(R.string.action__cancel)) }
                },
            )
        }

        keyPicker?.let { slot ->
            val current = row[slot]
            // A slot that no longer exists (the row changed meanwhile) simply shows no dialog.
            if (current != null) {
                KeyPickerDialog(
                    title = stringRes(R.string.typebetter__kb_picker_key_title),
                    row = row,
                    current = current,
                    otherKeyLabel = stringRes(R.string.typebetter__kb_on_other_key),
                    onPick = { picked ->
                        keyPicker = null
                        val change = row.assign(slot, picked)
                        if (change.needsConfirmation) {
                            pendingKeySwap = slot to picked
                        } else if (picked != current) {
                            saveRow(change.row)
                            status = Status(R.string.typebetter__kb_status_key_set, "a" to NameRes(keyNameRes(picked)))
                        }
                    },
                    footer = {
                        TextButton(onClick = {
                            keyPicker = null
                            saveRow(row.remove(slot))
                            status = Status(R.string.typebetter__kb_status_removed, "a" to NameRes(keyNameRes(current)))
                        }) { Text(stringRes(R.string.typebetter__kb_remove_key)) }
                    },
                    onDismiss = { keyPicker = null },
                )
            }
        }

        addPicker?.let { side ->
            KeyPickerDialog(
                title = stringRes(R.string.typebetter__kb_picker_add_title),
                row = row,
                current = null,
                otherKeyLabel = stringRes(R.string.typebetter__kb_on_other_key_move),
                onPick = { picked ->
                    addPicker = null
                    saveRow(row.add(side, picked))
                    status = Status(R.string.typebetter__kb_status_added, "a" to NameRes(keyNameRes(picked)))
                },
                footer = {},
                onDismiss = { addPicker = null },
            )
        }

        pendingKeySwap?.let { (slot, picked) ->
            val current = row[slot]
            if (current != null) {
                val newName = stringRes(keyNameRes(picked))
                val oldName = stringRes(keyNameRes(current))
                AlertDialog(
                    onDismissRequest = { pendingKeySwap = null },
                    title = { Text(stringRes(R.string.typebetter__kb_swap_keys_title)) },
                    text = { Text(stringRes(R.string.typebetter__kb_swap_keys_text, "new" to newName, "old" to oldName)) },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingKeySwap = null
                            saveRow(row.assign(slot, picked).row)
                            status = Status(R.string.typebetter__kb_status_swapped, "a" to newName, "b" to oldName)
                        }) { Text(stringRes(R.string.typebetter__kb_swap)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingKeySwap = null }) { Text(stringRes(R.string.action__cancel)) }
                    },
                )
            }
        }

        if (showMoreInfo) {
            AlertDialog(
                onDismissRequest = { showMoreInfo = false },
                title = { Text(stringRes(R.string.typebetter__kb_more_section)) },
                text = {
                    Column {
                        Text(stringRes(R.string.typebetter__kb_more_section_hint))
                        if (more.isEmpty()) Text(stringRes(R.string.typebetter__kb_more_empty))
                        more.forEach { Text("• " + it.computeDisplayName(DefaultComputingEvaluator)) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMoreInfo = false }) { Text(stringRes(R.string.action__ok)) }
                },
            )
        }
    }
}

private const val DEFAULT_FIT_GUESS = 5
private val BUTTON_SIZE = 40.dp
private val KEY_HEIGHT = 44.dp
private val LETTER_ROWS = listOf(
    "qwertyuiop".map { it.toString() },
    "asdfghjkl".map { it.toString() },
    listOf("⇧") + "zxcvbnm".map { it.toString() } + listOf("⌫"),
)

/** A message about the last change; rendered in composition so it follows the app's language. */
private class Status(@StringRes val res: Int, vararg val args: Pair<String, Any?>) {
    @Composable
    fun render(): String {
        // A name can be a key's string resource, a top-bar button, or text already.
        val resolved = args.map { (k, v) ->
            k to when (v) {
                is NameRes -> stringRes(v.res)
                is QuickAction -> v.computeDisplayName(DefaultComputingEvaluator)
                else -> v
            }
        }
        return stringRes(res, *resolved.toTypedArray())
    }
}

/** A key name given as a resource, resolved when the status is shown. */
private class NameRes(@StringRes val res: Int)

private fun keyNameRes(action: BottomKeyAction): Int = when (action) {
    BottomKeyAction.COMMA -> R.string.typebetter__kb_key_comma
    BottomKeyAction.PERIOD -> R.string.typebetter__kb_key_period
    BottomKeyAction.QUESTION -> R.string.typebetter__kb_key_question
    BottomKeyAction.EXCLAMATION -> R.string.typebetter__kb_key_exclamation
    BottomKeyAction.APOSTROPHE -> R.string.typebetter__kb_key_apostrophe
    BottomKeyAction.AT -> R.string.typebetter__kb_key_at
    BottomKeyAction.SLASH -> R.string.typebetter__kb_key_slash
    BottomKeyAction.HYPHEN -> R.string.typebetter__kb_key_hyphen
    BottomKeyAction.EMOJI -> R.string.typebetter__kb_key_emoji
    BottomKeyAction.VOICE -> R.string.typebetter__kb_key_voice
    BottomKeyAction.AI_PROMPTS -> R.string.typebetter__kb_key_ai_prompts
    BottomKeyAction.CLIPBOARD -> R.string.typebetter__kb_key_clipboard
    BottomKeyAction.TRANSLATE -> R.string.typebetter__kb_key_translate
    BottomKeyAction.UNDO -> R.string.typebetter__kb_key_undo
    BottomKeyAction.CURSOR_LEFT -> R.string.typebetter__kb_key_cursor_left
    BottomKeyAction.CURSOR_RIGHT -> R.string.typebetter__kb_key_cursor_right
    BottomKeyAction.NUMBER_PAD -> R.string.typebetter__kb_key_number_pad
    BottomKeyAction.LANGUAGE -> R.string.typebetter__kb_key_language
    BottomKeyAction.KEYBOARD_PICKER -> R.string.typebetter__kb_key_keyboard_picker
    BottomKeyAction.HIDE_KEYBOARD -> R.string.typebetter__kb_key_hide_keyboard
    BottomKeyAction.SETTINGS -> R.string.typebetter__kb_key_settings
}

/** A key's face in the picture: its character, or the same icon the keyboard draws. */
private fun keyFace(action: BottomKeyAction): Pair<ImageVector?, String?> = when (val data = action.data) {
    is TextKeyData -> if (data.type == dev.patrickgold.florisboard.ime.text.key.KeyType.CHARACTER) {
        null to data.label
    } else {
        DefaultComputingEvaluator.computeImageVector(data) to null
    }
    else -> null to "," // the comma, whose face changes with the field
}

@Composable
private fun placeLabel(place: TopBarEditor.Place?): String? = when (place) {
    TopBarEditor.Place.Sticky -> stringRes(R.string.typebetter__kb_place_sticky)
    is TopBarEditor.Place.Bar -> stringRes(R.string.typebetter__kb_place_bar)
    TopBarEditor.Place.More -> stringRes(R.string.typebetter__kb_place_more)
    TopBarEditor.Place.Hidden -> stringRes(R.string.typebetter__kb_place_hidden)
    null -> null
}

@Composable
private fun SectionLabel(@StringRes res: Int) {
    Text(
        text = stringRes(res),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ListHeader(@StringRes res: Int) {
    Text(
        text = stringRes(res),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(44.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null)
    }
}

/** One button of the top bar in the picture. */
@Composable
private fun BarButton(action: QuickAction?, pinned: Boolean, onClick: () -> Unit) {
    val name = action?.computeDisplayName(DefaultComputingEvaluator)
        ?: stringRes(R.string.typebetter__kb_no_sticky)
    Box(contentAlignment = Alignment.TopEnd) {
        PictureKey(
            icon = action?.let { DefaultComputingEvaluator.computeImageVector(it.keyData()) },
            label = if (action == null) "–" else name.take(1),
            description = if (pinned) "$name, ${stringRes(R.string.typebetter__kb_sticky_hint)}" else name,
            modifier = Modifier.size(BUTTON_SIZE),
            highlighted = pinned,
            onClick = onClick,
        )
        if (pinned) {
            Icon(
                Icons.Default.PushPin,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RowScope.UserKey(action: BottomKeyAction, modifier: Modifier, onClick: () -> Unit) {
    val (icon, label) = keyFace(action)
    PictureKey(
        icon = icon,
        label = label,
        description = stringRes(keyNameRes(action)),
        modifier = modifier.height(KEY_HEIGHT),
        highlighted = true,
        onClick = onClick,
    )
}

/** One tappable key or button of the picture. Locked keys are drawn flatter but still explain themselves. */
@Composable
private fun PictureKey(
    icon: ImageVector?,
    label: String?,
    description: String,
    modifier: Modifier,
    locked: Boolean = false,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(if (locked) colors.surface.copy(alpha = 0.6f) else colors.surface, RoundedCornerShape(6.dp))
            .then(
                if (highlighted) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(6.dp)) else Modifier,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        } else {
            Text(
                text = label.orEmpty(),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = if (locked) colors.onSurfaceVariant else colors.onSurface,
            )
        }
    }
}

/** A row of the ⋯ menu or hidden list. */
@Composable
private fun ActionRow(
    action: QuickAction,
    dimmed: Boolean = false,
    onClick: (() -> Unit)?,
    trailingLabel: String,
    onTrailing: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = DefaultComputingEvaluator.computeImageVector(action.keyData())
        Box(Modifier.size(32.dp).alpha(if (dimmed) 0.5f else 1f), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(action.computeDisplayName(DefaultComputingEvaluator), style = MaterialTheme.typography.bodyLarge)
            if (onClick != null) {
                Text(
                    stringRes(R.string.typebetter__kb_put_on_bar),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        TextButton(onClick = onTrailing) { Text(trailingLabel) }
    }
}

@Composable
private fun ActionPickerDialog(
    title: String,
    actions: List<QuickAction>,
    current: QuickAction?,
    placeLabel: @Composable (QuickAction) -> String?,
    onPick: (QuickAction) -> Unit,
    footer: @Composable () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                actions.forEach { action ->
                    val icon = DefaultComputingEvaluator.computeImageVector(action.keyData())
                    PickerRow(
                        icon = icon,
                        name = action.computeDisplayName(DefaultComputingEvaluator),
                        note = if (action == current) stringRes(R.string.typebetter__kb_place_current) else placeLabel(action),
                        selected = action == current,
                        onClick = { onPick(action) },
                    )
                }
            }
        },
        confirmButton = { footer() },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) } },
    )
}

@Composable
private fun KeyPickerDialog(
    title: String,
    row: BottomRow,
    current: BottomKeyAction?,
    otherKeyLabel: String,
    onPick: (BottomKeyAction) -> Unit,
    footer: @Composable () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                listOf(
                    BottomKeyAction.Kind.CHARACTER to R.string.typebetter__kb_group_characters,
                    BottomKeyAction.Kind.TOOL to R.string.typebetter__kb_group_tools,
                    BottomKeyAction.Kind.KEYBOARD to R.string.typebetter__kb_group_keyboard,
                ).forEach { (kind, header) ->
                    Text(
                        stringRes(header),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    BottomKeyAction.entries.filter { it.kind == kind }.forEach { action ->
                        val (icon, label) = keyFace(action)
                        PickerRow(
                            icon = icon,
                            glyph = label,
                            name = stringRes(keyNameRes(action)),
                            note = when {
                                action == current -> stringRes(R.string.typebetter__kb_place_current)
                                row.slotOf(action) != null -> otherKeyLabel
                                else -> null
                            },
                            selected = action == current,
                            onClick = { onPick(action) },
                        )
                    }
                }
            }
        },
        confirmButton = { footer() },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) } },
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector?,
    name: String,
    note: String?,
    selected: Boolean,
    glyph: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(icon, contentDescription = null)
                glyph != null -> Text(glyph, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
