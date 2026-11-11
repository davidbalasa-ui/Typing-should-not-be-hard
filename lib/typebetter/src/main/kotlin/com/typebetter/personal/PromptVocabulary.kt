package com.typebetter.personal

/**
 * Chooses which of the user's words go into the speech model's prompt.
 *
 * Whisper reads a short list of expected words before it listens and then spells them that way: it is the
 * main cure for "silos" when Xylos was said. The window is small (about 190 tokens once the style prompt
 * has its share), so the list is ranked:
 *
 * 1. the user's own custom dictation words, untouched and first, exactly as before;
 * 2. the words My fixes point to — the proven trouble words;
 * 3. My words with deliberate spelling (capitals, digits, unusual letters), the ones a speech model
 *    cannot guess;
 * 4. the remaining My words.
 *
 * Within a group, the original order is kept. Duplicates (ignoring case) are dropped. The list stops at
 * the budget instead of letting the model silently cut it off half-way through a word.
 */
object PromptVocabulary {
    /** Rough token cost: three characters a token, non-ASCII counted double, plus the ", " joint. */
    fun estimateTokens(word: String): Int {
        val weight = word.sumOf { if (it.code < 128) 1 else 2 }
        return (weight + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN + SEPARATOR_TOKENS
    }

    fun build(
        customWords: List<String>,
        rules: PersonalRules,
        myWords: List<String>,
        tokenBudget: Int,
        /** True for an ordinary dictionary word, which needs no help being spelled. */
        isCommonWord: (String) -> Boolean = { false },
    ): List<String> {
        val fixTargets = rules.fixes.map { it.meant }
        val (deliberate, plain) = myWords.partition { isDeliberate(it) }
        val ranked = customWords + fixTargets + deliberate + plain.filterNot { isCommonWord(keyOf(it)) }

        val seen = HashSet<String>()
        val out = mutableListOf<String>()
        var used = 0
        for ((index, raw) in ranked.withIndex()) {
            val word = raw.trim()
            if (word.isEmpty() || !seen.add(keyOf(word))) continue
            val cost = estimateTokens(word)
            // The user's own dictation words always go out as they did before TypeBetter; only what we
            // add on top has to fit. A shorter word further down may still fit after a long one did not.
            val isCustom = index < customWords.size
            if (!isCustom && used + cost > tokenBudget) continue
            out += word
            used += cost
        }
        return out
    }

    private fun isDeliberate(word: String): Boolean =
        word.any { it.isUpperCase() || it.isDigit() || it.code >= 128 || it == '\'' || it == '’' }

    private const val CHARS_PER_TOKEN = 3
    private const val SEPARATOR_TOKENS = 1
}
