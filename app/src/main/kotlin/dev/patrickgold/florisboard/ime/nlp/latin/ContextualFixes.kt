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

/**
 * Two corrections the edit-distance readers cannot make, both English only (TypeBetter, 2026-11-24:
 * "dor siezers" was left alone — `dor` is in the word list, `siezers` is three edits from `seizures`).
 *
 * - [realWordFix]: a word the dictionary knows, but only barely, one neighbouring key away from a much
 *   more common word that the previous word is constantly followed by (`responsible dor` → `for`). The
 *   corpus behind `en.json` is film subtitles, so it holds thousands of rare strings (`dor`, `thar`,
 *   `yiu`, `tet`) that are far more often a slip than a word. The typed word is never touched when the
 *   bigram table has *ever* seen it after the previous word.
 * - [soundAlikes]: a word spelled the way it sounds, with the consonants right and the vowels wrong
 *   (`siezers`, `definately`, `seperate`). Matched on the consonant skeleton, so the distance does not
 *   matter, only that the consonants agree.
 *
 * Pure functions over the dictionary, the bigram counts and the key geometry, so the rules are measured
 * in `ContextualFixesTest` from the shipped files, not from a copy.
 */
internal object ContextualFixes {

    /** The one language whose bigram table and vowel set these rules were measured on. */
    const val LANG = "en"

    // ── Real-word fix ────────────────────────────────────────────────────────────────────────────

    /** A typed word this common or more is a word, whatever the context says. */
    const val RARE_MAX_FREQ = 170

    /** How much more common the replacement must be than the typed word (en.json's 0–255 scale). */
    const val SUGGEST_FREQ_GAP = 40

    /** How often the previous word must have been followed by the replacement in the bigram table. */
    const val SUGGEST_MIN_CONTEXT = 30L

    /** The stricter bar for swapping it in on Space at [AutoCorrectStrength.BALANCED]. */
    const val AUTO_RARE_MAX_FREQ = 150
    const val AUTO_FREQ_GAP = 50
    const val AUTO_MIN_CONTEXT = 50L

    /**
     * Squared key-width distance within which two keys count as neighbours. KeyProximityInfo divides both
     * axes by the key width, so on a phone a key one row up is ~1.4–1.6 widths away (squared ~2.0–2.5)
     * and a diagonal neighbour a little more; 2.6 takes in the row above and below, not two keys across.
     */
    const val NEIGHBOUR_SQ_DIST = 2.6f

    class Fix(val word: String, val autoCommit: Boolean)

    /**
     * The more likely word [typed] (folded, lowercase) was meant to be after [prev], or null.
     *
     * [freq] is the dictionary, [bigramCount] the raw count of `"prev word"`, [sqDistance] the key
     * geometry (null when a key is unknown, which rules the substitution out). Only single substitutions
     * of a neighbouring key are considered: that is the slip a finger makes, and the one where the typed
     * word carries no evidence that it was meant.
     */
    fun realWordFix(
        typed: String,
        prev: String?,
        freq: Map<String, Int>,
        bigramCount: (String) -> Long,
        sqDistance: (Char, Char) -> Float?,
        strength: AutoCorrectStrength,
    ): Fix? {
        if (prev == null || typed.length < 2 || typed.any { !it.isLetter() }) return null
        val typedFreq = freq[typed] ?: return null
        if (typedFreq >= RARE_MAX_FREQ) return null
        if (bigramCount("$prev $typed") > 0L) return null

        var best: String? = null
        var bestCount = 0L
        var bestFreq = 0
        val chars = typed.toCharArray()
        for (i in chars.indices) {
            val original = chars[i]
            for (c in 'a'..'z') {
                if (c == original) continue
                val d = sqDistance(original, c) ?: continue
                if (d > NEIGHBOUR_SQ_DIST) continue
                chars[i] = c
                val cand = String(chars)
                chars[i] = original
                val candFreq = freq[cand] ?: continue
                if (candFreq < typedFreq + SUGGEST_FREQ_GAP) continue
                val n = bigramCount("$prev $cand")
                if (n < SUGGEST_MIN_CONTEXT) continue
                if (n > bestCount || (n == bestCount && cand < (best ?: cand))) {
                    best = cand
                    bestCount = n
                    bestFreq = candFreq
                }
            }
        }
        val word = best ?: return null
        val auto = when (strength) {
            AutoCorrectStrength.CAUTIOUS -> false
            AutoCorrectStrength.BALANCED -> typedFreq < AUTO_RARE_MAX_FREQ &&
                bestFreq >= typedFreq + AUTO_FREQ_GAP && bestCount >= AUTO_MIN_CONTEXT &&
                bestFreq >= AutoCommitGate.MIN_FREQ
            AutoCorrectStrength.AGGRESSIVE -> bestFreq >= AutoCommitGate.MIN_FREQ
        }
        return Fix(word, auto)
    }

    // ── Contractions ─────────────────────────────────────────────────────────────────────────────

    /**
     * English contractions typed without the apostrophe that may be fixed on Space: the bare spelling is
     * not a word anyone writes on purpose. Words that are also ordinary words (`ill`, `well`, `were`,
     * `its`, `lets`, `hell`, `shell`, `wed`, `shed`, `id`, `hed`) are deliberately absent: they are offered,
     * never swapped. The provider still requires the dictionary to hold exactly one apostrophe form.
     */
    val SAFE_CONTRACTIONS: Set<String> = setOf(
        "im", "ive", "dont", "doesnt", "didnt", "isnt", "wasnt", "arent", "werent", "hasnt", "havent",
        "hadnt", "couldnt", "wouldnt", "shouldnt", "mustnt", "neednt", "cant", "wont", "aint",
        "youre", "theyre", "youve", "theyve", "weve", "youll", "theyll", "shouldve", "wouldve", "couldve",
        "thats", "whats", "theres", "heres", "wheres", "whos", "hows", "hes", "shes",
    )

    // ── Sound-alike fallback ─────────────────────────────────────────────────────────────────────

    /** Shorter words have too few consonants for the skeleton to say anything. */
    const val SOUND_MIN_LEN = 5

    /** Most letters the typed word and the match may differ by, transpositions counting once. */
    const val SOUND_MAX_EDITS = 3

    /** How many sound-alikes the strip is offered. */
    const val SOUND_MAX_OFFERS = 2

    /** Only words this common are offered at all; rarer ones are mostly corpus noise. */
    const val SOUND_MIN_FREQ = 120

    /** Most letters (as a multiset) the typed word and an auto-committed match may differ by. */
    const val SOUND_AUTO_MAX_LETTER_DIFF = 2

    private const val VOWELS = "aeiouy"

    /** [word] without its vowels and with doubled letters collapsed: `seizures` and `siezers` → `szrs`. */
    fun skeleton(word: String): String {
        val sb = StringBuilder(word.length)
        for (ch in word) {
            if (ch in VOWELS) continue
            if (sb.isNotEmpty() && sb[sb.length - 1] == ch) continue
            sb.append(ch)
        }
        return sb.toString()
    }

    private fun keyOf(word: String): String = word[0] + "|" + skeleton(word)

    /** Dictionary words grouped by first letter and [skeleton]. Build once per dictionary. */
    class SoundIndex(freq: Map<String, Int>) {
        internal val groups: Map<String, List<String>> = HashMap<String, MutableList<String>>().also { m ->
            for ((w, f) in freq) {
                if (f < SOUND_MIN_FREQ || w.length < SOUND_MIN_LEN - 1 || !w.all { it in 'a'..'z' }) continue
                m.getOrPut(keyOf(w)) { ArrayList(2) }.add(w)
            }
        }
    }

    class SoundReading(val words: List<String>, val autoCommit: Boolean)

    /**
     * Words [typed] (folded, lowercase, not itself a dictionary word) may be a sound-alike spelling of,
     * best first, and whether the first may be swapped in on Space.
     *
     * Auto-commit is for the case where nothing else would be: the caller passes [othersWouldCommit] and
     * [distance1Empty]. Then it needs exactly one common match, at most one letter longer or shorter, that
     * differs from the typed word by at most [SOUND_AUTO_MAX_LETTER_DIFF] letters as a multiset. Measured
     * on German words the English dictionary lacks, the rule changes about 0.6 % that nothing else would
     * have (asserted under 1 % in `ContextualFixesTest`).
     */
    fun soundAlikes(
        typed: String,
        index: SoundIndex,
        freq: Map<String, Int>,
        strength: AutoCorrectStrength,
        othersWouldCommit: Boolean,
        distance1Empty: Boolean,
    ): SoundReading {
        if (typed.length < SOUND_MIN_LEN || !typed.all { it in 'a'..'z' }) return SoundReading(emptyList(), false)
        val group = index.groups[keyOf(typed)] ?: return SoundReading(emptyList(), false)
        val scored = group
            .filter { it != typed && kotlin.math.abs(it.length - typed.length) <= 2 }
            .map { it to osaDistance(typed, it) }
            .filter { it.second <= SOUND_MAX_EDITS }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenByDescending { freq[it.first] ?: 0 }.thenBy { it.first })
        if (scored.isEmpty()) return SoundReading(emptyList(), false)
        val words = scored.map { it.first }
        val strong = words.filter { w ->
            (freq[w] ?: 0) >= AutoCommitGate.MIN_FREQ &&
                kotlin.math.abs(w.length - typed.length) <= 1 &&
                letterDifference(typed, w) <= SOUND_AUTO_MAX_LETTER_DIFF
        }
        val auto = strength != AutoCorrectStrength.CAUTIOUS && !othersWouldCommit && distance1Empty &&
            strong.size == 1
        val ordered = if (auto) listOf(strong[0]) + (words - strong[0]) else words
        return SoundReading(ordered.take(SOUND_MAX_OFFERS), auto)
    }

    /** Optimal-string-alignment distance: insert, delete, substitute, swap two neighbours. */
    fun osaDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
                d[i][j] = v
            }
        }
        return d[a.length][b.length]
    }

    /** Size of the symmetric difference of the two words' letter multisets. */
    fun letterDifference(a: String, b: String): Int {
        val counts = IntArray(26)
        for (ch in a) if (ch in 'a'..'z') counts[ch - 'a']++
        for (ch in b) if (ch in 'a'..'z') counts[ch - 'a']--
        return counts.sumOf { kotlin.math.abs(it) }
    }
}
