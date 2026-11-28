/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.nlp.latin

/**
 * A typo that happens to start some rarer dictionary word (TypeBetter, 2026-11-28).
 *
 * `contine` is `continue` with the u left out, but it is also the start of `continent`. The keyboard
 * reads a word that starts dictionary words as possibly half-typed, so it used to show those completions
 * first and never swap anything in on Space: `continue` sat fifth in the list, off the visible strip, and
 * the word stood as typed. Space ends a word, though, and nobody presses it half way through `continent`.
 *
 * What decides it is the frequency of the fix against the most common word the typo starts:
 * - **Offered first** (any strength) when the fix is more common than every word the typo starts. It goes
 *   right behind the typed word, ahead of the completions, so it is on the visible strip.
 * - **Swapped in on Space** when it is also an everyday word, clearly more common than those words, the
 *   typed word is long enough to say something, and the fix is the typed word with one letter put back
 *   ([isOneLetterMissing]).
 *
 * Never when a word of the user's own (My words, learned) starts the typo: the caller passes
 * [Int.MAX_VALUE] as [judge]'s top completion then, so nothing beats it.
 *
 * Measured in `PrefixShadowEvalTest` on the shipped en.json.
 */
internal object PrefixShadowGate {

    /** When Space may take the fix: none at Cautious. */
    data class Limits(
        /** The fix's frequency (en.json's 128..255 scale) must be at least this. */
        val minFreq: Int,
        /** ...and at least this far ahead of the most common word the typo starts. */
        val lead: Int,
        /** Letters the typed word needs. */
        val minLength: Int,
    )

    fun limitsFor(strength: AutoCorrectStrength): Limits? = when (strength) {
        AutoCorrectStrength.CAUTIOUS -> null
        AutoCorrectStrength.BALANCED -> BALANCED
        AutoCorrectStrength.AGGRESSIVE -> AGGRESSIVE
    }

    val BALANCED = Limits(minFreq = 200, lead = 5, minLength = 5)
    val AGGRESSIVE = Limits(minFreq = 185, lead = 5, minLength = 5)

    data class Verdict(val offerFirst: Boolean, val autoCommit: Boolean)

    val NONE = Verdict(offerFirst = false, autoCommit = false)

    /**
     * [typed] and [fix] folded (lower case); [topCompletionFreq] the frequency of the most common word
     * [typed] starts, or [Int.MAX_VALUE] when one of the user's own words starts it.
     */
    fun judge(
        typed: String,
        fix: String,
        fixFreq: Int,
        topCompletionFreq: Int,
        strength: AutoCorrectStrength,
    ): Verdict {
        if (fix == typed || fixFreq <= topCompletionFreq) return NONE
        val limits = limitsFor(strength)
        val auto = limits != null &&
            typed.length >= limits.minLength &&
            fixFreq >= limits.minFreq &&
            fixFreq - topCompletionFreq >= limits.lead &&
            isOneLetterMissing(typed, fix)
        return Verdict(offerFirst = true, autoCommit = auto)
    }

    /**
     * [fix] is [typed] with one letter put back — the slip the tap reader cannot see, because it reads
     * one letter per tap. A changed letter is left to the tap reader, which knows where the finger landed
     * (without that evidence the German `frust` would become `trust`), and a letter too many is left alone:
     * that is how most names and foreign words differ from an English one (`wolfs`, `bombe`, `metall`).
     */
    fun isOneLetterMissing(typed: String, fix: String): Boolean {
        if (fix.length != typed.length + 1) return false
        val short = typed
        val long = fix
        var i = 0
        while (i < short.length && short[i] == long[i]) i++
        return short.substring(i) == long.substring(i + 1)
    }
}
