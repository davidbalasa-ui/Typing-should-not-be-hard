/*
 * Copyright (C) 2026 TypeBetter contributors
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Measures [ContextualFixes] against the shipped English dictionary and bigram table.
 *
 * The two reported words come first (`responsible dor siezers`), then the cost of each rule: how often
 * a correctly typed word would be changed, measured the same way as [AutocorrectEvalTest] measures harm.
 */
class ContextualFixesTest {

    private companion object {
        /** en.json folded the way the provider folds English: lowercase, the most frequent spelling wins. */
        val freq: Map<String, Int> by lazy {
            val out = HashMap<String, Int>()
            for ((w, f) in EvalKeyboard.readDict("en.json")) {
                val k = w.lowercase()
                if ((out[k] ?: -1) < f) out[k] = f
            }
            out
        }
        val bigrams: NgramIndex by lazy { NgramIndex.parse(EvalKeyboard.dictFile("en_bigrams.txt").readText()) }
        val sound: ContextualFixes.SoundIndex by lazy { ContextualFixes.SoundIndex(freq) }

        fun sq(a: Char, b: Char): Float? {
            val layout = EvalKeyboard.layout
            val i = layout.indexOf(a)
            val j = layout.indexOf(b)
            if (i < 0 || j < 0) return null
            return layout.sqDistanceBetween(i, j)
        }

        fun fix(prev: String?, typed: String, strength: AutoCorrectStrength = AutoCorrectStrength.BALANCED) =
            ContextualFixes.realWordFix(typed, prev, freq, { bigrams.countOf(it) }, ::sq, strength)

        fun sounds(typed: String, distance1Empty: Boolean = true, othersWouldCommit: Boolean = false,
                   strength: AutoCorrectStrength = AutoCorrectStrength.BALANCED) =
            ContextualFixes.soundAlikes(typed, sound, freq, strength, othersWouldCommit, distance1Empty)

        val COMMON_PREVIOUS = listOf(
            "the", "a", "to", "and", "of", "i", "you", "my", "it", "is",
            "in", "that", "for", "on", "with", "was", "this", "we", "be", "have",
        )
    }

    // ── The report ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun responsibleDorBecomesFor() {
        val f = fix("responsible", "dor")
        assertEquals("for", f?.word)
        assertTrue(f!!.autoCommit, "dor after responsible should be swapped on Space at BALANCED")
    }

    @Test
    fun siezersBecomesSeizures() {
        val r = sounds("siezers")
        assertEquals("seizures", r.words.firstOrNull())
        assertTrue(r.autoCommit)
    }

    // ── Real-word fix: the rules ─────────────────────────────────────────────────────────────────

    @Test
    fun cautiousOnlyOffers() {
        val f = fix("responsible", "dor", AutoCorrectStrength.CAUTIOUS)
        assertEquals("for", f?.word)
        assertFalse(f!!.autoCommit)
    }

    @Test
    fun aWordTheContextHasSeenIsLeftAlone() {
        // "a seizure" is in the table, so whatever is rare about a word, the table vouching for it wins.
        val table = NgramIndex.parse("responsible dor\t1\nresponsible for\t1018\n")
        assertNull(ContextualFixes.realWordFix("dor", "responsible", freq, { table.countOf(it) }, ::sq,
            AutoCorrectStrength.AGGRESSIVE))
    }

    @Test
    fun noPreviousWordNoFix() {
        assertNull(fix(null, "dor"))
    }

    @Test
    fun commonWordsAreNeverTouched() {
        // A word at or above RARE_MAX_FREQ is a word, however unlikely the pair.
        for (w in listOf("door", "for", "the", "was", "not")) assertNull(fix("responsible", w), w)
    }

    @Test
    fun farKeysAreNotSlips() {
        // q and p are a keyboard apart: no finger lands on one for the other.
        val table = NgramIndex.parse("xx pat\t500\n")
        val f = mapOf("qat" to 100, "pat" to 250)
        assertNull(ContextualFixes.realWordFix("qat", "xx", f, { table.countOf(it) }, ::sq,
            AutoCorrectStrength.AGGRESSIVE))
    }

    /**
     * Harm: every rare dictionary word typed correctly after twenty common words. Many such pairs are not
     * English at all, so this over-counts; it is a ceiling, not an estimate.
     */
    @Test
    fun harmStaysUnderBudget() {
        val rare = freq.filter { (w, f) -> f < ContextualFixes.RARE_MAX_FREQ && w.all { it in 'a'..'z' } }.keys
        var offered = 0
        var swapped = 0
        var total = 0
        for (w in rare) for (p in COMMON_PREVIOUS) {
            total++
            val f = fix(p, w) ?: continue
            offered++
            if (f.autoCommit) swapped++
        }
        val swapPct = 100.0 * swapped / total
        val offerPct = 100.0 * offered / total
        println("real-word fix: $total pairs, offered ${"%.2f".format(offerPct)} %, swapped ${"%.2f".format(swapPct)} %")
        assertTrue(swapPct < 1.0, "BALANCED swaps $swapPct % of correctly typed rare words")
        assertTrue(offerPct < 1.5, "offers on $offerPct % of correctly typed rare words")
    }

    /** Benefit: a common word after a word it often follows, with one key slipped onto a rare word. */
    @Test
    fun fixesASubstantialShareOfSlips() {
        var total = 0
        var fixed = 0
        for ((w, f) in freq) {
            if (f < 200 || !w.all { it in 'a'..'z' }) continue
            val prevs = COMMON_PREVIOUS.filter { bigrams.countOf("$it $w") >= ContextualFixes.AUTO_MIN_CONTEXT }
            if (prevs.isEmpty()) continue
            for (i in w.indices) for (c in EvalKeyboard.neighbours[w[i]].orEmpty()) {
                val typo = w.substring(0, i) + c + w.substring(i + 1)
                val tf = freq[typo] ?: continue
                if (tf >= ContextualFixes.RARE_MAX_FREQ) continue
                for (p in prevs) {
                    total++
                    val r = fix(p, typo)
                    if (r != null && r.word == w && r.autoCommit) fixed++
                }
            }
        }
        val pct = 100.0 * fixed / total
        println("real-word fix: $total slips onto rare words, swapped back ${"%.1f".format(pct)} %")
        assertTrue(total > 1000)
        assertTrue(pct > 20.0, "fixes only $pct % of slips")
    }

    // ── Sound-alikes ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun classicMisspellings() {
        val expected = mapOf(
            "siezure" to "seizure", "beleive" to "believe",
            "freind" to "friend", "tommorow" to "tomorrow",
            "accomodate" to "accommodate", "neccessary" to "necessary", "arguement" to "argument",
            "existance" to "existence", "independant" to "independent", "buisness" to "business",
            "becuase" to "because", "agressive" to "aggressive",
        )
        // Only words the dictionary lacks reach this rule in the keyboard (`mayCorrect`), so only they count.
        val known = expected.keys.filter { it in freq }
        assertTrue(known.isEmpty(), "in en.json, so never sound-alike corrected: $known")
        val missed = expected.filter { (typo, meant) -> sounds(typo).words.firstOrNull() != meant }
        assertTrue(missed.isEmpty(), "not first: $missed")
    }

    @Test
    fun neverSwappedWhenSomethingElseWouldBe() {
        assertFalse(sounds("siezers", othersWouldCommit = true).autoCommit)
        assertFalse(sounds("siezers", distance1Empty = false).autoCommit)
        assertFalse(sounds("siezers", strength = AutoCorrectStrength.CAUTIOUS).autoCommit)
        // …but still offered.
        assertEquals("seizures", sounds("siezers", othersWouldCommit = true).words.firstOrNull())
    }

    @Test
    fun shortAndOddWordsAreSkipped() {
        assertTrue(sounds("wich").words.isEmpty())
        assertTrue(sounds("s1ezers").words.isEmpty())
    }

    @Test
    fun ambiguousMatchesAreOfferedNotSwapped() {
        // weird, wired and word all share w-r-d: no single answer, so nothing is swapped in.
        val r = sounds("wierd")
        assertFalse(r.autoCommit)
        assertTrue("weird" in r.words, "${r.words}")
    }

    /**
     * Harm, the [AutocorrectEvalTest] way: German words English does not have, typed correctly into the
     * English keyboard. Counted only where nothing else would have swapped them (no dictionary word one
     * edit away), because those are the words this rule newly changes.
     */
    @Test
    fun soundAlikeHarmStaysUnderBudget() {
        val alphabet = ('a'..'z').toSet()
        val german = EvalKeyboard.readDict("de.json").keys.map { it.lowercase() }.toSet()
            .filter { it.length >= ContextualFixes.SOUND_MIN_LEN && it.all { c -> c in 'a'..'z' } && EvalKeyboard.isForeignStandIn(it, freq) }
        var total = 0
        var swapped = 0
        for (w in german) {
            val d1Empty = EditDistance.edits1(w, alphabet).none { it in freq }
            if (!d1Empty) continue
            total++
            if (sounds(w, distance1Empty = true).autoCommit) swapped++
        }
        val pct = 100.0 * swapped / german.size
        println("sound-alike: ${german.size} German words, $total with nothing one edit away, swapped $swapped (${"%.2f".format(pct)} %)")
        assertTrue(german.size > 20_000)
        assertTrue(pct < 1.0, "sound-alike swaps $pct % of correctly typed foreign words")
    }

    // ── Word-list cleanup and contractions ───────────────────────────────────────────────────────

    /**
     * Misspellings the upstream word list held as words (TypeBetter, 2026-11-24), so nothing ever corrected
     * them. Each one is gone, and what it means is one edit away or its sound-alike, so the ordinary
     * corrector now reaches it.
     */
    @Test
    fun removedMisspellingsStayRemovedAndAreReachable() {
        val removed = EvalKeyboard.ENGLISH_TYPOS
        val raw = EvalKeyboard.readDict("en.json").keys
        val stillThere = raw.filter { it.lowercase() in removed }
        assertTrue(stillThere.isEmpty(), "back in en.json: $stillThere")
        val alphabet = ('a'..'z').toSet() + '\''
        val unreachable = removed.filter { (typo, meant) ->
            meant !in freq || (meant !in EditDistance.edits1(typo, alphabet) && meant !in sounds(typo).words)
        }
        assertTrue(unreachable.isEmpty(), "not one edit or a sound-alike away: $unreachable")
    }

    @Test
    fun safeContractionsHaveExactlyOneApostropheForm() {
        // The provider offers a form only when it is more frequent than the bare spelling, so count those.
        val bad = ContextualFixes.SAFE_CONTRACTIONS.filter { bare ->
            val bareFreq = freq[bare] ?: 0
            (1 until bare.length).count { i ->
                val form = freq[bare.substring(0, i) + "'" + bare.substring(i)]
                form != null && form > bareFreq
            } != 1
        }
        // With no single form the keyboard swaps nothing, so a listed word that fails here is dead weight.
        assertTrue(bad.isEmpty(), "contractions without exactly one apostrophe form in en.json: $bad")
    }

    @Test
    fun ambiguousContractionsAreNeverSwapped() {
        val words = listOf("ill", "well", "were", "its", "lets", "hell", "shell", "wed", "shed", "id", "hed")
        val listed = words.filter { it in ContextualFixes.SAFE_CONTRACTIONS }
        assertTrue(listed.isEmpty(), "real words in the swap list: $listed")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun skeletonDropsVowelsAndDoubles() {
        assertEquals("szrs", ContextualFixes.skeleton("seizures"))
        assertEquals("szrs", ContextualFixes.skeleton("siezers"))
        assertEquals("cmdt", ContextualFixes.skeleton("accommodate"))
    }

    @Test
    fun distances() {
        assertEquals(3, ContextualFixes.osaDistance("siezers", "seizures"))
        assertEquals(1, ContextualFixes.osaDistance("ab", "ba"))
        assertEquals(1, ContextualFixes.letterDifference("siezers", "seizures"))
        assertEquals(0, ContextualFixes.letterDifference("listen", "silent"))
    }
}
