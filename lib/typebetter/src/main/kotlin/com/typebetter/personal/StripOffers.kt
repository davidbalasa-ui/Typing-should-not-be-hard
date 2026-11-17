package com.typebetter.personal

/**
 * One-tap offers in the suggestion strip, shown at the moment a word goes wrong:
 *
 * - [AddWord] — "+ Kasmyra": after a word the keyboard does not know, and after an automatic correction
 *   was taken back with Backspace. One tap puts the word on My words, which also stops it ever being
 *   corrected again.
 * - [StopChanging] — after the same correction was taken back twice: one tap saves it as Never change,
 *   so only that one bad correction stops while the rest of autocorrect keeps working.
 */
sealed interface StripOffer {
    data class AddWord(val word: String) : StripOffer
    data class StopChanging(val typed: String, val corrected: String) : StripOffer
}

/** The word the offer is about: the one to add, or the one that keeps being corrected. */
val StripOffer.word: String
    get() = when (this) {
        is StripOffer.AddWord -> word
        is StripOffer.StopChanging -> typed
    }

object StripOffers {
    /**
     * Backspace just put [typed] back where autocorrect had written [corrected].
     *
     * @return the rules with the undo counted, and the offers to show (the question first, when asked).
     */
    fun afterUndo(
        rules: PersonalRules,
        typed: String,
        corrected: String,
        isMyWord: Boolean,
    ): Pair<PersonalRules, List<StripOffer>> {
        val word = typed.trim()
        val (updated, ask) = rules.recordRevert(word, corrected.trim())
        val offers = buildList {
            if (ask) add(StripOffer.StopChanging(word, corrected.trim()))
            if (!isMyWord && looksAddable(word)) add(StripOffer.AddWord(word))
        }
        return updated to offers
    }

    /** A word was finished as typed. Offers it when the keyboard does not know it. */
    fun afterWord(word: String, isKnown: Boolean, isMyWord: Boolean): StripOffer? {
        val trimmed = word.trim()
        return if (!isKnown && !isMyWord && looksAddable(trimmed)) StripOffer.AddWord(trimmed) else null
    }

    /** A word worth offering: letters, no spaces, not a link or an address, not a stray letter or two. */
    fun looksAddable(word: String): Boolean {
        if (word.isEmpty() || word.length > MAX_LENGTH) return false
        if (word.any { it.isWhitespace() || it == '@' || it == '/' || it == '\\' }) return false
        // Invisible characters (zero-width joiners and spaces, direction marks) and emoji would make a
        // look-alike of a real word, or something that is not a word at all.
        if (word.any { Character.getType(it) == Character.FORMAT.toInt() || it.isSurrogate() }) return false
        val letters = word.count { it.isLetter() }
        if (letters < 2) return false
        // Two letters only as an acronym (DM, PC); short lower-case scraps are usually typos.
        if (word.length < 3 && !word.all { it.isUpperCase() }) return false
        // A word may end in a combining mark (an accent typed as its own character, Hindi vowel signs).
        val last = word.last()
        return word.first().isLetterOrDigit() &&
            (last.isLetterOrDigit() || Character.getType(last) in COMBINING_MARKS)
    }

    private const val MAX_LENGTH = 40
    private val COMBINING_MARKS = setOf(
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(),
    )
}
