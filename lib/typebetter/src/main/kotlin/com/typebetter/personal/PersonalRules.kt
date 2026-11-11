package com.typebetter.personal

import kotlinx.serialization.Serializable

/**
 * How a fix is applied.
 *
 * [AUTO] swaps the word in by itself, the way autocorrect does. [SUGGEST] only puts the meant word first
 * in the suggestion strip, for a wrong word that is also a real word ("silos" for Xylos): swapping those
 * automatically would break every sentence that really is about silos.
 */
@Serializable
enum class FixMode { AUTO, SUGGEST }

/** Where an entry came from, so a starter-pack entry can be taken back out without touching the user's own. */
@Serializable
enum class Source { USER, STARTER, LEARNED, IMPORTED }

/**
 * One "My fixes" entry: when [typed] is written, [meant] is what the user wanted.
 *
 * [typed] may be a phrase ("fey wild"); phrases only apply to dictation, where a whole transcript arrives
 * at once. Typed words are matched ignoring case.
 */
@Serializable
data class Fix(
    val typed: String,
    val meant: String,
    val mode: FixMode = FixMode.AUTO,
    val source: Source = Source.USER,
    val addedAt: Long = 0L,
) {
    val key: String get() = keyOf(typed)
    val isPhrase: Boolean get() = typed.trim().contains(' ')
}

/** One "Never change" entry: the keyboard may not turn [typed] into [blocked] by itself any more. */
@Serializable
data class NeverChange(
    val typed: String,
    val blocked: String,
    val source: Source = Source.USER,
    val addedAt: Long = 0L,
) {
    val key: String get() = pairKey(typed, blocked)
}

/**
 * Everything TypeBetter keeps about the user's corrections, apart from the words themselves (My words live
 * in the keyboard's personal dictionary, where the prediction engine already reads them).
 *
 * Immutable: every change returns a new copy, which is what the store saves.
 */
@Serializable
data class PersonalRules(
    val version: Int = CURRENT_VERSION,
    val fixes: List<Fix> = emptyList(),
    val neverChange: List<NeverChange> = emptyList(),
    /** How often each automatic correction was undone, keyed by [pairKey]. */
    val revertCounts: Map<String, Int> = emptyMap(),
    /** Pairs the user was already asked about ("Stop changing X to Y?"), answered either way. */
    val askedPairs: Set<String> = emptySet(),
    /** The starter pack version last applied; 0 = never. */
    val starterPackVersion: Int = 0,
    /** Words the starter pack put into the personal dictionary, so switching it off removes only those. */
    val starterWords: List<String> = emptyList(),
) {
    fun fixFor(typed: String): Fix? {
        val key = keyOf(typed)
        return fixes.firstOrNull { it.key == key }
    }

    fun isBlocked(typed: String, candidate: String): Boolean {
        val key = pairKey(typed, candidate)
        return neverChange.any { it.key == key }
    }

    /** Adds or replaces the fix for [fix]'s typed word. A word can only mean one thing. */
    fun withFix(fix: Fix): PersonalRules {
        require(fix.typed.isNotBlank() && fix.meant.isNotBlank()) { "A fix needs both words" }
        require(fix.key != keyOf(fix.meant) || fix.typed != fix.meant) { "A fix must change something" }
        val rest = fixes.filterNot { it.key == fix.key }
        // A fix and a block for the same pair contradict each other; the newer statement wins.
        val blocks = neverChange.filterNot { it.key == pairKey(fix.typed, fix.meant) }
        return copy(fixes = rest + fix.trimmed(), neverChange = blocks)
    }

    fun withoutFix(typed: String): PersonalRules = copy(fixes = fixes.filterNot { it.key == keyOf(typed) })

    fun withNeverChange(entry: NeverChange): PersonalRules {
        require(entry.typed.isNotBlank() && entry.blocked.isNotBlank()) { "A block needs both words" }
        val rest = neverChange.filterNot { it.key == entry.key }
        // "Never turn X into Y" and a fix "X means Y" cannot both stand.
        val fixesLeft = fixes.filterNot { it.key == keyOf(entry.typed) && keyOf(it.meant) == keyOf(entry.blocked) }
        return copy(neverChange = rest + entry.trimmed(), fixes = fixesLeft, askedPairs = askedPairs + entry.key)
    }

    fun withoutNeverChange(typed: String, blocked: String): PersonalRules =
        copy(neverChange = neverChange.filterNot { it.key == pairKey(typed, blocked) })

    /**
     * Notes that an automatic correction from [typed] to [corrected] was undone.
     *
     * Returns the new rules and whether to ask "Stop changing [typed] to [corrected]?" now: on the second
     * undo of the same pair, once, and never for a pair that is already blocked.
     */
    fun recordRevert(typed: String, corrected: String): Pair<PersonalRules, Boolean> {
        if (typed.isBlank() || corrected.isBlank() || keyOf(typed) == keyOf(corrected)) return this to false
        val key = pairKey(typed, corrected)
        val count = (revertCounts[key] ?: 0) + 1
        val updated = copy(revertCounts = (revertCounts + (key to count)).trimmedCounts())
        val ask = count >= ASK_AFTER_REVERTS && key !in askedPairs && !isBlocked(typed, corrected)
        return updated to ask
    }

    /** Remembers that the user answered (or dismissed) the question for this pair. */
    fun markAsked(typed: String, corrected: String): PersonalRules = copy(askedPairs = askedPairs + pairKey(typed, corrected))

    private fun Map<String, Int>.trimmedCounts(): Map<String, Int> =
        if (size <= MAX_REVERT_ENTRIES) this else entries.sortedByDescending { it.value }.take(MAX_REVERT_ENTRIES).associate { it.toPair() }

    companion object {
        const val CURRENT_VERSION = 1
        const val ASK_AFTER_REVERTS = 2
        private const val MAX_REVERT_ENTRIES = 500
    }
}

private fun Fix.trimmed() = copy(typed = typed.trim().replace(WHITESPACE, " "), meant = meant.trim())
private fun NeverChange.trimmed() = copy(typed = typed.trim(), blocked = blocked.trim())

private val WHITESPACE = Regex("\\s+")

/** The form words are compared in: trimmed, single-spaced, lower case. */
fun keyOf(word: String): String = word.trim().replace(WHITESPACE, " ").lowercase()

/** Key for a typed → corrected pair. */
fun pairKey(typed: String, corrected: String): String = keyOf(typed) + "→" + keyOf(corrected)
