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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A typo that starts a rarer dictionary word: `contine` for `continue`, which starts `continent`
 * ([PrefixShadowGate]). Measured on the shipped en.json with the shipping string reader
 * ([CorrectionReaders.byString]) choosing the fix, the way the keyboard does when there are no taps to
 * read or when the fix changes the word's length (the beam cannot see a missing letter).
 *
 * Two populations:
 * - **Missing letter**: every everyday English word (frequency 170+, 5+ letters) with one letter left
 *   out, where what is left is not a word but does start one, and does not start the meant word itself
 *   (`continu` is a half-typed `continue`, which the completions already offer).
 * - **Must stay**: German words absent from en.json (the stand-in for names and jargon the other stands
 *   use, [EvalKeyboard.isForeignStandIn]) that start an English word. Swapping one of these is harm.
 */
class PrefixShadowEvalTest {

    private val en: Map<String, Int> by lazy {
        val out = HashMap<String, Int>()
        for ((w, f) in EvalKeyboard.readDict("en.json")) {
            val k = w.lowercase()
            if (EvalKeyboard.typeable(k)) out[k] = maxOf(out[k] ?: 0, f)
        }
        out
    }
    private val sorted by lazy { en.keys.sorted().toTypedArray() }
    private val alphabet by lazy { en.keys.flatMapTo(HashSet()) { it.asIterable() } }
    private val prefixIndex by lazy { TouchBeamDecoder.PrefixIndex(sorted) }

    private val layout = EvalKeyboard.layout
    private val sqDistance: (Char, Char) -> Float? = { a, b ->
        if (a == b) 0f else {
            val ia = layout.indexOf(a)
            val ib = layout.indexOf(b)
            if (ia < 0 || ib < 0) null else layout.sqDistanceBetween(ia, ib)
        }
    }

    /** The most common dictionary word [typed] starts (not counting itself), or null when it starts none. */
    private fun topCompletion(typed: String): Int? {
        var i = sorted.binarySearch(typed).let { if (it < 0) -it - 1 else it }
        var best: Int? = null
        while (i < sorted.size && sorted[i].startsWith(typed)) {
            if (sorted[i] != typed) best = maxOf(best ?: 0, en.getValue(sorted[i]))
            i++
        }
        return best
    }

    private fun lead(typed: String): String? =
        CorrectionReaders.byString(typed, en, alphabet, prefixIndex, sqDistance, { 0.0 }, allowDistance2 = true)
            .takeIf { !it.distance1Empty }?.words?.firstOrNull()

    private data class Outcome(val offeredFirst: Boolean, val swapped: Boolean, val lead: String?)

    private fun decide(typed: String, strength: AutoCorrectStrength): Outcome {
        val top = topCompletion(typed) ?: return Outcome(false, false, null)
        val fix = lead(typed) ?: return Outcome(false, false, null)
        val v = PrefixShadowGate.judge(typed, fix, en[fix] ?: 0, top, strength)
        return Outcome(v.offerFirst, v.autoCommit, fix)
    }

    private val missingLetter: List<Pair<String, String>> by lazy {
        en.filter { (w, f) -> f >= AutoCommitGate.MIN_FREQ && w.length >= 5 }.keys.sorted().flatMap { w ->
            w.indices.map { i -> w.removeRange(i, i + 1) }.distinct()
                .filter { it !in en && !w.startsWith(it) && topCompletion(it) != null }
                .map { it to w }
        }
    }

    private val mustStay: List<String> by lazy {
        EvalKeyboard.readDict("de.json").keys.map { it.lowercase() }.distinct()
            .filter { EvalKeyboard.typeable(it) && it.length >= 4 && EvalKeyboard.isForeignStandIn(it, en) }
            .filter { topCompletion(it) != null && lead(it) != null }
    }

    private fun pct(n: Int, of: Int) = if (of == 0) 0.0 else 100.0 * n / of

    @Test
    fun `contine becomes continue, wich is offered first`() {
        for (strength in listOf(AutoCorrectStrength.BALANCED, AutoCorrectStrength.AGGRESSIVE)) {
            val o = decide("contine", strength)
            assertEquals("continue", o.lead)
            assertTrue(o.offeredFirst && o.swapped, "contine at $strength: $o")
            // Four letters say too little to swap on, but `which` goes ahead of `wichita`.
            val wich = decide("wich", strength)
            assertEquals("which", wich.lead)
            assertTrue(wich.offeredFirst && !wich.swapped, "wich at $strength: $wich")
        }
        // Cautious: offered first, never swapped.
        val cautious = decide("contine", AutoCorrectStrength.CAUTIOUS)
        assertTrue(cautious.offeredFirst && !cautious.swapped)
    }

    @Test
    fun `a word of the user's own that the typo starts keeps the typo alone`() {
        val v = PrefixShadowGate.judge("contine", "continue", 238, Int.MAX_VALUE, AutoCorrectStrength.AGGRESSIVE)
        assertFalse(v.offerFirst)
        assertFalse(v.autoCommit)
    }

    @Test
    fun `one letter missing`() {
        assertTrue(PrefixShadowGate.isOneLetterMissing("contine", "continue"))
        assertTrue(PrefixShadowGate.isOneLetterMissing("ontinue", "continue"))
        assertTrue(PrefixShadowGate.isOneLetterMissing("continu", "continue"))
        assertFalse(PrefixShadowGate.isOneLetterMissing("continuue", "continue"))
        assertFalse(PrefixShadowGate.isOneLetterMissing("contimue", "continue"))
        assertFalse(PrefixShadowGate.isOneLetterMissing("contniue", "continue"))
        assertFalse(PrefixShadowGate.isOneLetterMissing("contnie", "continue"))
        assertFalse(PrefixShadowGate.isOneLetterMissing("continue", "continue"))
    }

    @Test
    fun `measured on the shipped word list`() {
        val rows = mutableListOf<String>()
        for (strength in AutoCorrectStrength.entries) {
            var fixed = 0
            var wrong = 0
            var offered = 0
            for ((typed, meant) in missingLetter) {
                val o = decide(typed, strength)
                if (o.offeredFirst && o.lead == meant) offered++
                if (o.swapped) { if (o.lead == meant) fixed++ else wrong++ }
            }
            val harm = mustStay.count { decide(it, strength).swapped }
            val harmExamples = mustStay.filter { decide(it, strength).swapped }.take(8)
            rows += "%-10s missing-letter n=%d  offered-first %.1f %%  fixed %.1f %%  wrong word %.1f %%   must-stay n=%d swapped %.2f %% %s"
                .format(strength, missingLetter.size, pct(offered, missingLetter.size), pct(fixed, missingLetter.size),
                    pct(wrong, missingLetter.size), mustStay.size, pct(harm, mustStay.size), harmExamples)
            val n = missingLetter.size
            when (strength) {
                AutoCorrectStrength.CAUTIOUS -> assertEquals(0, fixed + wrong + harm)
                AutoCorrectStrength.BALANCED -> {
                    assertTrue(pct(offered, n) >= 30.0, rows.last())
                    assertTrue(pct(fixed, n) >= 13.0, rows.last())
                    assertTrue(pct(wrong, n) <= 2.0, rows.last())
                    assertTrue(pct(harm, mustStay.size) <= 1.0, rows.last())
                }
                AutoCorrectStrength.AGGRESSIVE -> {
                    assertTrue(pct(fixed, n) >= 17.0, rows.last())
                    assertTrue(pct(wrong, n) <= 2.5, rows.last())
                    assertTrue(pct(harm, mustStay.size) <= 2.0, rows.last())
                }
            }
        }
        println(rows.joinToString("\n"))
    }
}
