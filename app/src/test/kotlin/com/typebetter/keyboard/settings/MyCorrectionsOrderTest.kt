package com.typebetter.keyboard.settings

import com.typebetter.personal.Fix
import kotlin.test.Test
import kotlin.test.assertEquals

/** The My corrections list puts the correction just added at the top, where the eye already is. */
class MyCorrectionsOrderTest {
    @Test
    fun `newest correction first, undated ones last in A to Z order`() {
        val fixes = listOf(
            Fix("teh", "the", addedAt = 0L),
            Fix("dor", "for", addedAt = 300L),
            Fix("adn", "and", addedAt = 0L),
            Fix("siezers", "seizures", addedAt = 100L),
        )
        assertEquals(listOf("dor", "siezers", "adn", "teh"), newestFirst(fixes).map { it.typed })
    }
}
