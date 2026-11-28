package com.typebetter.personal

/**
 * Puts My words back into a dictation where the speech model wrote a sound-alike instead: "Naya" for
 * Niah, "Zephania" for Zephaniah, "Strad" for Strahd.
 *
 * Why this exists: the on-device Whisper (sherpa-onnx 1.13.3) takes no prompt, so My words cannot be
 * given to it the way they are given to a cloud model. The transcript is corrected afterwards instead,
 * on every path, after My fixes ([TranscriptFixer]).
 *
 * A heard word becomes a My word only when all of this holds:
 * - both have the same [SoundKey] and no other My word has that key (never a guess between two);
 * - the heard word is not itself a My word, and no "Never change" entry blocks the pair;
 * - the heard word is not a real word in its own right. It may be one the dictionary does not know at
 *   all, or a rare one that the model itself wrote with a capital, the way it writes a name: under
 *   [MAX_FREQ_NAME] in the middle of a sentence, under [MAX_FREQ_SENTENCE_START] at the start of one,
 *   where every word has a capital. Common words ("now", "soon") and lower-case real words ("silos")
 *   are never changed, the rule the keyboard follows for typing too.
 *
 * [frequencyOf] gives a word's dictionary frequency (128..255) or null when the dictionary does not
 * know it. It is only asked about words that already sound like a My word.
 */
class HeardNameFixer(myWords: List<String>, private val rules: PersonalRules = PersonalRules()) {

    /**
     * My words, and the names My corrections turn something into ("Naya" → "Niah"): a correction to a
     * capitalised word says that name is meant, so a dictation that heard it as "Nia" or "Nyah" gets it
     * too, not only the one spelling the correction names. Only automatic, single-word corrections.
     */
    private val names: List<String> = (
        myWords + rules.fixes
            .filter { it.mode == FixMode.AUTO && !it.isPhrase && it.meant.trim().firstOrNull()?.isUpperCase() == true }
            .map { it.meant.trim() }
        ).distinctBy { keyOf(it) }

    private val byKey: Map<String, String> = run {
        val groups = HashMap<String, MutableSet<String>>()
        for (word in names) {
            val w = word.trim()
            if (!isTarget(w)) continue
            val key = SoundKey.of(w) ?: continue
            groups.getOrPut(key) { LinkedHashSet() }.add(w)
        }
        // Two My words that sound the same: the transcript cannot say which was meant, so neither is used.
        groups.filterValues { it.size == 1 }.mapValues { it.value.first() }
    }

    private val myKeys: Set<String> = names.map { keyOf(it) }.toHashSet()

    val isEmpty: Boolean get() = byKey.isEmpty()

    fun apply(input: String, frequencyOf: (String) -> Int?): String {
        if (input.isEmpty() || byKey.isEmpty()) return input
        val text = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFC)
        if (!looksEnglish(text, frequencyOf)) return text
        val out = StringBuilder(text.length)
        var cursor = 0
        for (match in TOKEN.findAll(text)) {
            val token = match.value
            // "Naya's" is Niah's; any other apostrophe ("don't") is not a name.
            val possessive = POSSESSIVE.find(token)
            val heard = possessive?.groupValues?.get(1) ?: token
            if (heard.any { it in APOSTROPHE_CHARS }) continue
            val replacement = replacementFor(heard, isSentenceStart(text, match.range.first), frequencyOf) ?: continue
            out.append(text, cursor, match.range.first).append(replacement)
            if (possessive != null) out.append(possessive.groupValues[2])
            cursor = match.range.last + 1
        }
        if (cursor == 0) return text
        out.append(text, cursor, text.length)
        return out.toString()
    }

    private fun replacementFor(heard: String, sentenceStart: Boolean, frequencyOf: (String) -> Int?): String? {
        if (heard.length < MIN_HEARD_LENGTH) return null
        if (keyOf(heard) in myKeys) return null
        val key = SoundKey.of(heard) ?: return null
        val target = byKey[key] ?: return null
        if (kotlin.math.abs(target.length - heard.length) > MAX_LENGTH_DIFFERENCE) return null
        if (rules.isBlocked(heard, target)) return null
        val freq = frequencyOf(heard.lowercase())
        if (freq != null) {
            val writtenAsName = heard.first().isUpperCase() && !heard.all { !it.isLetter() || it.isUpperCase() }
            if (!writtenAsName) return null
            val limit = if (sentenceStart) MAX_FREQ_SENTENCE_START else MAX_FREQ_NAME
            if (freq >= limit) return null
        }
        val replacement = CaseStyle.apply(heard, target)
        return replacement.takeIf { it != heard }
    }

    private fun isTarget(word: String): Boolean {
        if (word.length < MIN_HEARD_LENGTH || word.any { it.isWhitespace() || it.isDigit() }) return false
        val letters = word.filter { it.isLetter() }
        // Acronyms (NPC, MCP) are spelled out, not sounded; nothing to hear them as.
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return false
        return letters.any { it.lowercaseChar() in "aeiouy" }
    }

    /**
     * A dictation the language detector took for another language would have almost no English words,
     * and almost every one of them would count as "unknown" here. So with [MIN_WORDS_TO_JUDGE] words or
     * more lower-case words (not counting the ones that sound like a My word), at least [MIN_ENGLISH_SHARE] of them have
     * to be English ones before anything is changed.
     */
    private fun looksEnglish(text: String, frequencyOf: (String) -> Int?): Boolean {
        // Only lower-case words vote: a capitalised one may be a name (a misheard one, a My word, or a
        // name nobody listed), and names say nothing about the language. Words that sound like a My word
        // are what is being judged, so they do not vote either. Curly apostrophes are read as straight
        // ones, the way the dictionary writes "don't".
        val words = TOKEN.findAll(text).map { it.value }
            .filter { w -> w.length >= 2 && w.first().isLowerCase() }
            .map { w -> w.replace(CURLY_APOSTROPHES, "'") }
            .filter { w ->
                val stem = POSSESSIVE.find(w)?.groupValues?.get(1) ?: w
                keyOf(stem) !in myKeys && SoundKey.of(stem)?.let { key -> key in byKey } != true
            }
            .toList()
        if (words.size < MIN_WORDS_TO_JUDGE) return true
        val known = words.count { frequencyOf(it) != null }
        return known >= words.size * MIN_ENGLISH_SHARE
    }

    private fun isSentenceStart(text: String, at: Int): Boolean {
        var i = at - 1
        while (i >= 0) {
            val ch = text[i]
            // A new line or a colon starts something new too: the stricter limit applies.
            if (ch == '\n' || ch == ':') return true
            if (ch.isWhitespace() || ch in OPENERS) {
                i--
                continue
            }
            return ch in ENDERS
        }
        return true
    }

    companion object {
        /** A rare word the model capitalised mid-sentence is read as a misheard name below this. */
        const val MAX_FREQ_NAME = 170

        /** At a sentence start every word has a capital, so only very rare words count. */
        const val MAX_FREQ_SENTENCE_START = 150

        const val MIN_WORDS_TO_JUDGE = 4
        const val MIN_ENGLISH_SHARE = 0.8

        const val MIN_HEARD_LENGTH = 3
        const val MAX_LENGTH_DIFFERENCE = 3

        private const val APOSTROPHE_CHARS = "'’‘ʼ＇"
        private val CURLY_APOSTROPHES = Regex("[\u2019\u2018\u02BC\uFF07]")
        private val TOKEN = Regex("[\\p{L}\\p{M}]+(?:['’‘ʼ＇][\\p{L}\\p{M}]+)*")
        private val POSSESSIVE = Regex("^(.+?)(['’‘ʼ＇][sS])$")
        private const val OPENERS = "\"'“‘([{«-–—*"
        private const val ENDERS = ".!?…"
    }
}

/**
 * A rough English sound key for names: the consonants, plus each vowel sound reduced to one of four
 * classes. Spellings that sound alike share a key: Niah, Naya and Nia → `nIA`; Zephaniah and Zefania →
 * `sIfAnIA`; Xylos and silos → `sIlOs`. Noah (`nO`, too short to use) stays apart from Niah. It is deliberately coarse;
 * [HeardNameFixer] decides with it only together with its other conditions.
 *
 * Returns null for a word with nothing to compare (fewer than three key characters).
 */
object SoundKey {
    fun of(word: String): String? {
        var s = fold(word)
        if (s.length < 2) return null
        s = when {
            s.startsWith("kn") || s.startsWith("wr") || s.startsWith("ps") || s.startsWith("gn") -> s.drop(1)
            s.startsWith("x") -> "s" + s.drop(1)
            s.startsWith("wh") -> "w" + s.drop(2)
            else -> s
        }
        // Silent final e after a consonant: Sune, Arcadiane.
        if (s.length > 3 && s.last() == 'e' && s[s.length - 2] !in VOWELS) s = s.dropLast(1)
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val next = s.getOrNull(i + 1)
            // y opens a word as a consonant (Yaga); everywhere else it is a vowel (Lyla, Naya).
            val isVowel = ch in VOWELS && !(ch == 'y' && i == 0 && next != null && next in VOWELS)
            if (isVowel) {
                var j = i
                while (j < s.length && s[j] in VOWELS) j++
                appendVowels(s.substring(i, j), out)
                i = j
                continue
            }
            val (sound, used) = consonant(s, i)
            if (sound.isNotEmpty() && (out.isEmpty() || !out.endsWith(sound))) out.append(sound)
            i += used
        }
        return out.toString().takeIf { it.length >= 3 }
    }

    private const val VOWELS = "aeiouy"

    private fun fold(word: String): String =
        java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFD)
            .lowercase()
            .filter { it in 'a'..'z' }

    /** The sound of the consonant at [i] and how many letters it took. "" for a silent letter. */
    private fun consonant(s: String, i: Int): Pair<String, Int> {
        val ch = s[i]
        val next = s.getOrNull(i + 1)
        val prev = s.getOrNull(i - 1)
        return when (ch) {
            'p' -> if (next == 'h') "f" to 2 else "p" to 1
            's' -> if (next == 'h') "S" to 2 else "s" to 1
            't' -> when {
                next == 'h' -> "T" to 2
                next == 'c' && s.getOrNull(i + 2) == 'h' -> "C" to 3
                else -> "t" to 1
            }
            'c' -> when (next) {
                'h' -> "C" to 2
                'k' -> "k" to 2
                'e', 'i', 'y' -> "s" to 1
                else -> "k" to 1
            }
            'g' -> if (next == 'h') (if (i == 0) "g" to 2 else "" to 2) else "g" to 1
            'q' -> if (next == 'u') "kw" to 2 else "k" to 1
            'x' -> "ks" to 1
            'z' -> "s" to 1
            // h after a vowel or at the end is not heard: Niah, Strahd, Sarah.
            'h' -> if (prev != null && prev in VOWELS || next == null) "" to 1 else "h" to 1
            else -> ch.toString() to 1
        }
    }

    /** One run of vowel letters as vowel classes, repeats merged: "aya" → IA, "ia" → IA, "oa" → OA. */
    private fun appendVowels(run: String, out: StringBuilder) {
        var k = 0
        while (k < run.length) {
            val (cls, used) = vowel(run, k)
            if (out.isEmpty() || out.last() != cls) out.append(cls)
            k += used
        }
    }

    private fun vowel(run: String, k: Int): Pair<Char, Int> {
        val two = run.substring(k, minOf(k + 2, run.length))
        return when (two) {
            "ay", "ai", "ey", "ei", "ee", "ea", "ie" -> 'I' to 2
            "oa", "au" -> 'O' to 2
            "oo", "ue", "ou" -> 'U' to 2
            else -> when (run[k]) {
                'a' -> 'A' to 1
                'o' -> 'O' to 1
                'u' -> 'U' to 1
                else -> 'I' to 1 // e, i, y
            }
        }
    }
}
