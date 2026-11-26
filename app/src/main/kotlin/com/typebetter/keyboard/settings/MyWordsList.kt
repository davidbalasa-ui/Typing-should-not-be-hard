package com.typebetter.keyboard.settings

import com.typebetter.personal.Fix
import com.typebetter.personal.FixMode
import com.typebetter.personal.NeverChange
import com.typebetter.personal.PersonalRules
import com.typebetter.personal.isUsableFix
import com.typebetter.personal.keyOf

/**
 * The one "My words" list (David, 2026-11-26: "merge words and fixes"): each word with the typos it fixes
 * and the changes it is protected from. Pure, so the grouping is unit tested without a device.
 *
 * Data does not move: My words stay in the personal dictionary, fixes and never-change rules stay in
 * personal-rules.json. This only groups them for display.
 */
internal data class WordEntry(
    /** The spelling shown: the My words spelling when there is one, else the fix target or protected word. */
    val word: String,
    /** Whether [word] is in the personal dictionary (never corrected, suggested early, heard by dictation). */
    val isMyWord: Boolean,
    /** Typos that turn into [word], oldest first. */
    val fixes: List<Fix>,
    /** Changes the keyboard may not make to [word] by itself. */
    val blocks: List<NeverChange>,
) {
    val tag: WordTag
        get() = when {
            fixes.any { it.mode == FixMode.AUTO } -> WordTag.AUTO_FIX
            fixes.isNotEmpty() -> WordTag.SUGGEST_ONLY
            blocks.isNotEmpty() -> WordTag.NEVER_CHANGE
            else -> WordTag.NONE
        }
}

internal enum class WordTag { AUTO_FIX, SUGGEST_ONLY, NEVER_CHANGE, NONE }

internal enum class WordFilter { ALL, FIXES, SUGGEST, NEVER_CHANGE }

internal fun WordEntry.matches(filter: WordFilter): Boolean = when (filter) {
    WordFilter.ALL -> true
    WordFilter.FIXES -> fixes.isNotEmpty()
    WordFilter.SUGGEST -> fixes.any { it.mode == FixMode.SUGGEST }
    WordFilter.NEVER_CHANGE -> blocks.isNotEmpty()
}

/** Case-insensitive search over the word, its typos and the changes it is protected from. */
internal fun WordEntry.matches(query: String): Boolean {
    val q = keyOf(query)
    if (q.isEmpty()) return true
    return keyOf(word).contains(q) ||
        fixes.any { keyOf(it.typed).contains(q) } ||
        blocks.any { keyOf(it.blocked).contains(q) }
}

/** Groups [myWords] and [rules] into one entry per word, A to Z. */
internal fun buildWordEntries(myWords: List<String>, rules: PersonalRules): List<WordEntry> {
    val spelling = LinkedHashMap<String, String>()
    val inDictionary = HashSet<String>()
    myWords.forEach { w ->
        val k = keyOf(w)
        if (k.isEmpty()) return@forEach
        spelling.putIfAbsent(k, w.trim())
        inDictionary += k
    }
    val fixesByWord = rules.fixes.groupBy { keyOf(it.meant) }
    val blocksByWord = rules.neverChange.groupBy { keyOf(it.typed) }
    fixesByWord.forEach { (k, list) -> spelling.putIfAbsent(k, list.first().meant.trim()) }
    blocksByWord.forEach { (k, list) -> spelling.putIfAbsent(k, list.first().typed.trim()) }
    return spelling.map { (k, shown) ->
        WordEntry(
            word = shown,
            isMyWord = k in inDictionary,
            fixes = fixesByWord[k].orEmpty().sortedBy { it.addedAt },
            blocks = blocksByWord[k].orEmpty(),
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.word })
}

/** What the quick-add form saves. */
internal data class QuickAdd(
    /** Added to My words when no typos were given (a typo list only adds fixes, so "for" never lands there). */
    val word: String?,
    val fixes: List<Fix>,
    /** Typos that could not be saved (same as the word, too long, ...), shown back to the user. */
    val rejected: List<String>,
)

/**
 * Reads the quick-add form: [word] spelled right, [typosText] comma- or space-separated (a typo is one
 * word: phrases go through "Add a fix"). Returns null when there is nothing to save.
 */
internal fun planQuickAdd(
    word: String,
    typosText: String,
    mode: FixMode,
    now: Long,
    isValidWord: (String) -> Boolean,
): QuickAdd? {
    val w = word.trim()
    if (w.isEmpty()) return null
    val typos = typosText.split(',', ' ', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        .distinctBy { keyOf(it) }
    if (typos.isEmpty()) {
        return if (isValidWord(w)) QuickAdd(word = w, fixes = emptyList(), rejected = emptyList()) else null
    }
    val (ok, bad) = typos.partition { isUsableFix(it, w) }
    if (ok.isEmpty()) return QuickAdd(word = null, fixes = emptyList(), rejected = bad)
    return QuickAdd(
        word = null,
        fixes = ok.map { Fix(typed = it, meant = w, mode = mode, addedAt = now) },
        rejected = bad,
    )
}
