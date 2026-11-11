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
        val letters = word.count { it.isLetter() }
        if (letters < 2) return false
        // Two letters only as an acronym (DM, PC); short lower-case scraps are usually typos.
        if (word.length < 3 && !word.all { it.isUpperCase() }) return false
        return word.first().isLetterOrDigit() && word.last().isLetterOrDigit()
    }

    private const val MAX_LENGTH = 40
}
