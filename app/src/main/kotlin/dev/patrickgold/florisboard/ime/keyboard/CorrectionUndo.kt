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

/**
 * The rules behind "backspace takes back what the keyboard just changed" (issues #283, #295), kept
 * apart from [KeyboardManager] so they can be tested without a keyboard.
 */
object CorrectionUndo {

    /**
     * Keys that change neither the text nor the cursor, so they must not cost the user the undo.
     *
     * Shift and the layer keys were letting it go: `teh ` became `the `, a tap on Shift (or on `?123`
     * and back) to look for the right key, and the backspace after it only deleted a space. Nothing about
     * the text changed in between, and the editor is checked again before anything is put back (the text
     * still ends in the correction, the cursor has not moved since). Shift does change text when something
     * is selected (it cycles the capitals), so [KeyboardManager] lets the undo go in that case.
     */
    fun keepsUndo(code: Int): Boolean = when (code) {
        KeyCode.SHIFT,
        KeyCode.CAPS_LOCK,
        KeyCode.VIEW_CHARACTERS,
        KeyCode.VIEW_SYMBOLS,
        KeyCode.VIEW_SYMBOLS2,
        KeyCode.VIEW_NUMERIC,
        KeyCode.VIEW_NUMERIC_ADVANCED,
        KeyCode.VIEW_PHONE,
        KeyCode.VIEW_PHONE2,
        KeyCode.TOGGLE_NUMBER_ROW -> true
        else -> false
    }

    /**
     * What follows [inserted] at the end of [before]: "" when the text ends in it, the one boundary
     * character typed behind it, or null when the text no longer ends in the correction at all.
     *
     * Only the last [tailLength] characters of [inserted] are compared, because the cached text before
     * the cursor is capped.
     */
    fun boundaryAfter(before: CharSequence, inserted: String, tailLength: Int): String? {
        val tail = inserted.takeLast(tailLength)
        return when {
            before.endsWith(tail) -> ""
            before.length > inserted.length && before.dropLast(1).endsWith(tail) -> before.takeLast(1).toString()
            else -> null
        }
    }

    /** The endings a glide may add by mistake and a backspace may take off again (`needed` → `need`). */
    private val GLIDE_ENDINGS = setOf("ed", "d", "s", "es", "ing")

    /** Below this length the shorter word is too likely a different word (`your` → `you`, `then` → `the`). */
    const val GLIDE_MIN_TARGET = 3

    /**
     * The word a backspace right after a glide should leave instead of [committed], or null when the
     * backspace should simply delete a letter.
     *
     * Swiping `need` (or `seed`, `succeed`, `proceed`) is easily read as `needed`: the extra `e-d` is a
     * small wobble back and forth between two neighbouring keys, the same movement as the loop drawn for
     * a doubled letter. The decoder usually still lists the shorter word among its other choices, and
     * when it does, that is what the user most likely meant, so the first backspace takes the added end
     * off in one go. Only a word the decoder itself offered is used, never a guessed stem, only when the
     * committed word is that word plus a word ending ([isGlideEnding]: `-ed`, `-d`, `-s`, `-es`, `-ing`, or
     * a doubled last letter plus `-ed`), and only for words of at least [GLIDE_MIN_TARGET] letters — so
     * `your` with `you` offered still deletes a letter. The longest such word wins.
     */
    fun glideUndoTarget(committed: String, alternatives: List<String>): String? =
        alternatives
            .filter { alt ->
                alt.isNotBlank() && alt.length >= GLIDE_MIN_TARGET && alt.length < committed.length &&
                    committed.startsWith(alt) && isGlideEnding(alt, committed.substring(alt.length))
            }
            .maxByOrNull { it.length }

    /** Whether [ending] is a word ending for [stem]: `need`+`ed`, `stop`+`ped`, `walk`+`ing`. */
    fun isGlideEnding(stem: String, ending: String): Boolean {
        val lower = ending.lowercase()
        if (lower in GLIDE_ENDINGS) return true
        return lower.length == 3 && lower.endsWith("ed") && stem.isNotEmpty() &&
            lower[0] == stem.last().lowercaseChar()
    }
}
