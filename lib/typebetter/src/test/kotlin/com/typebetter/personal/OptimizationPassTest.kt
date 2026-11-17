package com.typebetter.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pre-test optimization pass (2026-11-17). */
class OptimizationPassTest {
    @Test
    fun `the dialog check and withFix agree on spacing`() {
        // Two spaces typed, one meant: same text once tidied. The dialog must not offer Save,
        // because withFix refuses it (this used to crash the settings screen on Save).
        assertFalse(isUsableFix("fey  wild", "fey wild"))
        assertFailsWith<IllegalArgumentException> { PersonalRules().withFix(Fix("fey  wild", "fey wild")) }
        assertFalse(isUsableFix(" pr ", "pr"))
        assertFalse(isUsableFix("", "x"))
        assertFalse(isUsableFix("x", "  "))
    }

    @Test
    fun `every fix the dialog accepts is saved`() {
        val cases = listOf("pr" to "PR", "teh" to "the", "fey  wild" to "Feywild", " recieve " to "receive")
        for ((typed, meant) in cases) {
            assertTrue(isUsableFix(typed, meant), "$typed -> $meant")
            val saved = PersonalRules().withFix(Fix(typed, meant)).fixes.single()
            assertEquals(meant.trim(), saved.meant)
        }
    }
}
