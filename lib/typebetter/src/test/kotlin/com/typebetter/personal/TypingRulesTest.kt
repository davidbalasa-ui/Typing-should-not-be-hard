package com.typebetter.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypingRulesTest {
    /** A stand-in for the keyboard's candidate: its text and whether Space would swap it in. */
    private data class Cand(val text: String, val auto: Boolean = false)

    private fun TypingRules.run(typed: String, vararg stock: Cand) =
        apply(typed, stock.toList(), { it.text }, { it.auto }) { it in common }

    private val common = setOf("weave", "silos", "corn", "cursor", "grog", "heaven", "its", "it's", "the")

    private fun rules(
        rules: PersonalRules = PersonalRules(),
        words: List<String> = emptyList(),
    ) = TypingRules(rules, words)

    private fun <C> List<TypingRules.Entry<C>>.autoWord() = singleOrNull { it.autoCommit }?.text
    private fun <C> List<TypingRules.Entry<C>>.texts() = map { it.text }

    @Test
    fun `without personal entries the keyboard's suggestions pass through unchanged`() {
        val out = rules().run("teh", Cand("the", auto = true), Cand("tech"))
        assertEquals(listOf("the", "tech"), out.texts())
        assertEquals("the", out.autoWord())
        assertEquals(TypingRules.Origin.STOCK, out.first().origin)
    }

    @Test
    fun `a word on My words is never replaced`() {
        val out = rules(words = listOf("Xylos")).run("Xylos", Cand("Xylophone", auto = true))
        assertNull(out.autoWord())
        assertTrue("Xylophone" in out.texts(), "the stock word stays available as a suggestion")
    }

    @Test
    fun `My words puts a deliberately spelled name right when typed in lower case`() {
        val out = rules(words = listOf("Xylos")).run("xylos", Cand("xylose", auto = true))
        assertEquals("Xylos", out.first().text)
        assertEquals("Xylos", out.autoWord())
        assertEquals(TypingRules.Origin.MY_WORD, out.first().origin)
    }

    @Test
    fun `an everyday word on My words is only offered in its capitalised form`() {
        val out = rules(words = listOf("Weave")).run("weave")
        assertEquals("Weave", out.first().text)
        assertNull(out.autoWord(), "weave in an ordinary sentence must stay weave")
    }

    @Test
    fun `typing a My word in capitals is left alone`() {
        val out = rules(words = listOf("Xylos")).run("XYLOS")
        assertNull(out.autoWord())
        assertFalse(out.any { it.origin == TypingRules.Origin.MY_WORD && it.text == "Xylos" })
    }

    @Test
    fun `an automatic fix replaces the typed word and beats the keyboard's own correction`() {
        val rules = PersonalRules().withFix(Fix("recieve", "receive"))
        val out = rules(rules).run("recieve", Cand("relieve", auto = true), Cand("receive"))
        assertEquals("receive", out.autoWord())
        assertEquals("receive", out.first().text)
        assertEquals(listOf("receive", "relieve"), out.texts(), "no duplicate of the meant word")
        assertEquals(Cand("receive"), out.first().stock, "the keyboard's own candidate object is reused")
    }

    @Test
    fun `a fix keeps the case the user typed`() {
        val rules = PersonalRules().withFix(Fix("recieve", "receive"))
        assertEquals("Receive", rules(rules).run("Recieve").autoWord())
        assertEquals("RECEIVE", rules(rules).run("RECIEVE").autoWord())
    }

    @Test
    fun `a fix to a deliberately spelled word keeps that spelling`() {
        val rules = PersonalRules().withFix(Fix("xylose", "Xylos"))
        assertEquals("Xylos", rules(rules).run("xylose").autoWord())
    }

    @Test
    fun `a suggest-only fix offers the meant word first but changes nothing by itself`() {
        val rules = PersonalRules().withFix(Fix("silos", "Xylos", FixMode.SUGGEST))
        val out = rules(rules).run("silos", Cand("solos", auto = true))
        assertEquals("Xylos", out.first().text)
        assertNull(out.autoWord())
    }

    @Test
    fun `phrase fixes do not apply while typing a single word`() {
        val rules = PersonalRules().withFix(Fix("fey wild", "Feywild"))
        val out = rules(rules).run("fey", Cand("few", auto = true))
        assertEquals("few", out.autoWord())
    }

    @Test
    fun `never change blocks only that one correction`() {
        val rules = PersonalRules().withNeverChange(NeverChange("its", "it's"))
        val blocked = rules(rules).run("its", Cand("it's", auto = true))
        assertNull(blocked.autoWord())
        assertTrue("it's" in blocked.texts(), "still offered, just not swapped in")

        val other = rules(rules).run("teh", Cand("the", auto = true))
        assertEquals("the", other.autoWord())
    }

    @Test
    fun `never change also stops a My-words case fix it names`() {
        val rules = PersonalRules().withNeverChange(NeverChange("rory", "Rory"))
        val out = rules(rules, listOf("Rory")).run("rory")
        assertNull(out.autoWord())
    }

    @Test
    fun `My words are suggested from the second letter, shortest first`() {
        val out = rules(words = listOf("Kasmyra", "Kas", "Zephaniah")).run("ka", Cand("kale"))
        assertEquals(listOf("Kas", "Kasmyra", "kale"), out.texts())
        assertNull(out.autoWord())
    }

    @Test
    fun `completions follow a capital typed at the start`() {
        val out = rules(words = listOf("homebrew")).run("Hom")
        assertEquals("Homebrew", out.first().text)
    }

    @Test
    fun `a keyboard correction that is still allowed stays first when completions are added`() {
        val out = rules(words = listOf("Thradrix")).run("thr", Cand("the", auto = true))
        assertEquals("the", out.first().text)
        assertEquals("the", out.autoWord())
        assertEquals("Thradrix", out[1].text)
    }

    @Test
    fun `only one entry may be swapped in automatically`() {
        val out = rules().run("teh", Cand("the", auto = true), Cand("tea", auto = true))
        assertEquals(1, out.count { it.autoCommit })
    }

    @Test
    fun `predictions after a space are left alone`() {
        val out = rules(words = listOf("Xylos")).run("", Cand("the", auto = false))
        assertEquals(listOf("the"), out.texts())
    }

    @Test
    fun `two spellings of one My word prefer the one with capitals`() {
        val r = rules(words = listOf("kas", "Kas"))
        assertEquals("Kas", r.myWordFor("KAS"))
    }
}
