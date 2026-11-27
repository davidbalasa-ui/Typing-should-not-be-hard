package com.typebetter.keyboard.settings

import com.typebetter.personal.Fix
import com.typebetter.personal.FixMode
import com.typebetter.personal.NeverChange
import com.typebetter.personal.PersonalRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The merged My words list: each word carries the typos it fixes (David, 2026-11-26). */
class MyWordsListTest {
    private val rules = PersonalRules(
        fixes = listOf(
            Fix("wiht", "with", addedAt = 20L),
            Fix("wotj", "with", addedAt = 10L),
            Fix("xylose", "Xylos"),
            Fix("silos", "Xylos", mode = FixMode.SUGGEST),
            Fix("twin haven", "TwinHaven", mode = FixMode.SUGGEST),
        ),
        neverChange = listOf(NeverChange("Niah", "Nah")),
    )

    @Test
    fun `fixes are grouped under the word they fix, typos oldest first`() {
        val entries = buildWordEntries(listOf("xylos", "Claude"), rules)
        assertEquals(listOf("Claude", "Niah", "TwinHaven", "with", "xylos"), entries.map { it.word })
        val with = entries.single { it.word == "with" }
        assertEquals(listOf("wotj", "wiht"), with.fixes.map { it.typed })
        assertFalse(with.isMyWord)
        // The dictionary spelling wins and the fixes still join it, whatever their case.
        val xylos = entries.single { it.word == "xylos" }
        assertTrue(xylos.isMyWord)
        assertEquals(listOf("xylose", "silos"), xylos.fixes.map { it.typed })
    }

    @Test
    fun `tags follow the strongest rule`() {
        val entries = buildWordEntries(listOf("Claude"), rules).associateBy { it.word }
        assertEquals(WordTag.AUTO_FIX, entries.getValue("with").tag)
        assertEquals(WordTag.AUTO_FIX, entries.getValue("Xylos").tag)
        assertEquals(WordTag.SUGGEST_ONLY, entries.getValue("TwinHaven").tag)
        assertEquals(WordTag.NEVER_CHANGE, entries.getValue("Niah").tag)
        assertEquals(WordTag.NONE, entries.getValue("Claude").tag)
    }

    @Test
    fun `filters and search`() {
        val entries = buildWordEntries(listOf("Claude"), rules)
        assertEquals(listOf("TwinHaven", "with", "Xylos"), entries.filter { it.matches(WordFilter.FIXES) }.map { it.word })
        assertEquals(listOf("TwinHaven", "Xylos"), entries.filter { it.matches(WordFilter.SUGGEST) }.map { it.word })
        assertEquals(listOf("Niah"), entries.filter { it.matches(WordFilter.NEVER_CHANGE) }.map { it.word })
        assertEquals(5, entries.count { it.matches(WordFilter.ALL) })
        // A typo finds its word.
        assertEquals(listOf("with"), entries.filter { it.matches("WOTJ") }.map { it.word })
        assertEquals(5, entries.count { it.matches("  ") })
    }

    @Test
    fun `quick add with typos saves fixes only`() {
        val plan = planQuickAdd(" with ", "wotj, wiht  with", FixMode.AUTO, now = 5L, isValidWord = { true })!!
        assertNull(plan.word)
        assertEquals(listOf("wotj", "wiht"), plan.fixes.map { it.typed })
        assertTrue(plan.fixes.all { it.meant == "with" && it.mode == FixMode.AUTO && it.addedAt == 5L })
        assertEquals(listOf("with"), plan.rejected)
    }

    @Test
    fun `quick add without typos adds the word`() {
        assertEquals("Niah", planQuickAdd("Niah", "", FixMode.AUTO, 0L, isValidWord = { true })!!.word)
        assertNull(planQuickAdd("two words", "", FixMode.AUTO, 0L, isValidWord = { !it.contains(' ') }))
        assertNull(planQuickAdd("   ", "teh", FixMode.AUTO, 0L, isValidWord = { true }))
    }

    @Test
    fun `suggest-only mode is kept`() {
        val plan = planQuickAdd("Xylos", "silos", FixMode.SUGGEST, 0L, isValidWord = { true })!!
        assertEquals(FixMode.SUGGEST, plan.fixes.single().mode)
    }

    @Test
    fun `home example is the newest automatic one-word fix`() {
        assertEquals("wiht", exampleFix(rules.fixes)?.typed)
        assertNull(exampleFix(listOf(Fix("silos", "Xylos", mode = FixMode.SUGGEST), Fix("fey wild", "Feywild"))))
    }

    @Test
    fun `home peek shows words with fixes first, newest fix first`() {
        val entries = buildWordEntries(listOf("Claude"), rules)
        assertEquals(listOf("with", "TwinHaven", "Xylos"), peekEntries(entries).map { it.word })
    }

    @Test
    fun `quick add never replaces a typo that fixes another word`() {
        val existing = mapOf("teh" to Fix("teh", "the"), "wotj" to Fix("wotj", "with"))
        val plan = planQuickAdd("ten", "teh, tne", FixMode.AUTO, 0L, { true }) { existing[it] }!!
        assertEquals(listOf("tne"), plan.fixes.map { it.typed })
        assertEquals(listOf("teh"), plan.taken.map { it.typed })
        // The same typo for the same word is simply saved again (mode may change).
        val again = planQuickAdd("with", "wotj", FixMode.SUGGEST, 0L, { true }) { existing[it] }!!
        assertEquals(listOf("wotj"), again.fixes.map { it.typed })
        assertTrue(again.taken.isEmpty())
    }
}
