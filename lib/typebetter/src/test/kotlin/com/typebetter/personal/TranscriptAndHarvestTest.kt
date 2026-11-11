package com.typebetter.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptFixerTest {
    private val rules = PersonalRules()
        .withFix(Fix("xylose", "Xylos"))
        .withFix(Fix("silos", "Xylos", FixMode.SUGGEST))
        .withFix(Fix("fey wild", "Feywild"))
        .withFix(Fix("weave maker", "WeaveMaker"))
        .withFix(Fix("weave", "Weave"))
        .withFix(Fix("we've maker", "WeaveMaker"))
        .withFix(Fix("nephron", "Nephryon"))

    private val fixer = TranscriptFixer(rules)

    @Test
    fun `automatic fixes apply anywhere in a transcript`() {
        assertEquals("The Xylos army crossed the Feywild.", fixer.apply("The xylose army crossed the fey wild."))
    }

    @Test
    fun `suggest-only fixes never touch a transcript`() {
        assertEquals("The silos were full.", fixer.apply("The silos were full."))
    }

    @Test
    fun `a sentence-start capital is kept`() {
        assertEquals("Nephryon attacked.", fixer.apply("Nephron attacked."))
        assertEquals("Feywild again", fixer.apply("Fey Wild again"))
    }

    @Test
    fun `only whole words and phrases match`() {
        assertEquals("nephrons and nephronic", fixer.apply("nephrons and nephronic"))
    }

    @Test
    fun `the longest phrase wins and replaced text is not rewritten again`() {
        assertEquals("ask the WeaveMaker about the Weave", fixer.apply("ask the weave maker about the weave"))
        assertEquals("the WeaveMaker said", fixer.apply("the we’ve maker said"))
    }

    @Test
    fun `phrases match across line breaks and double spaces`() {
        assertEquals("the Feywild", fixer.apply("the fey\n  wild"))
    }

    @Test
    fun `no fixes means the text is returned as is`() {
        val text = "nothing to see"
        assertTrue(TranscriptFixer(PersonalRules()).isEmpty)
        assertEquals(text, TranscriptFixer(PersonalRules()).apply(text))
    }
}

class WordHarvesterTest {
    private val dictionary = setOf("the", "army", "and", "talked", "to", "about", "copied", "this", "from", "chat", "with")

    private fun harvest(text: String, mine: Set<String> = emptySet()) =
        WordHarvester.harvest(text, isKnown = { it.lowercase() in dictionary || it.lowercase() in mine })

    @Test
    fun `unknown words come back most frequent first`() {
        val out = harvest("Xylos and Kasmyra talked to Xylos about the Xylos army and Kasmyra.")
        assertEquals(listOf("Xylos" to 3, "Kasmyra" to 2), out.map { it.word to it.count })
    }

    @Test
    fun `known words and words already on the list are skipped`() {
        val out = harvest("Xylos and Rory", mine = setOf("rory"))
        assertEquals(listOf("Xylos"), out.map { it.word })
    }

    @Test
    fun `deliberate spellings win over sentence-start capitals`() {
        val out = harvest("weavemaker. WeaveMaker and WeaveMaker. Weavemaker")
        assertEquals("WeaveMaker", out.single().word)
        assertEquals(4, out.single().count)
    }

    @Test
    fun `acronyms are kept, short lower-case scraps are not`() {
        val words = harvest("the DM and an NPC ok xx").map { it.word }
        assertTrue("DM" in words && "NPC" in words)
        assertFalse("ok" in words)
        assertFalse("xx" in words)
    }

    @Test
    fun `links, e-mail addresses and code are ignored`() {
        val out = harvest("see https://ddd-game.vercel.app/play and mail me@example.com or run `npmx verify`")
        assertEquals(listOf("mail", "run", "see"), out.map { it.word }.sorted())
    }

    @Test
    fun `possessives count as the word itself`() {
        val out = harvest("Xylos's army and Xylos")
        assertEquals(listOf("Xylos" to 2), out.map { it.word to it.count })
    }

    @Test
    fun `apostrophes and digits inside names survive`() {
        val words = harvest("Ba'al met Krezakar'thuul and Auth0").map { it.word }
        assertEquals(setOf("Ba'al", "Krezakar'thuul", "Auth0", "met"), words.toSet())
    }

    @Test
    fun `nothing to learn from blank text`() {
        assertTrue(harvest("   ").isEmpty())
    }
}
