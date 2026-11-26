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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Measures [AutoCommitGate.allowsInContext]: a word with two neighbouring-key slips (`wotj` for `with`)
 * swapped in on Space when the previous word says so (`words wotj` → `with`).
 *
 * Runs the real [CorrectionReaders.byTouch] with the real bigram context over the shipped English files,
 * against the same two populations as [AutocorrectEvalTest]:
 *  - **must be fixed**: English words typed after a word they really follow (sampled from the bigram
 *    table by count), with one or two clean neighbour-key slips;
 *  - **must not be touched**: German words unknown to en.json, typed dead-centre after each of twenty
 *    of the most common English words. An upper bound on harm: a name or foreign word normally follows
 *    words that are not this good at predicting the next one.
 */
class ContextSlipEvalTest {

    private companion object {
        const val SAMPLE_SIZE = 2000
        const val SEED = 20261126L
        val ALPHABET = ('a'..'z').toSet()

        val en: Map<String, Int> by lazy { EvalKeyboard.readDict("en.json").filterKeys { EvalKeyboard.typeable(it) } }
        val de: Map<String, Int> by lazy { EvalKeyboard.readDict("de.json").filterKeys { EvalKeyboard.typeable(it) } }
        val prefixIndex: TouchBeamDecoder.PrefixIndex by lazy {
            val words = en.keys.sorted().toTypedArray()
            TouchBeamDecoder.PrefixIndex(words, IntArray(words.size) { en.getValue(words[it]) })
        }
        val bigramText: String by lazy { EvalKeyboard.dictFile("en_bigrams.txt").readText() }
        val bigrams: NgramIndex by lazy { NgramIndex.parse(bigramText) }

        val COMMON_PREVIOUS = listOf(
            "the", "a", "to", "and", "of", "i", "you", "my", "it", "is",
            "in", "that", "for", "on", "with", "was", "this", "we", "be", "have",
        )
    }

    /** One decision as the provider makes it: the touch reading plus what the gate is asked. */
    private data class Case(
        val prev: String,
        val typed: String,
        val lead: String,
        val cost: Float?,
        val margin: Double?,
        val leadFreq: Int,
        val context: Long,
        val total: Long,
    )

    private fun count(prev: String, word: String) = bigrams.countOf("$prev $word")

    private fun read(prev: String?, typed: String, taps: FloatArray = EvalKeyboard.tapsFor(typed)): Case? {
        val ctx: (String) -> Double = if (prev == null) {
            { 0.0 }
        } else {
            { cand -> CorrectionReaders.CONTEXT_WEIGHT * kotlin.math.ln((count(prev, cand) + 1L).toDouble()) }
        }
        val reading = CorrectionReaders.byTouch(
            points = taps, typed = typed, folded = typed, freq = en, alphabet = ALPHABET,
            prefixIndex = prefixIndex, layout = EvalKeyboard.layout, maxCount = CorrectionReaders.MAX_CORRECTIONS,
            contextScore = ctx,
        ) ?: return null
        val lead = reading.words.first()
        return Case(
            prev = prev.orEmpty(), typed = typed, lead = lead, cost = reading.topCost, margin = reading.topMargin,
            leadFreq = en[lead] ?: 0, context = if (prev == null) 0L else count(prev, lead),
            total = if (prev == null) 0L else bigrams.totalAfter(prev),
        )
    }

    /** What Space does, exactly as `LatinLanguageProvider.suggest()` decides it for a touch reading. */
    private fun swaps(
        c: Case,
        strength: AutoCorrectStrength,
        contextRule: Boolean = true,
        rule: AutoCommitGate.ContextRule? = AutoCommitGate.contextRule(strength),
    ): Boolean {
        val cost = c.cost ?: return false // length-changing edits keep their own rule, not measured here
        if (c.leadFreq < AutoCommitGate.MIN_FREQ) return false
        if (AutoCommitGate.allows(cost, c.typed.length, strength)) return true
        return contextRule && AutoCommitGate.allowsInContext(
            cost, c.typed.length, strength, c.leadFreq, c.context, c.total, c.margin, rule,
        )
    }

    // ── The reported cases ───────────────────────────────────────────────────────────────────────

    @Test
    fun wordsWotjBecomesWith() {
        val c = read("words", "wotj")!!
        assertEquals("with", c.lead)
        // Two whole-key slips in four letters: over the per-tap budget, so only context can carry it.
        assertFalse(AutoCommitGate.allows(c.cost!!, 4, AutoCorrectStrength.BALANCED))
        assertTrue(swaps(c, AutoCorrectStrength.BALANCED), "words wotj must become words with ($c)")
        assertTrue(swaps(c, AutoCorrectStrength.AGGRESSIVE))
        assertFalse(swaps(c, AutoCorrectStrength.CAUTIOUS), "Cautious keeps the per-tap budget only")
    }

    @Test
    fun wotjWithoutContextIsOnlyOffered() {
        // `with` and `work` are a hair apart on taps and frequency alone: nothing says which, so neither
        // is swapped in. Both stay in the strip.
        val c = read(null, "wotj")!!
        assertTrue(c.lead == "with" || c.lead == "work")
        assertFalse(swaps(c, AutoCorrectStrength.BALANCED))
    }

    @Test
    fun everydaySlipsAfterAWordThatPredictsThem() {
        for ((prev, typed, meant) in listOf(
            Triple("words", "wotj", "with"),   // two slips: o for i, j for h
            Triple("i", "rhinl", "think"),     // two slips: r for t, l for k
            Triple("of", "rhe", "the"),        // one slip, but 1.0 over 3 taps is over the 0.30 budget
            Triple("i", "jbow", "know"),       // two slips: j for k, b for n
        )) {
            val c = read(prev, typed)!!
            assertEquals(meant, c.lead, "$prev $typed")
            assertTrue(swaps(c, AutoCorrectStrength.BALANCED), "$prev $typed must become $meant ($c)")
        }
    }

    @Test
    fun aCommonPreviousWordIsNotEnough() {
        // German `dich` after `to`: `to such` was seen 149 times, but `to` is followed by everything, so
        // that is a tiny share of what follows it. Left alone, as it was before this rule.
        for (prev in listOf("to", "in", "for", "have")) {
            val c = read(prev, "dich") ?: continue
            assertFalse(swaps(c, AutoCorrectStrength.BALANCED), "$prev dich must stay ($c)")
        }
    }

    // ── The measurement ──────────────────────────────────────────────────────────────────────────

    private fun fixPopulation(): List<Triple<String, Case, String>> {
        // (prev, word) pairs weighted by how often they occur, so the sample looks like running text.
        val pairs = ArrayList<Pair<String, String>>()
        val weights = ArrayList<Long>()
        for (line in bigramText.lineSequence()) {
            val tab = line.indexOf('\t')
            if (tab < 0) continue
            val parts = line.substring(0, tab).split(' ')
            if (parts.size != 2) continue
            val (p, w) = parts
            if (w.length < 3 || (en[w] ?: 0) < 150 || p !in en) continue
            pairs.add(p to w)
            weights.add(line.substring(tab + 1).trim().toLongOrNull() ?: continue)
        }
        val cumulative = LongArray(weights.size)
        var sum = 0L
        for (i in weights.indices) { sum += weights[i]; cumulative[i] = sum }
        val rng = Random(SEED)
        val out = ArrayList<Triple<String, Case, String>>()
        repeat(SAMPLE_SIZE) {
            val r = (rng.nextDouble() * sum).toLong()
            var at = cumulative.binarySearch(r)
            if (at < 0) at = -at - 1
            val (prev, word) = pairs[at.coerceAtMost(pairs.size - 1)]
            for ((kind, slips) in listOf("clean 1" to 1, "clean 2" to 2)) {
                val (typed, taps) = EvalKeyboard.cleanNeighbourSlip(word, rng, slips) ?: continue
                if (typed in en) continue
                val c = read(prev, typed, taps) ?: continue
                out.add(Triple(word, c, kind))
            }
        }
        return out
    }

    /** The readings, and how many (word, previous word) pairs were typed — the harm denominator. */
    private fun keepPopulation(): Pair<List<Case>, Int> {
        val foreign = de.keys.filter { EvalKeyboard.isForeignStandIn(it, en) }.sorted()
            .shuffled(Random(SEED)).take(SAMPLE_SIZE / 4)
        return foreign.flatMap { w -> COMMON_PREVIOUS.mapNotNull { p -> read(p, w) } } to
            foreign.size * COMMON_PREVIOUS.size
    }

    @Test
    fun measureTheContextRuleAgainstBothPopulations() {
        val mustFix = fixPopulation()
        val (mustKeep, typedKeep) = keepPopulation()
        val two = mustFix.filter { it.third == "clean 2" }
        val one = mustFix.filter { it.third == "clean 1" }

        fun fixed(cases: List<Triple<String, Case, String>>, s: AutoCorrectStrength, ctx: Boolean) =
            cases.count { (w, c, _) -> c.lead == w && swaps(c, s, ctx) } * 100.0 / cases.size
        fun wrong(cases: List<Triple<String, Case, String>>, s: AutoCorrectStrength, ctx: Boolean) =
            cases.count { (w, c, _) -> c.lead != w && swaps(c, s, ctx) } * 100.0 / cases.size
        fun broken(s: AutoCorrectStrength, ctx: Boolean) =
            mustKeep.count { c -> c.lead != c.typed && swaps(c, s, ctx) } * 100.0 / typedKeep

        println()
        println("=== two-slip words in context ===========================================")
        println("must be fixed: ${one.size} one-slip, ${two.size} two-slip; must not be touched: $typedKeep")
        println("%-26s %9s %9s %9s %9s".format("rule", "1 fixed", "2 fixed", "→WRONG", "BROKEN"))
        for (s in AutoCorrectStrength.entries) for (ctx in listOf(false, true)) {
            println(
                "%-26s %8.1f%% %8.1f%% %8.2f%% %8.2f%%".format(
                    "$s" + if (ctx) " + context" else "",
                    fixed(one, s, ctx), fixed(two, s, ctx), wrong(mustFix, s, ctx), broken(s, ctx),
                ),
            )
        }
        println()
        // The neighbourhood of the shipped Balanced rule, so the next person to touch it sees the curve.
        println("%-44s %9s %9s %9s".format("Balanced + context rule", "2 fixed", "→WRONG", "BROKEN"))
        for (budget in listOf(0.45f, 0.55f, 0.70f)) for (share in listOf(0.0, 0.005, 0.01, 0.02)) {
            for (margin in listOf(0.0, 0.5, 1.0)) {
                val r = AutoCommitGate.ContextRule(budget, 230, 20, share, margin)
                val f = two.count { (w, c, _) -> c.lead == w && swaps(c, AutoCorrectStrength.BALANCED, true, r) }
                val wr = mustFix.count { (w, c, _) -> c.lead != w && swaps(c, AutoCorrectStrength.BALANCED, true, r) }
                val br = mustKeep.count { c -> c.lead != c.typed && swaps(c, AutoCorrectStrength.BALANCED, true, r) }
                println(
                    "%-44s %8.1f%% %8.2f%% %8.2f%%".format(
                        "budget %.2f share %.3f margin %.1f".format(budget, share, margin),
                        f * 100.0 / two.size, wr * 100.0 / mustFix.size, br * 100.0 / typedKeep,
                    ),
                )
            }
        }
        println()

        val b = AutoCorrectStrength.BALANCED
        // What the rule must buy: a real share of the two-slip typos the per-tap budget cannot see.
        assertTrue(fixed(two, b, true) - fixed(two, b, false) > 10.0, "context must fix two-slip typos")
        // What it may cost: correctly typed unknown words barely move, even after the most predictive
        // previous words, and fresh wrong words stay rare.
        assertTrue(broken(b, true) - broken(b, false) < 0.1, "context must not rewrite correct words")
        assertTrue(wrong(mustFix, b, true) - wrong(mustFix, b, false) < 1.0, "context must not invent words")
        assertEquals(broken(AutoCorrectStrength.CAUTIOUS, false), broken(AutoCorrectStrength.CAUTIOUS, true))
    }
}
