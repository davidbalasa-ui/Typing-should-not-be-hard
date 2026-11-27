/*
 * Copyright (C) 2026 TypeBetter contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorrectionUndoTest {

    @Test
    fun `shift and the layer keys keep the undo`() {
        listOf(
            KeyCode.SHIFT, KeyCode.CAPS_LOCK, KeyCode.VIEW_CHARACTERS, KeyCode.VIEW_SYMBOLS,
            KeyCode.VIEW_SYMBOLS2, KeyCode.VIEW_NUMERIC, KeyCode.VIEW_NUMERIC_ADVANCED,
            KeyCode.VIEW_PHONE, KeyCode.VIEW_PHONE2, KeyCode.TOGGLE_NUMBER_ROW,
        ).forEach { assertTrue(CorrectionUndo.keepsUndo(it), "key $it") }
    }

    @Test
    fun `keys that edit or move the cursor let the undo go`() {
        listOf(
            'a'.code, '?'.code, KeyCode.SPACE, KeyCode.ENTER, KeyCode.DELETE_WORD, KeyCode.FORWARD_DELETE,
            KeyCode.ARROW_LEFT, KeyCode.CLIPBOARD_PASTE, KeyCode.UNDO,
        ).forEach { assertFalse(CorrectionUndo.keepsUndo(it), "key $it") }
    }

    @Test
    fun `the space typed after the correction is found`() {
        assertEquals(" ", CorrectionUndo.boundaryAfter("I walked ", "walked", 120))
        assertEquals("", CorrectionUndo.boundaryAfter("I walked", "walked", 120))
        assertEquals(",", CorrectionUndo.boundaryAfter("I walked,", "walked", 120))
    }

    @Test
    fun `nothing is put back once the text moved on`() {
        assertNull(CorrectionUndo.boundaryAfter("I walked t", "walked", 120))
        assertNull(CorrectionUndo.boundaryAfter("I walked  ", "walked", 120))
        assertNull(CorrectionUndo.boundaryAfter("", "walked", 120))
    }

    @Test
    fun `only the tail is compared when the cache is shorter than the correction`() {
        assertEquals(" ", CorrectionUndo.boundaryAfter("ababxed ", "walked", 2))
    }

    // ── Backspace right after a glide ──────────────────────────────────────────────────────────────

    @Test
    fun `a glided word with an added ending goes back to the shorter word the decoder offered`() {
        assertEquals("need", CorrectionUndo.glideUndoTarget("needed", listOf("seeded", "need", "heeded")))
        assertEquals("succeed", CorrectionUndo.glideUndoTarget("succeeded", listOf("succeed", "succeeds")))
        assertEquals("Need", CorrectionUndo.glideUndoTarget("Needed", listOf("Need")))
    }

    @Test
    fun `the longest shorter word wins`() {
        assertEquals("need", CorrectionUndo.glideUndoTarget("needed", listOf("nee", "need", "ne")))
    }

    @Test
    fun `no shorter choice means the backspace deletes as before`() {
        assertNull(CorrectionUndo.glideUndoTarget("needed", listOf("seeded", "heeded")))
        assertNull(CorrectionUndo.glideUndoTarget("needed", emptyList()))
        // Not the start of the glided word.
        assertNull(CorrectionUndo.glideUndoTarget("needed", listOf("seed")))
        // Same word, or a longer one, is never a target.
        assertNull(CorrectionUndo.glideUndoTarget("need", listOf("need", "needed")))
        assertNull(CorrectionUndo.glideUndoTarget("needed", listOf("", " ")))
    }

    @Test
    fun `at most three letters come off`() {
        assertEquals("walk", CorrectionUndo.glideUndoTarget("walking", listOf("walk")))
        assertNull(CorrectionUndo.glideUndoTarget("therefore", listOf("the", "there")))
        assertEquals("there", CorrectionUndo.glideUndoTarget("therein", listOf("the", "there")))
    }
}
