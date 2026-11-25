/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.key

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which keys sit next to the space bar (TypeBetter: emoji key always visible, 2026-11-25).
 */
class UtilityKeyActionTest {

    private fun emoji(action: UtilityKeyAction, multi: Boolean, enabled: Boolean = true) =
        UtilityKeyAction.showsEmojiKey(enabled, action, multi)

    private fun globe(action: UtilityKeyAction, multi: Boolean, enabled: Boolean = true) =
        UtilityKeyAction.showsLanguageKey(enabled, action, multi)

    @Test
    fun `the TypeBetter default keeps the emoji key with one language`() {
        assertTrue(emoji(UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE, multi = false))
        assertFalse(globe(UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE, multi = false))
    }

    @Test
    fun `the TypeBetter default keeps the emoji key and adds the globe with two languages`() {
        // The bug David hit: the old dynamic default dropped the emoji key as soon as a second
        // language was added.
        assertTrue(emoji(UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE, multi = true))
        assertTrue(globe(UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE, multi = true))
    }

    @Test
    fun `the old dynamic mode still swaps one key for the other`() {
        assertTrue(emoji(UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS, multi = false))
        assertFalse(globe(UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS, multi = false))
        assertFalse(emoji(UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS, multi = true))
        assertTrue(globe(UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS, multi = true))
    }

    @Test
    fun `the single-purpose modes are unchanged`() {
        for (multi in listOf(false, true)) {
            assertTrue(emoji(UtilityKeyAction.SWITCH_TO_EMOJIS, multi))
            assertFalse(globe(UtilityKeyAction.SWITCH_TO_EMOJIS, multi))
            assertFalse(emoji(UtilityKeyAction.SWITCH_LANGUAGE, multi))
            assertTrue(globe(UtilityKeyAction.SWITCH_LANGUAGE, multi))
            assertFalse(emoji(UtilityKeyAction.SWITCH_KEYBOARD_APP, multi))
            assertTrue(globe(UtilityKeyAction.SWITCH_KEYBOARD_APP, multi))
            assertFalse(emoji(UtilityKeyAction.DISABLED, multi))
            assertFalse(globe(UtilityKeyAction.DISABLED, multi))
        }
    }

    @Test
    fun `turning the utility key off hides both keys in every mode`() {
        for (action in UtilityKeyAction.entries) {
            for (multi in listOf(false, true)) {
                assertFalse(emoji(action, multi, enabled = false), "$action multi=$multi")
                assertFalse(globe(action, multi, enabled = false), "$action multi=$multi")
            }
        }
    }

    @Test
    fun `the default bottom row declares both the emoji and the globe key`() {
        // Visibility only hides keys the layout declares; if the row lost either key the new default
        // would have nothing to show.
        val row = File(
            "src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/default.json",
        ).readText()
        assertEquals(1, Regex("\"code\":\\s*${KeyCode.IME_UI_MODE_MEDIA}\\b").findAll(row).count())
        assertEquals(1, Regex("\"code\":\\s*${KeyCode.LANGUAGE_SWITCH}\\b").findAll(row).count())
    }
}
