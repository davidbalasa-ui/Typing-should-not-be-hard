package com.typebetter.keyboard.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typebetter.keyboard.PersonalWords
import com.typebetter.personal.Fix
import com.typebetter.personal.FixMode
import com.typebetter.personal.NeverChange
import dev.patrickgold.florisboard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.stringRes

/**
 * "Add a word": the word spelled right, and optionally the ways it gets mistyped. With typos it saves a
 * fix for each (automatic or suggest-only); without, it adds the word to My words.
 */
@Composable
internal fun QuickAddForm(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var word by rememberSaveable { mutableStateOf("") }
    var typos by rememberSaveable { mutableStateOf("") }
    var auto by rememberSaveable { mutableStateOf(true) }
    val rules by PersonalWords.rules.collectAsState()
    val plan = planQuickAdd(
        word = word,
        typosText = typos,
        mode = if (auto) FixMode.AUTO else FixMode.SUGGEST,
        now = 0L,
        isValidWord = PersonalWords::isValidWord,
        existing = { rules.fixFor(it) },
    )
    val canSave = plan != null && (plan.word != null || plan.fixes.isNotEmpty())

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringRes(R.string.typebetter__quick_add_title).uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = word,
                onValueChange = { word = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(R.string.typebetter__quick_add_word)) },
                singleLine = true,
                keyboardOptions = verbatimKeyboardOptions(ImeAction.Next),
            )
            OutlinedTextField(
                value = typos,
                onValueChange = { typos = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(R.string.typebetter__quick_add_typos)) },
                supportingText = { Text(stringRes(R.string.typebetter__quick_add_typos_hint)) },
                singleLine = true,
                keyboardOptions = verbatimKeyboardOptions(ImeAction.Done),
            )
            // How the typos are handled; it only matters once there are typos, so it says so when disabled.
            val hasTypos = typos.isNotBlank()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = auto,
                    onClick = { auto = true },
                    enabled = hasTypos,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringRes(R.string.typebetter__fix_mode_auto)) }
                SegmentedButton(
                    selected = !auto,
                    onClick = { auto = false },
                    enabled = hasTypos,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringRes(R.string.typebetter__fix_mode_suggest)) }
            }
            Text(
                text = stringRes(
                    when {
                        !hasTypos -> R.string.typebetter__quick_add_explain_word
                        auto -> R.string.typebetter__quick_add_explain_auto
                        else -> R.string.typebetter__quick_add_explain_suggest
                    },
                ),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (plan != null && plan.taken.isNotEmpty()) {
                Text(
                    text = stringRes(
                        R.string.typebetter__quick_add_taken,
                        "fixes" to plan.taken.joinToString(", ") { it.typed + " → " + it.meant },
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (plan != null && plan.rejected.isNotEmpty()) {
                Text(
                    text = stringRes(R.string.typebetter__quick_add_rejected, "typos" to plan.rejected.joinToString(", ")),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    val p = plan ?: return@Button
                    scope.launch {
                        val already = p.word != null && withContext(Dispatchers.IO) { PersonalWords.isMyWord(p.word) }
                        val saved = withContext(Dispatchers.IO) {
                            when {
                                already -> 0
                                p.word != null -> if (PersonalWords.addWord(p.word)) 1 else 0
                                else -> {
                                    p.fixes.forEach { PersonalWords.setFix(it) }
                                    p.fixes.size
                                }
                            }
                        }
                        if (already) {
                            context.showLongToast(R.string.typebetter__quick_add_word_exists, "word" to p.word)
                        } else if (p.word != null && saved == 0) {
                            context.showLongToast(R.string.typebetter__quick_add_word_failed)
                        } else {
                            word = ""
                            typos = ""
                            context.showLongToast(R.string.typebetter__quick_add_done, "count" to saved)
                        }
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringRes(R.string.typebetter__quick_add_save)) }
        }
    }
}

/**
 * One word of the list: the word in serif with its tag, then the typos it fixes ("← wotj"), each its
 * own tap target, and a + to add another typo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordEntryRow(
    entry: WordEntry,
    onWord: () -> Unit,
    onFix: (Fix) -> Unit,
    onBlock: (NeverChange) -> Unit,
    onAddTypo: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onWord).heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = entry.word,
                        fontFamily = FontFamily.Serif,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TbWordTag(entry.tag)
                }
                if (entry.fixes.isEmpty() && entry.blocks.isEmpty()) {
                    Text(
                        text = stringRes(R.string.typebetter__entry_word),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.fixes.isNotEmpty() || entry.blocks.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        entry.fixes.forEach { fix ->
                            val suggest = fix.mode == FixMode.SUGGEST
                            TypoChip(
                                text = "← " + fix.typed + if (suggest) " · " + stringRes(R.string.typebetter__entry_suggested) else "",
                                spoken = stringRes(
                                    if (suggest) R.string.typebetter__entry_typo_suggest_cd else R.string.typebetter__entry_typo_cd,
                                    "typo" to fix.typed,
                                    "word" to entry.word,
                                ),
                                onClick = { onFix(fix) },
                            )
                        }
                        entry.blocks.forEach { block ->
                            TypoChip(
                                text = "↛ " + block.blocked,
                                spoken = stringRes(R.string.typebetter__entry_block_cd, "word" to entry.word, "blocked" to block.blocked),
                                onClick = { onBlock(block) },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onAddTypo) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringRes(R.string.typebetter__entry_add_typo, "word" to entry.word),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TbHairline()
    }
}

@Composable
private fun TypoChip(text: String, spoken: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken }
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 6.dp),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
