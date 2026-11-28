package com.typebetter.personal

/**
 * The rules layer that sits in front of the keyboard's own autocorrect.
 *
 * The keyboard computes its suggestions for the word being typed as usual; [apply] then reorders them and
 * decides which one (if any) Space may swap in. The order of authority is fixed and the same everywhere:
 *
 * 1. **My fixes** — a word with a fix becomes the meant word ([FixMode.AUTO]) or offers it first
 *    ([FixMode.SUGGEST]). Nothing else may replace it.
 * 2. **Never change** — a blocked pair is never swapped in automatically; it stays as a suggestion.
 * 3. **My words** — a word on the list is never replaced. Typed in the wrong case it is put right
 *    ("xylos" → "Xylos"), unless it is also an everyday word ("weave"), which is only offered.
 *    Typing the start of one offers it early.
 * 4. Everything else is the keyboard's own suggestion, unchanged.
 *
 * Generic over the keyboard's candidate type [C] so this file needs no Android or FlorisBoard types.
 */
class TypingRules(
    private val rules: PersonalRules,
    myWords: Collection<String>,
) {
    enum class Origin { STOCK, FIX, MY_WORD }

    /**
     * One suggestion after the rules ran. [stock] is the keyboard's own candidate when the text came from
     * it (so the caller can keep that object), or null for a word the rules added.
     */
    data class Entry<C>(val text: String, val autoCommit: Boolean, val origin: Origin, val stock: C?)

    /** My words by lower-case key; where two spellings share a key, the one with capitals wins. */
    private val wordsByKey: Map<String, String> = buildMap {
        for (raw in myWords) {
            val word = raw.trim()
            if (word.isEmpty()) continue
            val key = keyOf(word)
            val current = get(key)
            if (current == null || (!current.any { it.isUpperCase() } && word.any { it.isUpperCase() })) put(key, word)
        }
    }

    /** Keys in sorted order, for prefix completion by binary search. */
    private val sortedKeys: List<String> = wordsByKey.keys.sorted()

    fun isMyWord(word: String): Boolean = keyOf(word) in wordsByKey

    fun myWordFor(word: String): String? = wordsByKey[keyOf(word)]

    fun <C> apply(
        typed: String,
        stock: List<C>,
        textOf: (C) -> String,
        autoCommitOf: (C) -> Boolean,
        /**
         * The keyboard's own "Auto-correct" setting. Off means Space never swaps a word: My fixes and My
         * words are still offered first on the strip, but only a tap applies them.
         */
        autoCorrect: Boolean = true,
        /** True for an ordinary dictionary word (given in lower case). Only used to stay cautious. */
        isCommonWord: (String) -> Boolean = { false },
    ): List<Entry<C>> {
        val word = typed.trim()
        val stockEntries = stock.map { Entry(textOf(it), autoCommitOf(it), Origin.STOCK, it) }
        // Predictions for the next word (nothing typed yet) and anything with a space are not ours to judge.
        if (word.isEmpty() || word.any { it.isWhitespace() }) return stockEntries

        fun stockFor(text: String): C? = stockEntries.firstOrNull { it.text == text }?.stock

        val front = mutableListOf<Entry<C>>()
        var stockMayAutoCommit = true

        val fix = rules.fixFor(word)?.takeIf { !it.isPhrase }
        val myWord = wordsByKey[keyOf(word)]
        if (fix != null) {
            stockMayAutoCommit = false
            val meant = CaseStyle.apply(word, fix.meant)
            if (meant != word) {
                val auto = autoCorrect && fix.mode == FixMode.AUTO && !rules.isBlocked(word, meant)
                front += Entry(meant, auto, Origin.FIX, stockFor(meant))
            }
        } else if (myWord == null && isMyWordFragment(word)) {
            // "Ba" of "Ba'al", "Krezakar" of "Krezakar-thuul": the apostrophe or hyphen typed next ends the
            // word for the keyboard, which would autocorrect the half it has. It is the start of a My word.
            stockMayAutoCommit = false
        } else if (myWord != null) {
            stockMayAutoCommit = false
            val target = CaseStyle.apply(word, myWord)
            if (target != word) {
                // Only a deliberate spelling is enforced: "rory" → "Rory", but "weave" stays "weave" even
                // though the list has the faction name "Weave".
                val auto = autoCorrect && target.any { it.isUpperCase() } && !isCommonWord(keyOf(word)) && !rules.isBlocked(word, target)
                front += Entry(target, auto, Origin.MY_WORD, stockFor(target))
            }
        }

        val rest = stockEntries.map { entry ->
            val allowed = stockMayAutoCommit && !rules.isBlocked(word, entry.text)
            if (entry.autoCommit && !allowed) entry.copy(autoCommit = false) else entry
        }
        val stockAuto = rest.firstOrNull { it.autoCommit }
        val completions = completionsFor(word).map { Entry(it, false, Origin.MY_WORD, stockFor(it)) }

        val ordered = buildList {
            addAll(front)
            stockAuto?.let { add(it) }
            addAll(completions)
            addAll(rest.filter { it !== stockAuto })
        }
        val seen = HashSet<String>()
        var autoTaken = false
        return ordered.mapNotNull { entry ->
            if (!seen.add(entry.text)) return@mapNotNull null
            if (entry.autoCommit) {
                if (autoTaken) entry.copy(autoCommit = false) else entry.also { autoTaken = true }
            } else {
                entry
            }
        }
    }

    /**
     * The word Space should write for [typed] on the user's own authority alone, or null: the meant word
     * of an automatic My fix, in the typed case, unless Never change blocks it or [autoCorrect] is off.
     *
     * The same decision [apply] makes, asked without the keyboard's suggestions. For the moments those
     * are not there: Space pressed before the strip caught up with the last letter, and a glided word,
     * which never goes through the strip at all. A fix needs no dictionary, so it is never late.
     */
    fun autoFixFor(typed: String, autoCorrect: Boolean = true): String? =
        apply(typed, emptyList<String>(), { it }, { false }, autoCorrect)
            .firstOrNull { it.origin == Origin.FIX && it.autoCommit }
            ?.text

    /**
     * True when [word] is the part before an apostrophe or hyphen of a My word ("Ba" of "Ba'al"), and not a
     * My word itself. The keyboard ends a word at those characters, so such a part must not be corrected
     * or offered as a new word.
     */
    fun isMyWordFragment(word: String): Boolean {
        val key = keyOf(word)
        if (key.isEmpty() || key in wordsByKey) return false
        // keyOf turns ’ into ', so two joiners cover every spelling.
        return JOINERS.any { joiner ->
            val prefix = key + joiner
            val index = sortedKeys.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
            index < sortedKeys.size && sortedKeys[index].startsWith(prefix)
        }
    }

    /** My words that start with [prefix] (at least [MIN_PREFIX] characters), shortest first, in the typed case. */
    fun completionsFor(prefix: String, limit: Int = MAX_COMPLETIONS): List<String> {
        val key = keyOf(prefix)
        if (key.length < MIN_PREFIX || sortedKeys.isEmpty()) return emptyList()
        var index = sortedKeys.binarySearch(key).let { if (it < 0) -it - 1 else it }
        val matches = mutableListOf<String>()
        while (index < sortedKeys.size && sortedKeys[index].startsWith(key)) {
            if (sortedKeys[index] != key) matches += wordsByKey.getValue(sortedKeys[index])
            index++
        }
        return matches
            .sortedWith(compareBy({ it.length }, { it.lowercase() }))
            .take(limit)
            .map { CaseStyle.apply(prefix, it) }
    }

    companion object {
        const val MIN_PREFIX = 3
        const val MAX_COMPLETIONS = 2
        private val JOINERS = listOf("'", "-")
    }
}
