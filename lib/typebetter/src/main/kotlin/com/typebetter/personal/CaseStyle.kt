package com.typebetter.personal

/**
 * Carries the capitalisation the user typed over to the word that replaces it.
 *
 * A word with capitals of its own (Xylos, WeaveMaker, MCP) keeps them: that spelling is the point of the
 * entry. Otherwise the typed word decides: "Recieve" becomes "Receive", "RECIEVE" becomes "RECEIVE".
 * A typed word in all capitals always wins, because shouting is deliberate.
 */
object CaseStyle {
    fun apply(typed: String, replacement: String): String {
        if (replacement.isEmpty()) return replacement
        val letters = typed.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return replacement.uppercase()
        if (replacement.any { it.isUpperCase() }) return replacement
        val first = typed.firstOrNull { it.isLetter() } ?: return replacement
        return if (first.isUpperCase()) replacement.replaceFirstChar { it.titlecase() } else replacement
    }

    /** True if [word] has a capital after its first letter (WeaveMaker, TwinHaven, DM). */
    fun hasInnerCapital(word: String): Boolean = word.drop(1).any { it.isUpperCase() }
}
