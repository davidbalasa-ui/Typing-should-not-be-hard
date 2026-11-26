package com.typebetter.keyboard.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typebetter.personal.Fix
import com.typebetter.personal.FixMode
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes

/**
 * The fix the home card suggests trying: the newest automatic one-word fix, so it is one David made
 * himself and will recognise. Null when there is none yet.
 */
internal fun exampleFix(fixes: List<Fix>): Fix? =
    fixes.filter { it.mode == FixMode.AUTO && !it.isPhrase && !it.meant.contains(' ') }
        .maxByOrNull { it.addedAt }

/**
 * "Try your keyboard": a real text box, so the keyboard itself (with David's fixes, suggestions and
 * autocorrect) does the work while he watches. Taps through to the screens that change what it does.
 */
@Composable
internal fun TryKeyboardCard(
    example: Fix?,
    onAddFix: () -> Unit,
    onOpenWords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
            Text(
                text = stringRes(R.string.typebetter__home_try_label).uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp),
                placeholder = { Text(stringRes(R.string.typebetter__home_try_placeholder)) },
                singleLine = true,
            )
            Text(
                text = if (example != null) {
                    stringRes(R.string.typebetter__home_try_hint, "typed" to example.typed, "meant" to example.meant)
                } else {
                    stringRes(R.string.typebetter__home_try_hint_none)
                },
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpenWords) { Text(stringRes(R.string.typebetter__home_try_words)) }
                TextButton(onClick = onAddFix) { Text(stringRes(R.string.typebetter__home_try_add_fix)) }
            }
        }
    }
}

/** A peek at up to three words with what they fix, under the My words row. */
@Composable
internal fun WordPeek(entries: List<WordEntry>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 60.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = entry.word,
                    modifier = Modifier.width(96.dp),
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = entryDetail(entry),
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    TbHairline()
}

/** "← wotj, wiht" for a word with fixes, else what kind of entry it is. */
@Composable
internal fun entryDetail(entry: WordEntry): String = when {
    entry.fixes.isNotEmpty() -> "← " + entry.fixes.joinToString(", ") { it.typed }
    entry.blocks.isNotEmpty() -> stringRes(R.string.typebetter__entry_never)
    else -> stringRes(R.string.typebetter__entry_word)
}

/** The entries the home peek shows: words that fix typos first (the newest fix first), then the rest. */
internal fun peekEntries(entries: List<WordEntry>, count: Int = 3): List<WordEntry> =
    entries.sortedWith(
        compareByDescending<WordEntry> { it.fixes.isNotEmpty() }
            .thenByDescending { e -> e.fixes.maxOfOrNull { it.addedAt } ?: Long.MIN_VALUE },
    ).take(count)
