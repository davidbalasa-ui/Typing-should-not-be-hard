package com.typebetter.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One test (or more) per finding of the blind review of 2026-11-15. */
class ReviewFixesTest {
    private data class Cand(val text: String, val auto: Boolean = false)

    // --- Auto-correct off

    @Test
    fun `with auto-correct off no personal rule swaps a word, but the fix is still offered first`() {
        val rules = TypingRules(PersonalRules().withFix(Fix("defence", "defense")), listOf("PR"))
        val fix = rules.apply("defence", listOf(Cand("defend")), { it.text }, { it.auto }, autoCorrect = false)
        assertEquals("defense", fix.first().text)
        assertTrue(fix.none { it.autoCommit }, "nothing may be swapped in by Space")

        val word = rules.apply("pr", listOf(Cand("or")), { it.text }, { it.auto }, autoCorrect = false)
        assertEquals("PR", word.first().text)
        assertTrue(word.none { it.autoCommit })
    }

    @Test
    fun `with auto-correct on the same fix still swaps`() {
        val rules = TypingRules(PersonalRules().withFix(Fix("defence", "defense")), emptyList())
        val out = rules.apply("defence", listOf(Cand("defend")), { it.text }, { it.auto })
        assertEquals("defense", out.single { it.autoCommit }.text)
    }

    // --- Revert counter cap

    @Test
    fun `a new pair still reaches its second undo when the counter table is full`() {
        var rules = PersonalRules()
        repeat(600) { i -> rules = rules.recordRevert("word$i", "other$i").first }
        assertEquals(500, rules.revertCounts.size)
        val (once, firstAsk) = rules.recordRevert("Kasmyra", "Kashmir")
        assertFalse(firstAsk)
        assertEquals(1, once.revertCounts[pairKey("Kasmyra", "Kashmir")], "the pair just undone is kept")
        val (_, secondAsk) = once.recordRevert("Kasmyra", "Kashmir")
        assertTrue(secondAsk, "the second undo asks, as it does with an empty table")
    }

    @Test
    fun `when trimming, pairs undone more often are kept over single undos`() {
        var rules = PersonalRules()
        rules = rules.recordRevert("often", "oft").first.recordRevert("often", "oft").first
        repeat(600) { i -> rules = rules.recordRevert("word$i", "other$i").first }
        assertEquals(2, rules.revertCounts[pairKey("often", "oft")])
    }

    // --- Files from newer versions, Notepad files

    @Test
    fun `an unknown fix mode from a newer version is read as suggest-only, not as a lost file`() {
        val text = PersonalCodec.encodeRules(PersonalRules().withFix(Fix("xylose", "Xylos")))
            .replace("\"AUTO\"", "\"SMART\"")
        val rules = assertNotNull(PersonalCodec.decodeRules(text))
        assertEquals(FixMode.SUGGEST, rules.fixFor("xylose")?.mode)
    }

    @Test
    fun `an unknown source from a newer version is read as imported`() {
        val backup = PersonalBackup.of(PersonalRules().withFix(Fix("xylose", "Xylos")), listOf("Xylos"), 1)
        val text = PersonalCodec.encodeBackup(backup).replace("\"USER\"", "\"SYNCED\"")
        val read = assertNotNull(PersonalCodec.decodeBackup(text))
        assertEquals(Source.IMPORTED, read.fixes.single().source)
        assertEquals(listOf("Xylos"), read.words)
    }

    @Test
    fun `a backup saved with a byte-order mark still opens`() {
        val text = "﻿" + PersonalCodec.encodeBackup(PersonalBackup.of(PersonalRules(), listOf("Xylos"), 1))
        assertEquals(listOf("Xylos"), assertNotNull(PersonalCodec.decodeBackup(text)).words)
    }

    // --- Apostrophes and whitespace

    @Test
    fun `curly and straight apostrophes are the same word`() {
        assertEquals(keyOf("Ba'al"), keyOf("Ba’al"))
        val rules = TypingRules(PersonalRules(), listOf("Ba'al"))
        assertTrue(rules.isMyWord("Ba’al"))
        val merged = PersonalImport.merge(PersonalRules(), listOf("Ba'al"), PersonalBackup(words = listOf("Ba’al")))
        assertTrue(merged.newWords.isEmpty(), "not imported as a second word")
    }

    @Test
    fun `a fix that only differs in spaces is refused, and skipped on import`() {
        assertFailsWith<IllegalArgumentException> { PersonalRules().withFix(Fix("a  b", "a b")) }
        val result = PersonalImport.merge(PersonalRules(), emptyList(), PersonalBackup(fixes = listOf(Fix("a  b", "a b"))))
        assertEquals(0, result.addedFixes)
        assertTrue(result.rules.fixes.isEmpty())
    }

    // --- Prompt vocabulary

    @Test
    fun `ordinary fix targets do not crowd names out of a full prompt`() {
        val (pack, _) = StarterPack.apply(PersonalRules())
        val names = listOf("Kasmyra", "Zephaniah", "Krezakar'thuul")
        val out = PromptVocabulary.build(emptyList(), pack, names, tokenBudget = 20)
        assertEquals(names, out.take(3), "deliberate My words come first")
        assertFalse(listOf("receive", "definitely", "armor", "defense", "combatant", "homebrew").any { it in out })
    }

    // --- Starter pack

    @Test
    fun `starter fixes whose typed word is a real word only suggest`() {
        for (realWord in listOf("regar", "nephron", "defence", "armour")) {
            assertEquals(FixMode.SUGGEST, StarterPack.fixes.first { it.key == realWord }.mode, realWord)
        }
    }

    @Test
    fun `upgrading from pack version 1 changes modes without bringing deleted entries back`() {
        val v1 = PersonalRules(
            fixes = listOf(
                Fix("regar", "Reygar", FixMode.AUTO, Source.STARTER),
                Fix("nephron", "Nephryon", FixMode.AUTO, Source.USER), // the user's own: left alone
            ),
            starterPackVersion = 1,
        )
        val (upgraded, words) = StarterPack.apply(v1)
        assertEquals(StarterPack.VERSION, upgraded.starterPackVersion)
        assertEquals(FixMode.SUGGEST, upgraded.fixFor("regar")?.mode)
        assertEquals(FixMode.AUTO, upgraded.fixFor("nephron")?.mode)
        assertNull(upgraded.fixFor("xylose"), "a starter fix the user deleted stays deleted")
        assertTrue(words.isEmpty())
    }

    // --- Backups carry the starter pack's state

    @Test
    fun `restoring on a new phone keeps deleted starter fixes deleted and remembers the pack's words`() {
        val (pack, packWords) = StarterPack.apply(PersonalRules())
        val oldPhone = StarterPack.recordWordsAdded(pack, packWords.take(2)).withoutFix("xylose")
        val file = PersonalCodec.encodeBackup(PersonalBackup.of(oldPhone, packWords.take(2), now = 1))

        val (newPhone, _) = StarterPack.apply(PersonalRules()) // the pack is applied at first start
        assertNotNull(newPhone.fixFor("xylose"))
        val restored = PersonalImport.merge(newPhone, emptyList(), assertNotNull(PersonalCodec.decodeBackup(file))).rules
        assertNull(restored.fixFor("xylose"))
        assertEquals(packWords.take(2), restored.starterWords)
        val (off, toDelete) = StarterPack.remove(restored)
        assertEquals(packWords.take(2), toDelete)
        assertTrue(off.fixes.none { it.source == Source.STARTER })
    }

    @Test
    fun `an old backup without starter state removes nothing`() {
        val (newPhone, _) = StarterPack.apply(PersonalRules())
        val old = PersonalBackup(fixes = listOf(Fix("teh", "the")))
        val restored = PersonalImport.merge(newPhone, emptyList(), old).rules
        assertEquals(newPhone.fixes.size + 1, restored.fixes.size)
    }

    // --- Learn from text

    private val dictionary = setOf("self", "hosted", "the", "and", "snake", "case")
    private fun harvest(text: String) = WordHarvester.harvest(text, isKnown = { it.lowercase() in dictionary }).map { it.word }

    @Test
    fun `pieces of identifiers are not offered`() {
        val words = harvest("4chan 0xDEADBEEF snake_case v2beta")
        assertFalse("chan" in words)
        assertFalse(words.any { it.startsWith("xDEAD") || it.startsWith("DEAD") })
        assertFalse("snake" in words || "case" in words)
    }

    @Test
    fun `a compound of known words is not new`() {
        assertFalse("self-hosted" in harvest("a self-hosted Kasmyra"))
        assertTrue("Kasmyra" in harvest("a self-hosted Kasmyra"))
    }

    @Test
    fun `a huge unbroken paste is handled quickly and without crashing`() {
        val start = System.nanoTime()
        harvest("a".repeat(200_000) + "@x")
        harvest("ab-".repeat(70_000))
        harvest("x".repeat(100_000) + " Kasmyra")
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(ms < 3_000, "took $ms ms")
        assertEquals(listOf("Kasmyra"), harvest("x".repeat(100_000) + " Kasmyra"))
    }
}
