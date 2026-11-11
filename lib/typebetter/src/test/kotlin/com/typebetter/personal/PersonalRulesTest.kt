package com.typebetter.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalRulesTest {
    @Test
    fun `a word has at most one fix, the newest`() {
        val rules = PersonalRules()
            .withFix(Fix("teh", "tea"))
            .withFix(Fix("TEH", "the"))
        assertEquals(1, rules.fixes.size)
        assertEquals("the", rules.fixFor("teh")?.meant)
    }

    @Test
    fun `fixes are found ignoring case and extra spaces`() {
        val rules = PersonalRules().withFix(Fix("  fey   wild ", "Feywild"))
        assertEquals("fey wild", rules.fixes.single().typed)
        assertNotNull(rules.fixFor("Fey Wild"))
    }

    @Test
    fun `a fix needs both words and must change something`() {
        assertFailsWith<IllegalArgumentException> { PersonalRules().withFix(Fix("", "x")) }
        assertFailsWith<IllegalArgumentException> { PersonalRules().withFix(Fix("same", "same")) }
        // A case-only fix is a real change.
        assertEquals("Xylos", PersonalRules().withFix(Fix("xylos", "Xylos")).fixFor("xylos")?.meant)
    }

    @Test
    fun `blocking a pair removes the contradicting fix, and fixing it removes the block`() {
        val blocked = PersonalRules().withFix(Fix("its", "it's")).withNeverChange(NeverChange("its", "it's"))
        assertNull(blocked.fixFor("its"))
        assertTrue(blocked.isBlocked("ITS", "IT'S"))

        val fixedAgain = blocked.withFix(Fix("its", "it's"))
        assertFalse(fixedAgain.isBlocked("its", "it's"))
    }

    @Test
    fun `the second undo of the same correction asks once`() {
        var rules = PersonalRules()
        val (first, askFirst) = rules.recordRevert("Kasmyra", "Kashmir")
        assertFalse(askFirst)
        rules = first
        val (second, askSecond) = rules.recordRevert("kasmyra", "kashmir")
        assertTrue(askSecond)
        rules = second.markAsked("Kasmyra", "Kashmir")
        val (_, askThird) = rules.recordRevert("Kasmyra", "Kashmir")
        assertFalse(askThird, "asked already")
    }

    @Test
    fun `undoing a correction that is already blocked never asks`() {
        val rules = PersonalRules().withNeverChange(NeverChange("its", "it's"))
        val (after, _) = rules.recordRevert("its", "it's")
        assertFalse(after.recordRevert("its", "it's").second)
    }

    @Test
    fun `a case-only undo is not a correction worth asking about`() {
        val (rules, ask) = PersonalRules().recordRevert("rory", "Rory")
        assertFalse(ask)
        assertTrue(rules.revertCounts.isEmpty())
    }

    @Test
    fun `removing entries works by word`() {
        val rules = PersonalRules()
            .withFix(Fix("teh", "the"))
            .withNeverChange(NeverChange("its", "it's"))
            .withoutFix("TEH")
            .withoutNeverChange("its", "it's")
        assertTrue(rules.fixes.isEmpty())
        assertTrue(rules.neverChange.isEmpty())
    }
}
