/*
 * Copyright (C) 2020-2025 The FlorisBoard Contributors
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

/**
 * Enum for declaring the utility key actions.
 */
enum class UtilityKeyAction {
    SWITCH_TO_EMOJIS,
    SWITCH_LANGUAGE,
    SWITCH_KEYBOARD_APP,
    DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
    /**
     * The emoji key always, and the language key beside it when there is more than one language
     * (TypeBetter default, see docs/decisions/2026-11-25-emoji-key-always-visible.md). The dynamic
     * mode above swaps the emoji key out for the globe as soon as a second language is added, which
     * left people with no visible way to reach emojis at all.
     */
    EMOJIS_AND_SWITCH_LANGUAGE,
    /**
     * No emoji key: the space bar takes its place, and emojis live in the top bar (TypeBetter default
     * since 2026-11-26, docs/decisions/2026-11-26-emoji-key-to-top-bar.md). The language key still
     * appears when there is more than one language, since there is no other key for it on the row.
     */
    LANGUAGE_WHEN_NEEDED,
    DISABLED;

    companion object {
        /** Whether the emoji key next to the space bar is shown. */
        fun showsEmojiKey(enabled: Boolean, action: UtilityKeyAction, multipleLanguages: Boolean): Boolean {
            if (!enabled) return false
            return when (action) {
                DISABLED, SWITCH_LANGUAGE, SWITCH_KEYBOARD_APP, LANGUAGE_WHEN_NEEDED -> false
                SWITCH_TO_EMOJIS, EMOJIS_AND_SWITCH_LANGUAGE -> true
                DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> !multipleLanguages
            }
        }

        /** Whether the language (globe) key next to the space bar is shown. */
        fun showsLanguageKey(enabled: Boolean, action: UtilityKeyAction, multipleLanguages: Boolean): Boolean {
            if (!enabled) return false
            return when (action) {
                DISABLED, SWITCH_TO_EMOJIS -> false
                SWITCH_LANGUAGE, SWITCH_KEYBOARD_APP -> true
                DYNAMIC_SWITCH_LANGUAGE_EMOJIS, EMOJIS_AND_SWITCH_LANGUAGE, LANGUAGE_WHEN_NEEDED -> multipleLanguages
            }
        }
    }
}
