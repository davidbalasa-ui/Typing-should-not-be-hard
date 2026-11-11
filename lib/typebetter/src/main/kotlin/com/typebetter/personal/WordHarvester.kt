package com.typebetter.personal

/**
 * Finds the words in a piece of text that the keyboard does not know yet — the engine behind
 * "Learn from clipboard": copy a Claude conversation, and its names and jargon come back as a list to tick.
 *
 * Nothing is stored here; the caller shows [Candidate]s and adds only the ones the user approves.
 */
object WordHarvester {
    data class Candidate(
        /** The spelling to add: the most frequent one, preferring capitals a writer chose (Xylos, MCP). */
        val word: String,
        val count: Int,
    )

    private val URL = Regex("""\b(?:https?://|www\.)\S+|\S+@\S+\.\S+""", RegexOption.IGNORE_CASE)
    private val CODE = Regex("""`[^`]*`""")

    // A word: letters, with digits, apostrophes or inner hyphens allowed (Auth0, Krezakar'thuul, Ba'al).
    private val WORD = Regex("""[\p{L}][\p{L}\p{M}\p{N}]*(?:['’\-][\p{L}\p{M}\p{N}]+)*""")

    /**
     * @param isKnown true for a word the keyboard already knows (its dictionary, or the user's own list),
     *   given in the spelling found; implementations should ignore case where that makes sense.
     * @param limit the most candidates to return.
     */
    fun harvest(text: String, isKnown: (String) -> Boolean, limit: Int = 200): List<Candidate> {
        if (text.isBlank()) return emptyList()
        val cleaned = CODE.replace(URL.replace(text, " "), " ")
        val forms = LinkedHashMap<String, MutableMap<String, Int>>()
        for (match in WORD.findAll(cleaned)) {
            // "Xylos's" counts as Xylos.
            val raw = match.value.trimEnd('\'', '’', '-').let { word ->
                if (word.length > 3 && (word.endsWith("'s") || word.endsWith("’s"))) word.dropLast(2) else word
            }
            if (!looksLikeAWord(raw)) continue
            val key = keyOf(raw)
            val spellings = forms.getOrPut(key) { HashMap() }
            spellings[raw] = (spellings[raw] ?: 0) + 1
        }
        return forms.values
            .map { spellings -> Candidate(bestSpelling(spellings), spellings.values.sum()) }
            .filterNot { isKnown(it.word) || isKnown(it.word.lowercase()) }
            .sortedWith(compareByDescending<Candidate> { it.count }.thenBy { it.word.lowercase() })
            .take(limit)
    }

    private fun looksLikeAWord(word: String): Boolean {
        val letters = word.count { it.isLetter() }
        if (letters < 2) return false
        // Two-letter words only as acronyms (DM, PC, AC); lower-case ones are noise or typos of real words.
        if (word.length < 3 && !word.all { it.isUpperCase() }) return false
        // A long run of one character is not a word ("aaaaah").
        if (Regex("(.)\\1\\1\\1").containsMatchIn(word.lowercase())) return false
        return word.length <= MAX_WORD_LENGTH
    }

    private fun bestSpelling(spellings: Map<String, Int>): String {
        // A spelling with capitals in the middle or all capitals is deliberate (WeaveMaker, MCP); then
        // a capitalised one (names appear capitalised mid-sentence); otherwise the most common.
        val deliberate = spellings.keys.filter { it.length > 1 && it.drop(1).any(Char::isUpperCase) }
        if (deliberate.isNotEmpty()) return deliberate.maxBy { spellings.getValue(it) }
        val capitalised = spellings.filterKeys { it.first().isUpperCase() }
        val lower = spellings.filterKeys { !it.first().isUpperCase() }
        if (capitalised.isNotEmpty() && capitalised.values.sum() >= lower.values.sum()) {
            return capitalised.maxBy { it.value }.key
        }
        return spellings.maxBy { it.value }.key
    }

    private const val MAX_WORD_LENGTH = 40
}
