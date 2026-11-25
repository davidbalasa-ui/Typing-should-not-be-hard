package com.typebetter.keyboard.keys

import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TopBarEditorTest : FunSpec({
    val mic = QuickAction.InsertKey(TextKeyData.IME_UI_MODE_DICTATE)
    val wand = QuickAction.InsertKey(TextKeyData.DICTATE_PROMPTS)
    val history = QuickAction.InsertKey(TextKeyData.DICTATE_REINSERT)
    val clip = QuickAction.InsertKey(TextKeyData.IME_UI_MODE_CLIPBOARD)
    val translate = QuickAction.InsertKey(TextKeyData.TRANSLATE)
    val gif = QuickAction.InsertKey(TextKeyData.IME_UI_MODE_GIF)
    val undo = QuickAction.InsertKey(TextKeyData.UNDO)
    val settings = QuickAction.InsertKey(TextKeyData.SETTINGS)

    // David's bar: mic always shown, then wand, history, clipboard (3 on the bar), then translate and
    // GIF in the ⋯ menu, undo hidden.
    val start = QuickActionArrangement(mic, listOf(wand, history, clip, translate, gif), listOf(undo))
    val count = 3

    fun everything(a: QuickActionArrangement) = listOfNotNull(a.stickyAction) + a.dynamicActions + a.hiddenActions

    fun assertNothingLostOrDoubled(before: QuickActionArrangement, after: QuickActionArrangement) {
        everything(after).size shouldBe everything(after).toSet().size
        everything(after).toSet() shouldBe everything(before).toSet()
    }

    test("the bar is the first buttons, the ⋯ menu the rest") {
        TopBarEditor.onBar(start, count) shouldBe listOf(wand, history, clip)
        TopBarEditor.inMore(start, count) shouldBe listOf(translate, gif)
        TopBarEditor.placeOf(start, mic, count) shouldBe TopBarEditor.Place.Sticky
        TopBarEditor.placeOf(start, clip, count) shouldBe TopBarEditor.Place.Bar(2)
        TopBarEditor.placeOf(start, gif, count) shouldBe TopBarEditor.Place.More
        TopBarEditor.placeOf(start, undo, count) shouldBe TopBarEditor.Place.Hidden
    }

    test("a button another setting keeps off the bar is skipped, not counted") {
        val noWand: (QuickAction) -> Boolean = { it != wand }
        TopBarEditor.onBar(start, count, noWand) shouldBe listOf(history, clip, translate)
    }

    context("replacing a bar button") {
        test("with another bar button swaps them, and asks first") {
            TopBarEditor.replaceMovesAnotherVisibleButton(start, wand, clip, count) shouldBe true
            val after = TopBarEditor.replace(start, wand, clip)
            TopBarEditor.onBar(after, count) shouldBe listOf(clip, history, wand)
            assertNothingLostOrDoubled(start, after)
        }
        test("with one from the ⋯ menu: it takes the spot, the old one goes to the ⋯ menu") {
            TopBarEditor.replaceMovesAnotherVisibleButton(start, history, gif, count) shouldBe false
            val after = TopBarEditor.replace(start, history, gif)
            TopBarEditor.onBar(after, count) shouldBe listOf(wand, gif, clip)
            (history in TopBarEditor.inMore(after, count)) shouldBe true
            assertNothingLostOrDoubled(start, after)
        }
        test("with a hidden one: it takes the spot, the old one is not lost but in the ⋯ menu") {
            val after = TopBarEditor.replace(start, clip, undo)
            TopBarEditor.onBar(after, count) shouldBe listOf(wand, history, undo)
            (clip in TopBarEditor.inMore(after, count)) shouldBe true
            after.hiddenActions shouldBe emptyList()
            assertNothingLostOrDoubled(start, after)
        }
        test("with the always-shown one swaps them") {
            TopBarEditor.replaceMovesAnotherVisibleButton(start, wand, mic, count) shouldBe true
            val after = TopBarEditor.replace(start, wand, mic)
            after.stickyAction shouldBe wand
            TopBarEditor.onBar(after, count) shouldBe listOf(mic, history, clip)
            assertNothingLostOrDoubled(start, after)
        }
        test("the always-shown one with a bar button swaps them") {
            val after = TopBarEditor.replace(start, mic, history)
            after.stickyAction shouldBe history
            TopBarEditor.onBar(after, count) shouldBe listOf(wand, mic, clip)
        }
        test("the always-shown one with a hidden button") {
            val after = TopBarEditor.replace(start, mic, undo)
            after.stickyAction shouldBe undo
            (mic in after.dynamicActions) shouldBe true
            assertNothingLostOrDoubled(start, after)
        }
    }

    test("no always-shown button: the old one goes to the ⋯ menu") {
        val after = TopBarEditor.setSticky(start, null)
        after.stickyAction shouldBe null
        after.dynamicActions.last() shouldBe mic
        assertNothingLostOrDoubled(start, after)
        val back = TopBarEditor.setSticky(after, mic)
        back.stickyAction shouldBe mic
        assertNothingLostOrDoubled(start, back)
    }

    test("putting a button on the bar shows it once the count goes up by one") {
        val after = TopBarEditor.bringToBar(start, gif, count)
        TopBarEditor.onBar(after, count + 1) shouldBe listOf(wand, history, clip, gif)
        val fromHidden = TopBarEditor.bringToBar(start, undo, count)
        TopBarEditor.onBar(fromHidden, count + 1) shouldBe listOf(wand, history, clip, undo)
        fromHidden.hiddenActions shouldBe emptyList()
        assertNothingLostOrDoubled(start, fromHidden)
    }

    test("taking a button off the bar, with the count down by one, keeps the others in place") {
        val after = TopBarEditor.moveToMore(start, history, count)
        TopBarEditor.onBar(after, count - 1) shouldBe listOf(wand, clip)
        TopBarEditor.inMore(after, count - 1) shouldBe listOf(history, translate, gif)
        assertNothingLostOrDoubled(start, after)
    }

    test("hide and show again") {
        val hidden = TopBarEditor.hide(start, clip)
        TopBarEditor.placeOf(hidden, clip, count) shouldBe TopBarEditor.Place.Hidden
        TopBarEditor.onBar(hidden, count) shouldBe listOf(wand, history, translate)
        val shown = TopBarEditor.unhide(hidden, clip)
        TopBarEditor.placeOf(shown, clip, count) shouldBe TopBarEditor.Place.More
        assertNothingLostOrDoubled(start, shown)
        // hiding the always-shown one leaves none
        TopBarEditor.hide(start, mic).stickyAction shouldBe null
    }

    test("no edit ever loses or doubles a button") {
        val all = everything(start) + settings
        val a = start.copy(dynamicActions = start.dynamicActions + settings)
        for (current in listOf(mic, wand, history, clip)) for (replacement in all) {
            assertNothingLostOrDoubled(a, TopBarEditor.replace(a, current, replacement))
        }
        for (x in all) {
            assertNothingLostOrDoubled(a, TopBarEditor.bringToBar(a, x, count))
            assertNothingLostOrDoubled(a, TopBarEditor.moveToMore(a, x, count))
            assertNothingLostOrDoubled(a, TopBarEditor.hide(a, x))
            assertNothingLostOrDoubled(a, TopBarEditor.unhide(a, x))
            assertNothingLostOrDoubled(a, TopBarEditor.setSticky(a, x))
        }
    }
})
