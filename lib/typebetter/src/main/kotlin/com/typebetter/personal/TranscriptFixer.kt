package com.typebetter.personal

/**
 * Applies My fixes to a finished dictation transcript.
 *
 * Only [FixMode.AUTO] fixes run here: a transcript has no suggestion strip to offer a [FixMode.SUGGEST]
 * word in, and swapping a real word ("silos") behind the user's back is exactly what that mode exists
 * to prevent. Phrases ("fey wild" → Feywild) work here, unlike while typing, because the whole
 * transcript is known at once.
 *
 * Matching ignores case and respects word edges, so "nephron" is fixed but "nephrons" is not. Longer
 * phrases are tried first, so "weave maker" wins over a fix for "weave". The replacement keeps the
 * capitalisation of what was heard, as [CaseStyle] describes. Text that was already replaced is not
 * matched again by a later fix.
 */
class TranscriptFixer(rules: PersonalRules) {
    private val fixes: List<Pair<Regex, Fix>> = rules.fixes
        .filter { it.mode == FixMode.AUTO && it.typed.isNotBlank() && it.meant.isNotBlank() }
        .sortedByDescending { keyOf(it.typed).length }
        .mapNotNull { fix ->
            // A straight and a curly apostrophe are the same letter to a listener.
            val words = keyOf(fix.typed).split(' ').map { word ->
                word.split('\'', '\u2019').joinToString("['\u2019]") { Regex.escape(it) }
            }
            // Between the words of a phrase, any run of spaces; around it, no letter or digit.
            val core = words.joinToString("\\s+")
            runCatching { Regex("(?<![\\p{L}\\p{N}])$core(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE) }
                .getOrNull()?.let { it to fix }
        }

    val isEmpty: Boolean get() = fixes.isEmpty()

    fun apply(text: String): String {
        if (text.isEmpty() || fixes.isEmpty()) return text
        // Work on spans so a replaced word is never rewritten by a second rule.
        val taken = BooleanArray(text.length)
        val replacements = mutableListOf<Triple<Int, Int, String>>()
        for ((regex, fix) in fixes) {
            for (match in regex.findAll(text)) {
                val range = match.range
                if ((range.first..range.last).any { taken[it] }) continue
                val replacement = CaseStyle.apply(match.value, fix.meant)
                if (replacement == match.value) continue
                for (i in range) taken[i] = true
                replacements += Triple(range.first, range.last + 1, replacement)
            }
        }
        if (replacements.isEmpty()) return text
        val out = StringBuilder(text.length)
        var cursor = 0
        for ((start, end, replacement) in replacements.sortedBy { it.first }) {
            out.append(text, cursor, start).append(replacement)
            cursor = end
        }
        out.append(text, cursor, text.length)
        return out.toString()
    }
}
