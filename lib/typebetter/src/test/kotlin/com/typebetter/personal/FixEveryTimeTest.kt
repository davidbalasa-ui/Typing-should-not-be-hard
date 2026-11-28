package com.typebetter.personal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * David, 2026-11-28: "it's not remembering NAYA to NIAH all the time". The strip applied My corrections
 * only when its suggestions for the last letter had arrived before Space, and a glide never asked at all.
 * [TypingRules.autoFixFor] is the same decision without the strip, for those moments.
 */
class FixEveryTimeTest {
    private val naya = PersonalRules().withFix(Fix(typed = "Naya", meant = "Niah"))

    @Test
    fun `the fix is made in every case the word is typed in`() {
        val rules = TypingRules(naya, emptyList())
        assertEquals("Niah", rules.autoFixFor("Naya"))
        assertEquals("Niah", rules.autoFixFor("naya"))
        assertEquals("NIAH", rules.autoFixFor("NAYA"))
    }

    @Test
    fun `it agrees with the strip's own decision`() {
        val rules = TypingRules(naya, emptyList())
        for (typed in listOf("Naya", "naya", "NAYA", "Nayan", "Niah", "the")) {
            val strip = rules.apply(typed, listOf("x"), { it }, { false }).firstOrNull { it.autoCommit }?.text
            assertEquals(strip, rules.autoFixFor(typed), typed)
        }
    }

    @Test
    fun `nothing is made where the strip would not make it either`() {
        assertNull(TypingRules(naya, emptyList()).autoFixFor("Naya", autoCorrect = false))
        assertNull(TypingRules(naya, emptyList()).autoFixFor("Nayan"))
        assertNull(TypingRules(naya, emptyList()).autoFixFor(" "))
        val offerOnly = PersonalRules().withFix(Fix(typed = "Naya", meant = "Niah", mode = FixMode.SUGGEST))
        assertNull(TypingRules(offerOnly, emptyList()).autoFixFor("Naya"))
        val blocked = naya.withNeverChange(NeverChange(typed = "Naya", blocked = "Niah"))
        assertNull(TypingRules(blocked, emptyList()).autoFixFor("Naya"))
        val phrase = PersonalRules().withFix(Fix(typed = "naya s", meant = "Niah's"))
        assertNull(TypingRules(phrase, emptyList()).autoFixFor("naya"))
    }

    private val dictionary: Map<String, Int> by lazy {
        val file = listOf("../../app/src/main/assets/ime/dict/en.json", "app/src/main/assets/ime/dict/en.json")
            .map { File(it) }.first { it.exists() }
        Regex("\"([^\"]+)\":\\s*(\\d+)").findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
    }

    @Test
    fun `dictation hears the corrected name in other spellings too`() {
        // No My words at all: the correction alone says Niah is meant.
        val fixer = HeardNameFixer(emptyList(), naya)
        assertEquals("I talked to Niah today.", fixer.apply("I talked to Nia today.", { dictionary[it] }))
        assertEquals("I talked to Niah today.", fixer.apply("I talked to Nyah today.", { dictionary[it] }))
        // Without the correction nothing changes.
        assertEquals("I talked to Nia today.", HeardNameFixer(emptyList()).apply("I talked to Nia today.", { dictionary[it] }))
    }

    @Test
    fun `only automatic corrections to a capitalised word count as a name`() {
        val lower = PersonalRules().withFix(Fix(typed = "teh", meant = "the"))
        assertEquals("Teh day", HeardNameFixer(emptyList(), lower).apply("Teh day", { dictionary[it] }))
        val offerOnly = PersonalRules().withFix(Fix(typed = "Naya", meant = "Niah", mode = FixMode.SUGGEST))
        assertEquals(
            "I talked to Nia today.",
            HeardNameFixer(emptyList(), offerOnly).apply("I talked to Nia today.", { dictionary[it] }),
        )
    }

    @Test
    fun `a correction's name never takes a sound from a My word`() {
        val fixer = HeardNameFixer(listOf("Nia"), naya)
        // "Nia" is a My word: the correction to Niah does not make it ambiguous, and Nia stays Nia.
        assertEquals("I talked to Nia today.", fixer.apply("I talked to Nyah today.", { dictionary[it] }))
    }
}
