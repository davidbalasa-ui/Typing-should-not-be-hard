package com.typebetter.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalCodecTest {
    private val rules = PersonalRules()
        .withFix(Fix("xylose", "Xylos", addedAt = 5))
        .withFix(Fix("silos", "Xylos", FixMode.SUGGEST, Source.STARTER))
        .withNeverChange(NeverChange("its", "it's"))
        .recordRevert("Kasmyra", "Kashmir").first

    @Test
    fun `rules survive a save and load unchanged`() {
        assertEquals(rules, PersonalCodec.decodeRules(PersonalCodec.encodeRules(rules)))
    }

    @Test
    fun `a damaged or foreign file is refused rather than half read`() {
        assertNull(PersonalCodec.decodeRules("{\"fixes\": [ {\"typed\": "))
        assertNull(PersonalCodec.decodeRules("not json"))
        assertNull(PersonalCodec.decodeBackup("{\"format\":\"something-else\"}"))
    }

    @Test
    fun `fields from a newer version are ignored`() {
        val text = PersonalCodec.encodeRules(rules).replaceFirst("{", "{\"futureField\": 42,")
        assertEquals(rules, PersonalCodec.decodeRules(text))
    }

    @Test
    fun `export, wipe and import bring every entry back`() {
        val words = listOf("Xylos", "Kasmyra", "WeaveMaker")
        val file = PersonalCodec.encodeBackup(PersonalBackup.of(rules, words, now = 1))
        val backup = assertNotNull(PersonalCodec.decodeBackup(file))

        val restored = PersonalImport.merge(PersonalRules(), emptyList(), backup)
        assertEquals(words, restored.newWords)
        assertEquals(rules.fixes.toSet(), restored.rules.fixes.toSet())
        assertEquals(rules.neverChange.map { it.key }, restored.rules.neverChange.map { it.key })
        assertEquals(2, restored.addedFixes)
        assertEquals(1, restored.addedBlocks)
    }

    @Test
    fun `importing never overwrites or removes what the user has`() {
        val mine = PersonalRules().withFix(Fix("xylose", "Xylophone")).withNeverChange(NeverChange("silos", "Xylos"))
        val backup = PersonalBackup.of(rules, listOf("xylos"), now = 1)
        val result = PersonalImport.merge(mine, listOf("Xylos"), backup)
        assertEquals("Xylophone", result.rules.fixFor("xylose")?.meant)
        assertTrue(result.rules.isBlocked("silos", "Xylos"))
        assertNull(result.rules.fixFor("silos"), "the user's block wins over the imported fix")
        assertTrue(result.newWords.isEmpty())
    }

    @Test
    fun `a saved rules file or weekly copy restores its fixes`() {
        val restored = assertNotNull(PersonalCodec.decodeBackup(PersonalCodec.encodeRules(rules)))
        assertEquals(rules.fixes, restored.fixes)
        assertEquals(rules.neverChange, restored.neverChange)
        assertTrue(restored.words.isEmpty())
    }

    @Test
    fun `unrelated JSON is still refused`() {
        assertNull(PersonalCodec.decodeBackup("""{"name": "something else"}"""))
        assertNull(PersonalCodec.decodeBackup("not json"))
        assertNull(PersonalCodec.decodeBackup("""{"fixes": []}"""))
    }
}

class PromptVocabularyTest {
    @Test
    fun `custom words come first, then fix targets, then deliberate spellings`() {
        val rules = PersonalRules().withFix(Fix("xylose", "Xylos"))
        val out = PromptVocabulary.build(
            customWords = listOf("Kubernetes"),
            rules = rules,
            myWords = listOf("homebrew", "Kasmyra", "xylos"),
            tokenBudget = 100,
        )
        assertEquals(listOf("Kubernetes", "Xylos", "Kasmyra", "homebrew"), out)
    }

    @Test
    fun `everyday words are left out of the prompt`() {
        val out = PromptVocabulary.build(emptyList(), PersonalRules(), listOf("repo", "zod"), 100) { it == "repo" }
        assertEquals(listOf("zod"), out)
    }

    @Test
    fun `the list stops at the budget but the user's own words always go`() {
        val custom = listOf("Supercalifragilistic")
        val out = PromptVocabulary.build(custom, PersonalRules(), List(50) { "Name$it" }, tokenBudget = 12)
        assertEquals("Supercalifragilistic", out.first())
        val used = out.drop(1).sumOf { PromptVocabulary.estimateTokens(it) }
        assertTrue(used <= 12, "added words stay inside the budget, used $used")
        assertTrue(out.size > 1)
    }
}

class StarterPackTest {
    @Test
    fun `the pack adds its fixes once and hands back its words`() {
        val (rules, words) = StarterPack.apply(PersonalRules())
        assertEquals(StarterPack.fixes.size, rules.fixes.size)
        assertEquals(StarterPack.words, words)
        assertEquals(StarterPack.VERSION, rules.starterPackVersion)

        val (again, moreWords) = StarterPack.apply(rules.withoutFix("xylose"))
        assertNull(again.fixFor("xylose"), "a deleted starter fix does not come back")
        assertTrue(moreWords.isEmpty())
    }

    @Test
    fun `the user's own fix for the same word is kept`() {
        val mine = PersonalRules().withFix(Fix("silos", "Silo"))
        val (rules, _) = StarterPack.apply(mine)
        assertEquals("Silo", rules.fixFor("silos")?.meant)
        assertEquals(Source.USER, rules.fixFor("silos")?.source)
    }

    @Test
    fun `switching the pack off removes only what it added`() {
        val (applied, words) = StarterPack.apply(PersonalRules().withFix(Fix("teh", "the")))
        val recorded = StarterPack.recordWordsAdded(applied, words.take(3))
        assertTrue(StarterPack.isActive(recorded))

        val (off, toDelete) = StarterPack.remove(recorded)
        assertEquals(listOf("teh"), off.fixes.map { it.typed })
        assertEquals(words.take(3), toDelete)
        assertFalse(StarterPack.isActive(off))

        val (back, _) = StarterPack.apply(off, force = true)
        assertNotNull(back.fixFor("xylose"))
    }

    @Test
    fun `the pack is internally consistent`() {
        val keys = StarterPack.fixes.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "one fix per word")
        StarterPack.fixes.forEach { assertTrue(it.typed != it.meant, "${it.typed} must change something") }
        val words = StarterPack.words.map { keyOf(it) }
        assertEquals(words.size, words.toSet().size, "no duplicate words")
        // Real words must not be swapped automatically.
        for (realWord in listOf("silos", "corn", "heaven", "arcades", "keyframe")) {
            assertEquals(FixMode.SUGGEST, StarterPack.fixes.first { it.key == realWord }.mode)
        }
    }
}
