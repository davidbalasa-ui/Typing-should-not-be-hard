package com.typebetter.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixes for the 2026-11-17 audits of the optimization pass (16 auditors; see the report). */
class AuditFixesTest {
    // F16: a small file nested thousands of levels deep overflowed the recursive JSON reader and crashed
    // the app on import (StackOverflowError is not an exception the decoder caught).
    @Test
    fun `a deeply nested file is refused instead of crashing`() {
        val deep = "{\"format\":\"${PersonalBackup.FORMAT}\",\"words\":" + "[".repeat(5000) + "]".repeat(5000) + "}"
        assertNull(PersonalCodec.decodeBackup(deep))
        val deepRules = "{\"fixes\":" + "[".repeat(5000) + "]".repeat(5000) + "}"
        assertNull(PersonalCodec.decodeRules(deepRules))
        assertNull(PersonalCodec.decodeBackup(deepRules))
    }

    @Test
    fun `brackets inside words do not count as nesting`() {
        val backup = PersonalBackup(words = listOf("[".repeat(40) + "x", "{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{\\\""))
        val decoded = assertNotNull(PersonalCodec.decodeBackup(PersonalCodec.encodeBackup(backup)))
        assertEquals(backup.words, decoded.words)
        assertFalse(PersonalCodec.nestedTooDeep(PersonalCodec.encodeBackup(backup)))
    }

    @Test
    fun `real files still read`() {
        val rules = PersonalRules().withFix(Fix("teh", "the")).withNeverChange(NeverChange("pr", "or"))
        assertEquals(rules.fixes, PersonalCodec.decodeRules(PersonalCodec.encodeRules(rules))?.fixes)
        val backup = PersonalBackup.of(rules, listOf("Owlbear"), 1L)
        assertEquals(backup, PersonalCodec.decodeBackup(PersonalCodec.encodeBackup(backup)))
    }

    // F10: a starter word the user deleted came back when restoring a backup onto a new phone.
    @Test
    fun `a deleted starter word stays deleted after a restore on a new phone`() {
        val oldPhone = StarterPack.apply(PersonalRules()).let { (rules, words) -> StarterPack.recordWordsAdded(rules, words) }
        val deleted = StarterPack.words.first()
        val afterDelete = StarterPack.forgetWords(oldPhone, listOf(deleted.uppercase()))
        assertFalse(afterDelete.starterWords.any { keyOf(it) == keyOf(deleted) })
        val backup = PersonalBackup.of(afterDelete, StarterPack.words - deleted + "MyOwnWord", 1L)

        // New phone: the pack went in at first start, words included.
        val newPhone = StarterPack.apply(PersonalRules()).let { (rules, words) -> StarterPack.recordWordsAdded(rules, words) }
        val result = PersonalImport.merge(newPhone, StarterPack.words, backup)
        assertEquals(listOf(deleted), result.removedStarterWords)
        assertFalse(result.rules.starterWords.any { keyOf(it) == keyOf(deleted) })
        assertEquals(listOf("MyOwnWord"), result.newWords)
    }

    @Test
    fun `a phone in use, or an old backup, never loses a starter word on import`() {
        val withPack = StarterPack.apply(PersonalRules()).let { (rules, words) -> StarterPack.recordWordsAdded(rules, words) }
        val inUse = withPack.withFix(Fix("teh", "the"))
        val backup = PersonalBackup.of(StarterPack.forgetWords(withPack, listOf(StarterPack.words.first())), emptyList(), 1L)
        assertTrue(PersonalImport.merge(inUse, StarterPack.words, backup).removedStarterWords.isEmpty())
        val oldBackup = backup.copy(starterWords = null)
        assertTrue(PersonalImport.merge(withPack, StarterPack.words, oldBackup).removedStarterWords.isEmpty())
    }

    @Test
    fun `forgetting words leaves the user's own words and other pack words alone`() {
        val rules = PersonalRules(starterWords = listOf("Owlbear", "Feywild"))
        assertEquals(listOf("Feywild"), StarterPack.forgetWords(rules, listOf("owlbear", "NotPack")).starterWords)
        assertTrue(StarterPack.forgetWords(rules, listOf("NotPack")) === rules)
    }

    // F14: the delete dialog says the weekly copy can bring a word back, but this week's copy was rewritten
    // on every save, so a word added and deleted in the same week (or a bulk mistake) was in no copy.
    @Test
    fun `this week's copy keeps what was deleted this week`() {
        val monday = PersonalBackup.of(
            PersonalRules().withFix(Fix("teh", "the")).withNeverChange(NeverChange("pr", "or")),
            listOf("Owlbear", "Feywild"), 1L,
        )
        val tuesday = PersonalBackup.of(PersonalRules().withFix(Fix("recieve", "receive")), listOf("Feywild", "Krezakar"), 2L)
        val copy = tuesday.keepingEarlier(monday)
        assertEquals(listOf("Feywild", "Krezakar", "Owlbear"), copy.words)
        assertEquals(setOf("recieve", "teh"), copy.fixes.map { it.key }.toSet())
        assertEquals(1, copy.neverChange.size)
        assertEquals(2L, copy.exportedAt)
        // Restoring it only adds, so it brings back the deleted word without touching anything else.
        val restored = PersonalImport.merge(PersonalRules().withFix(Fix("recieve", "receive")), listOf("Feywild", "Krezakar"), copy)
        assertEquals(listOf("Owlbear"), restored.newWords)
    }

    @Test
    fun `the current entry wins over the earlier one, and starter fixes are not kept`() {
        val earlier = PersonalBackup(
            words = listOf("owlbear"),
            fixes = listOf(Fix("teh", "tea"), Fix("half ling", "Halfling", source = Source.STARTER)),
        )
        val now = PersonalBackup(words = listOf("Owlbear"), fixes = listOf(Fix("teh", "the")))
        val copy = now.keepingEarlier(earlier)
        assertEquals(listOf("Owlbear"), copy.words)
        assertEquals(listOf(Fix("teh", "the")), copy.fixes)
        assertTrue(now.keepingEarlier(null) === now)
    }
}

/** F17: the part of a My word before an apostrophe or hyphen was autocorrected and offered as a word. */
class MyWordFragmentTest {
    private val rules = TypingRules(PersonalRules(), listOf("Ba'al", "Krezakar’thuul", "Cyan-k", "Owlbear"))

    @Test
    fun `the part before an apostrophe or hyphen is recognised`() {
        assertTrue(rules.isMyWordFragment("Ba"))
        assertTrue(rules.isMyWordFragment("krezakar"))
        assertTrue(rules.isMyWordFragment("Cyan"))
        assertFalse(rules.isMyWordFragment("Owl"))
        assertFalse(rules.isMyWordFragment("Owlbear"))
        assertFalse(rules.isMyWordFragment("B"))
        assertFalse(rules.isMyWordFragment(""))
    }

    @Test
    fun `the keyboard may not autocorrect the fragment`() {
        val out = rules.apply("Ba", listOf("Be" to true, "Ba" to false), { it.first }, { it.second })
        assertTrue(out.none { it.autoCommit }, out.toString())
        val other = rules.apply("Bq", listOf("Be" to true), { it.first }, { it.second })
        assertTrue(other.first().autoCommit)
    }
}

/** Low-severity input findings from the adversarial and data audits (F26, F37-F41, F46-F48). */
class InputEdgeCasesTest {
    @Test
    fun `every apostrophe form is the same letter (F37)`() {
        val key = keyOf("Ba'al")
        for (form in listOf("Ba’al", "Ba‘al", "Baʼal", "Ba＇al")) assertEquals(key, keyOf(form), form)
        val fixer = TranscriptFixer(PersonalRules().withFix(Fix("ba'al", "Ba'al")))
        assertEquals("hail Ba'al now", fixer.apply("hail baʼal now"))
    }

    @Test
    fun `composed and decomposed accents are the same word (F38)`() {
        assertEquals(keyOf("café"), keyOf("café"))
        assertTrue(StripOffers.looksAddable("café"))
        // ...and a fix for "cafe" does not fire inside "café" written with a combining accent.
        assertEquals("café", TranscriptFixer(PersonalRules().withFix(Fix("cafe", "Cafe"))).apply("café"))
    }

    @Test
    fun `Turkish dotted capital I keys as i (F39)`() {
        assertEquals(keyOf("istanbul"), keyOf("İstanbul"))
        assertEquals(keyOf("istanbul").length, keyOf("İstanbul").length)
    }

    @Test
    fun `meant text is tidied and spacing-only fixes are refused (F40)`() {
        assertEquals("Fey Wild", PersonalRules().withFix(Fix("feywild", "  Fey   Wild ")).fixes.single().meant)
        assertFalse(isUsableFix("fey wild", "fey  wild"))
    }

    @Test
    fun `a no-break space works like a space (F41)`() {
        val rules = PersonalRules().withFix(Fix("fey wild", "Feywild"))
        assertEquals("fey wild", rules.fixes.single().typed)
        assertTrue(rules.fixes.single().isPhrase)
        assertEquals("the Feywild", TranscriptFixer(rules).apply("the fey wild"))
        assertEquals("the Feywild", TranscriptFixer(rules).apply("the fey wild"))
    }

    @Test
    fun `fixes with line breaks or huge text are refused (F46)`() {
        assertFalse(isUsableFix("teh", "the\nrm -rf"))
        assertFalse(isUsableFix("x".repeat(50_000), "y"))
        assertTrue(isUsableFix("teh", "the"))
        val backup = PersonalBackup(fixes = listOf(Fix("teh", "the\nmore"), Fix("x".repeat(5000), "y"), Fix("recieve", "receive")))
        val merged = PersonalImport.merge(PersonalRules(), emptyList(), backup)
        assertEquals(listOf("recieve"), merged.rules.fixes.map { it.typed })
    }

    @Test
    fun `invisible characters and emoji are not offered as words (F48)`() {
        assertFalse(StripOffers.looksAddable("Ow​lbear"))
        assertFalse(StripOffers.looksAddable("Owl😀bear"))
        assertTrue(StripOffers.looksAddable("Owlbear"))
    }

    @Test
    fun `a backup cannot mark the user's own word as a pack word (F26)`() {
        val backup = PersonalBackup(words = listOf("MyOwnWord"), starterPackVersion = StarterPack.VERSION,
            starterWords = listOf("MyOwnWord", StarterPack.words.first()))
        val merged = PersonalImport.merge(PersonalRules().withFix(Fix("teh", "the")), emptyList(), backup)
        assertEquals(listOf(StarterPack.words.first()), merged.rules.starterWords)
    }
}
