package com.typebetter.keyboard.keys

import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The one-time move of the emoji key from the bottom row to the top bar (2026-11-26). */
class EmojiKeyMoveTest : FunSpec({
    val mic = QuickAction.InsertKey(TextKeyData.IME_UI_MODE_DICTATE)
    val wand = QuickAction.InsertKey(TextKeyData.DICTATE_PROMPTS)
    val history = QuickAction.InsertKey(TextKeyData.DICTATE_REINSERT)
    val emoji = QuickAction.InsertKey(TextKeyData.IME_UI_MODE_MEDIA)
    val clip = QuickAction.InsertKey(TextKeyData.IME_UI_MODE_CLIPBOARD)
    val gif = QuickAction.InsertKey(TextKeyData.IME_UI_MODE_GIF)
    val undo = QuickAction.InsertKey(TextKeyData.UNDO)
    val all = { _: QuickAction -> true }
    val noWand = { a: QuickAction -> a.keyData().code != KeyCode.DICTATE_PROMPTS }

    fun everything(a: QuickActionArrangement) = listOfNotNull(a.stickyAction) + a.dynamicActions + a.hiddenActions

    context("utility key") {
        test("the old always-emoji default becomes the new default") {
            EmojiKeyMove.utilityAction(UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE) shouldBe
                UtilityKeyAction.LANGUAGE_WHEN_NEEDED
        }
        test("every other mode was a choice and stays") {
            UtilityKeyAction.entries.filter { it != UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE }.forEach {
                EmojiKeyMove.utilityAction(it) shouldBe null
            }
        }
    }

    context("the user's own bottom row") {
        test("loses its emoji key, everything else stays in order") {
            EmojiKeyMove.bottomRow("comma,emoji,voice|period") shouldBe "comma,voice|period"
            EmojiKeyMove.bottomRow("comma|emoji,period") shouldBe "comma|period"
        }
        test("is left alone without an emoji key, or when it is the stock row") {
            EmojiKeyMove.bottomRow("comma,voice|period") shouldBe null
            EmojiKeyMove.bottomRow("") shouldBe null
        }
    }

    context("did the bottom row have an emoji key") {
        test("stock row on the old default (read back as the new default, never set by hand)") {
            EmojiKeyMove.hadEmojiKey("", true, UtilityKeyAction.LANGUAGE_WHEN_NEEDED) shouldBe true
            EmojiKeyMove.hadEmojiKey("", true, UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE) shouldBe true
        }
        test("not when the utility key is off or set to something else") {
            EmojiKeyMove.hadEmojiKey("", false, UtilityKeyAction.LANGUAGE_WHEN_NEEDED) shouldBe false
            EmojiKeyMove.hadEmojiKey("", true, UtilityKeyAction.SWITCH_LANGUAGE) shouldBe false
            EmojiKeyMove.hadEmojiKey("", true, UtilityKeyAction.SWITCH_TO_EMOJIS) shouldBe false
        }
        test("a custom row decides by itself, whatever the utility setting says") {
            EmojiKeyMove.hadEmojiKey("comma,emoji|period", false, UtilityKeyAction.DISABLED) shouldBe true
            EmojiKeyMove.hadEmojiKey("comma|period", true, UtilityKeyAction.LANGUAGE_WHEN_NEEDED) shouldBe false
        }
    }

    context("top bar") {
        test("emoji from the ⋯ menu goes right after the first button on the bar") {
            val start = QuickActionArrangement(mic, listOf(wand, history, clip, gif, emoji), listOf(undo))
            val moved = EmojiKeyMove.topBar(start, noWand)!!
            // The wand is kept off the bar by the prompt row, so the first *shown* button is history.
            moved.dynamicActions shouldBe listOf(wand, history, emoji, clip, gif)
            everything(moved).toSet() shouldBe everything(start).toSet()
        }
        test("a hidden emoji button comes back to the bar") {
            val start = QuickActionArrangement(mic, listOf(history, clip, gif), listOf(emoji, undo))
            val moved = EmojiKeyMove.topBar(start, all)!!
            moved.dynamicActions shouldBe listOf(history, emoji, clip, gif)
            moved.hiddenActions shouldBe listOf(undo)
        }
        test("missing altogether, it is added") {
            val start = QuickActionArrangement(mic, listOf(history, clip), emptyList())
            EmojiKeyMove.topBar(start, all)!!.dynamicActions shouldBe listOf(history, emoji, clip)
        }
        test("already first or second on the bar, or pinned, nothing moves") {
            EmojiKeyMove.topBar(QuickActionArrangement(mic, listOf(emoji, history), emptyList()), all) shouldBe null
            EmojiKeyMove.topBar(QuickActionArrangement(mic, listOf(wand, history, emoji), emptyList()), noWand) shouldBe null
            EmojiKeyMove.topBar(QuickActionArrangement(emoji, listOf(history, clip), emptyList()), all) shouldBe null
        }
        test("an empty bar gets emoji first") {
            EmojiKeyMove.topBar(QuickActionArrangement(mic, emptyList(), listOf(emoji)), all)!!.dynamicActions shouldBe
                listOf(emoji)
        }
        test("the new default arrangement already shows emoji second, so a fresh install changes nothing") {
            EmojiKeyMove.topBar(QuickActionArrangement.Default, noWand) shouldBe null
        }
    }
})
