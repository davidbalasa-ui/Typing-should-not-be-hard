package com.typebetter.keyboard.settings

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.typebetter.keyboard.PersonalWords
import com.typebetter.personal.Fix
import com.typebetter.personal.FixMode
import com.typebetter.personal.NeverChange
import com.typebetter.personal.Source
import com.typebetter.personal.StarterPack
import com.typebetter.personal.WordHarvester
import com.typebetter.personal.keyOf
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.SwitchRow
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.florisDialogScroll
import org.florisboard.lib.compose.stringRes

/**
 * "My words & fixes": everything TypeBetter keeps about the way David writes, in one place.
 *
 * - My fixes: typed → meant, fixed automatically or only suggested.
 * - Never change: corrections the keyboard may not make by itself any more.
 * - My words: never corrected, suggested early (the keyboard's personal dictionary).
 * - Starter pack, Learn from text, Export and Import.
 */
@Composable
fun MyWordsScreen() = FlorisScreen {
    title = stringRes(R.string.typebetter__screen_title)
    previewFieldVisible = true
    iconSpaceReserved = true

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by PersonalWords.rules.collectAsState()
    val changes by PersonalWords.changes.collectAsState()

    var words by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(changes) {
        words = withContext(Dispatchers.IO) { PersonalWords.myWords() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    var editingFix by remember { mutableStateOf<Fix?>(null) }
    var editingBlock by remember { mutableStateOf<NeverChange?>(null) }
    var editingWord by remember { mutableStateOf<String?>(null) }
    var learning by remember { mutableStateOf(false) }
    var restoringWeekly by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { PersonalWords.exportTo(context, uri) }
            context.showLongToast(if (ok) R.string.typebetter__export_done else R.string.typebetter__export_failed)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val summary = withContext(Dispatchers.IO) { PersonalWords.importFrom(context, uri) }
            if (summary == null) {
                context.showLongToast(R.string.typebetter__import_failed)
            } else {
                context.showLongToast(
                    R.string.typebetter__import_done,
                    "words" to summary.words,
                    "fixes" to summary.fixes + summary.blocks,
                )
            }
        }
    }

    actions {
        var menuExpanded by remember { mutableStateOf(false) }
        FlorisIconButton(onClick = { menuExpanded = true }, icon = Icons.Default.MoreVert)
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                text = { Text(stringRes(R.string.typebetter__learn_title)) },
                onClick = { menuExpanded = false; learning = true },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                text = { Text(stringRes(R.string.typebetter__export)) },
                onClick = {
                    menuExpanded = false
                    exportLauncher.launch("typebetter-words.json")
                },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                text = { Text(stringRes(R.string.typebetter__weekly_title)) },
                onClick = { menuExpanded = false; restoringWeekly = true },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                text = { Text(stringRes(R.string.typebetter__import)) },
                onClick = {
                    menuExpanded = false
                    importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                },
            )
        }
    }

    content {
        Preference(
            icon = Icons.Default.ContentPaste,
            title = stringRes(R.string.typebetter__learn_title),
            summary = stringRes(R.string.typebetter__learn_summary),
            onClick = { learning = true },
        )

        PreferenceGroup(title = stringRes(R.string.typebetter__fixes_group, "count" to rules.fixes.size)) {
            val auto = stringRes(R.string.typebetter__fix_mode_auto)
            val suggest = stringRes(R.string.typebetter__fix_mode_suggest)
            val phrase = stringRes(R.string.typebetter__fix_phrase)
            rules.fixes.sortedBy { keyOf(it.typed) }.forEach { fix ->
                Preference(
                    icon = Icons.Default.AutoFixHigh,
                    title = "${fix.typed}  →  ${fix.meant}",
                    summary = listOfNotNull(
                        if (fix.mode == FixMode.AUTO) auto else suggest,
                        phrase.takeIf { fix.isPhrase },
                    ).joinToString(" · "),
                    onClick = { editingFix = fix },
                )
            }
            Preference(
                icon = Icons.Default.Add,
                title = stringRes(R.string.typebetter__fix_add),
                summary = if (rules.fixes.isEmpty()) stringRes(R.string.typebetter__fixes_empty) else null,
                onClick = { editingFix = Fix(typed = "", meant = "") },
            )
        }

        PreferenceGroup(title = stringRes(R.string.typebetter__never_group, "count" to rules.neverChange.size)) {
            rules.neverChange.sortedBy { keyOf(it.typed) }.forEach { entry ->
                Preference(
                    icon = Icons.Default.Block,
                    title = "${entry.typed}  ↛  ${entry.blocked}",
                    summary = stringRes(R.string.typebetter__never_summary),
                    onClick = { editingBlock = entry },
                )
            }
            Preference(
                icon = Icons.Default.Add,
                title = stringRes(R.string.typebetter__never_add),
                summary = if (rules.neverChange.isEmpty()) stringRes(R.string.typebetter__never_empty) else null,
                onClick = { editingBlock = NeverChange(typed = "", blocked = "") },
            )
        }

        PreferenceGroup(title = stringRes(R.string.typebetter__words_group, "count" to words.size)) {
            Preference(
                icon = Icons.Default.Add,
                title = stringRes(R.string.typebetter__word_add),
                summary = if (words.isEmpty()) stringRes(R.string.typebetter__words_empty) else null,
                onClick = { editingWord = "" },
            )
            words.forEach { word ->
                Preference(
                    icon = Icons.Default.Star,
                    title = word,
                    onClick = { editingWord = word },
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.typebetter__starter_group)) {
            SwitchRow(
                title = stringRes(R.string.typebetter__starter_title),
                summary = stringRes(R.string.typebetter__starter_summary),
                checked = StarterPack.isActive(rules),
                onCheckedChange = { PersonalWords.setStarterPack(it) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        editingFix?.let { fix ->
            FixDialog(
                fix = fix,
                onDismiss = { editingFix = null },
                onSave = { updated ->
                    PersonalWords.setFix(updated, replacing = fix.typed.takeIf { it.isNotBlank() })
                    editingFix = null
                },
                onDelete = if (fix.typed.isNotBlank()) {
                    { PersonalWords.removeFix(fix.typed); editingFix = null }
                } else {
                    null
                },
            )
        }

        editingBlock?.let { entry ->
            NeverChangeDialog(
                entry = entry,
                onDismiss = { editingBlock = null },
                onSave = { updated ->
                    if (entry.typed.isNotBlank()) PersonalWords.removeNeverChange(entry.typed, entry.blocked)
                    PersonalWords.setNeverChange(updated)
                    editingBlock = null
                },
                onDelete = if (entry.typed.isNotBlank()) {
                    { PersonalWords.removeNeverChange(entry.typed, entry.blocked); editingBlock = null }
                } else {
                    null
                },
            )
        }

        editingWord?.let { word ->
            WordDialog(
                word = word,
                onDismiss = { editingWord = null },
                onSave = { updated ->
                    editingWord = null
                    scope.launch(Dispatchers.IO) {
                        if (word.isNotBlank() && word != updated) PersonalWords.removeWords(listOf(word))
                        PersonalWords.addWord(updated)
                    }
                },
                onDelete = if (word.isNotBlank()) {
                    {
                        editingWord = null
                        scope.launch(Dispatchers.IO) { PersonalWords.removeWords(listOf(word)) }
                    }
                } else {
                    null
                },
            )
        }

        if (restoringWeekly) {
            WeeklyDialog(
                onDismiss = { restoringWeekly = false },
                onPick = { copy ->
                    restoringWeekly = false
                    scope.launch {
                        val summary = withContext(Dispatchers.IO) { PersonalWords.restoreWeekly(copy) }
                        if (summary == null) {
                            context.showLongToast(R.string.typebetter__import_failed)
                        } else {
                            context.showLongToast(
                                R.string.typebetter__import_done,
                                "words" to summary.words,
                                "fixes" to summary.fixes + summary.blocks,
                            )
                        }
                    }
                },
            )
        }

        if (learning) {
            LearnDialog(
                context = context,
                onDismiss = { learning = false },
                onAdd = { chosen ->
                    learning = false
                    scope.launch {
                        val added = withContext(Dispatchers.IO) { PersonalWords.addWords(chosen) }
                        context.showLongToast(R.string.typebetter__learn_done, "count" to added.size)
                    }
                },
            )
        }
    }
}

@Composable
private fun FixDialog(
    fix: Fix,
    onDismiss: () -> Unit,
    onSave: (Fix) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var typed by remember { mutableStateOf(fix.typed) }
    var meant by remember { mutableStateOf(fix.meant) }
    var auto by remember { mutableStateOf(fix.mode == FixMode.AUTO) }
    val valid = typed.isNotBlank() && meant.isNotBlank() && typed.trim() != meant.trim()

    JetPrefAlertDialog(
        scrollModifier = florisDialogScroll(),
        title = stringRes(R.string.typebetter__fix_dialog_title),
        confirmLabel = stringRes(R.string.action__save),
        dismissLabel = stringRes(R.string.action__cancel),
        neutralLabel = if (onDelete != null) stringRes(R.string.action__delete) else null,
        onConfirm = {
            if (valid) {
                onSave(
                    fix.copy(
                        typed = typed.trim(),
                        meant = meant.trim(),
                        mode = if (auto) FixMode.AUTO else FixMode.SUGGEST,
                        // Edited by hand, so it is the user's own now (switching the starter pack off keeps it).
                        source = Source.USER,
                    ),
                )
            }
        },
        onDismiss = onDismiss,
        onNeutral = { onDelete?.invoke() },
    ) {
        Column {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(R.string.typebetter__fix_typed)) },
                placeholder = { Text("silos") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = meant,
                onValueChange = { meant = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(R.string.typebetter__fix_meant)) },
                placeholder = { Text("Xylos") },
            )
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                title = stringRes(R.string.typebetter__fix_auto_title),
                summary = stringRes(R.string.typebetter__fix_auto_summary),
                checked = auto,
                onCheckedChange = { auto = it },
            )
            if (!valid && (typed.isNotEmpty() || meant.isNotEmpty())) {
                HintText(stringRes(R.string.typebetter__fix_invalid))
            }
        }
    }
}

@Composable
private fun NeverChangeDialog(
    entry: NeverChange,
    onDismiss: () -> Unit,
    onSave: (NeverChange) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var typed by remember { mutableStateOf(entry.typed) }
    var blocked by remember { mutableStateOf(entry.blocked) }
    val valid = typed.isNotBlank() && blocked.isNotBlank()

    JetPrefAlertDialog(
        scrollModifier = florisDialogScroll(),
        title = stringRes(R.string.typebetter__never_dialog_title),
        confirmLabel = stringRes(R.string.action__save),
        dismissLabel = stringRes(R.string.action__cancel),
        neutralLabel = if (onDelete != null) stringRes(R.string.action__delete) else null,
        onConfirm = {
            if (valid) {
                onSave(
                    entry.copy(
                        typed = typed.trim(),
                        blocked = blocked.trim(),
                        // Edited by hand, so it is the user's own now (switching the starter pack off keeps it).
                        source = Source.USER,
                    ),
                )
            }
        },
        onDismiss = onDismiss,
        onNeutral = { onDelete?.invoke() },
    ) {
        Column {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(R.string.typebetter__never_typed)) },
                placeholder = { Text("its") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = blocked,
                onValueChange = { blocked = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(R.string.typebetter__never_blocked)) },
                placeholder = { Text("it's") },
            )
            HintText(stringRes(R.string.typebetter__never_hint))
        }
    }
}

@Composable
private fun WordDialog(
    word: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var text by remember { mutableStateOf(word) }
    val valid = text.isNotBlank() && text.trim().none { it.isWhitespace() }

    JetPrefAlertDialog(
        scrollModifier = florisDialogScroll(),
        title = stringRes(if (word.isBlank()) R.string.typebetter__word_add else R.string.typebetter__word_dialog_title),
        confirmLabel = stringRes(R.string.action__save),
        dismissLabel = stringRes(R.string.action__cancel),
        neutralLabel = if (onDelete != null) stringRes(R.string.action__delete) else null,
        onConfirm = { if (valid) onSave(text.trim()) },
        onDismiss = onDismiss,
        onNeutral = { onDelete?.invoke() },
    ) {
        Column {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(R.string.typebetter__word_field)) },
                placeholder = { Text("Kasmyra") },
            )
            HintText(stringRes(R.string.typebetter__word_hint))
        }
    }
}

/**
 * Learn from text: paste (or take from the clipboard) a conversation, see the words the keyboard does
 * not know, tick the ones to keep.
 */
@Composable
private fun LearnDialog(
    context: Context,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit,
) {
    // The clipboard is read only when asked (it may hold a password), and nothing is ticked until chosen.
    var text by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<WordHarvester.Candidate>?>(null) }
    val chosen = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    fun findWords() {
        val input = text
        scope.launch {
            val found = withContext(Dispatchers.Default) {
                val nlp by context.nlpManager()
                val subtypes by context.subtypeManager()
                val subtype = subtypes.activeSubtype
                val mine = PersonalWords.myWords().map { keyOf(it) }.toHashSet()
                WordHarvester.harvest(input, isKnown = { word ->
                    keyOf(word) in mine || runCatching {
                        nlp.getFrequencyForWord(subtype, word) > 0.0 ||
                            nlp.getFrequencyForWord(subtype, word.lowercase()) > 0.0
                    }.getOrDefault(false)
                })
            }
            chosen.clear()
            candidates = found
        }
    }

    val list = candidates
    JetPrefAlertDialog(
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        scrollModifier = florisDialogScroll(),
        title = stringRes(R.string.typebetter__learn_title),
        confirmLabel = if (list == null) {
            stringRes(R.string.typebetter__learn_find)
        } else {
            stringRes(R.string.typebetter__learn_add, "count" to chosen.size)
        },
        dismissLabel = stringRes(R.string.action__cancel),
        onConfirm = {
            if (list == null) {
                if (text.isNotBlank()) findWords()
            } else if (chosen.isNotEmpty()) {
                onAdd(chosen.toList())
            } else {
                onDismiss()
            }
        },
        onDismiss = onDismiss,
    ) {
        Column {
            if (list == null) {
                HintText(stringRes(R.string.typebetter__learn_paste_hint))
                TextButton(onClick = { text = readClipboard(context) }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Text(stringRes(R.string.typebetter__learn_paste), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 4,
                    maxLines = 10,
                    label = { Text(stringRes(R.string.typebetter__learn_field)) },
                )
            } else if (list.isEmpty()) {
                Text(stringRes(R.string.typebetter__learn_none))
            } else {
                HintText(stringRes(R.string.typebetter__learn_pick_hint))
                val allTicked = chosen.size == list.size
                TextButton(onClick = {
                    chosen.clear()
                    if (!allTicked) chosen.addAll(list.map { it.word })
                }) {
                    Text(stringRes(if (allTicked) R.string.typebetter__learn_untick_all else R.string.typebetter__learn_tick_all))
                }
                list.forEach { candidate ->
                    val checked = candidate.word in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) chosen.remove(candidate.word) else chosen.add(candidate.word) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on -> if (on) chosen.add(candidate.word) else chosen.remove(candidate.word) },
                        )
                        Text(
                            text = if (candidate.count > 1) "${candidate.word}  ×${candidate.count}" else candidate.word,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The weekly copies, newest first; picking one adds back what it holds (nothing is removed). */
@Composable
private fun WeeklyDialog(
    onDismiss: () -> Unit,
    onPick: (PersonalWords.WeeklyCopy) -> Unit,
) {
    var copies by remember { mutableStateOf<List<PersonalWords.WeeklyCopy>?>(null) }
    LaunchedEffect(Unit) { copies = withContext(Dispatchers.IO) { PersonalWords.weeklyCopies() } }
    val formatter = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }

    JetPrefAlertDialog(
        scrollModifier = florisDialogScroll(),
        title = stringRes(R.string.typebetter__weekly_title),
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
    ) {
        Column {
            HintText(stringRes(R.string.typebetter__weekly_hint))
            val list = copies
            when {
                list == null -> Unit
                list.isEmpty() -> Text(stringRes(R.string.typebetter__weekly_none), modifier = Modifier.padding(top = 8.dp))
                else -> list.forEach { copy ->
                    Text(
                        text = stringRes(R.string.typebetter__weekly_item, "date" to formatter.format(java.util.Date(copy.savedAt))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(copy) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        modifier = Modifier.padding(top = 8.dp),
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun readClipboard(context: Context): String = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull().orEmpty().take(MAX_LEARN_TEXT)

private const val MAX_LEARN_TEXT = 200_000
