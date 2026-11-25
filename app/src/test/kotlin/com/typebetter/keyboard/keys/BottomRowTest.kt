package com.typebetter.keyboard.keys

import com.typebetter.keyboard.keys.BottomKeyAction.AT
import com.typebetter.keyboard.keys.BottomKeyAction.CLIPBOARD
import com.typebetter.keyboard.keys.BottomKeyAction.COMMA
import com.typebetter.keyboard.keys.BottomKeyAction.EMOJI
import com.typebetter.keyboard.keys.BottomKeyAction.LANGUAGE
import com.typebetter.keyboard.keys.BottomKeyAction.PERIOD
import com.typebetter.keyboard.keys.BottomKeyAction.QUESTION
import com.typebetter.keyboard.keys.BottomKeyAction.SLASH
import com.typebetter.keyboard.keys.BottomKeyAction.UNDO
import com.typebetter.keyboard.keys.BottomKeyAction.VOICE
import dev.patrickgold.florisboard.ime.keyboard.VariationSelector
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BottomRowTest : FunSpec({
    val stockRow = BottomRow(listOf(COMMA, EMOJI), listOf(PERIOD))

    context("stored value") {
        test("empty means the stock row from the layout file") {
            BottomRow.parse("") shouldBe null
            BottomRow.parse("   ") shouldBe null
        }
        test("round trips") {
            val row = BottomRow(listOf(COMMA, LANGUAGE, EMOJI), listOf(PERIOD, VOICE))
            BottomRow.parse(row.serialize()) shouldBe row
        }
        test("an empty side survives") {
            val row = BottomRow(emptyList(), listOf(PERIOD))
            BottomRow.parse(row.serialize()) shouldBe row
            BottomRow.parse("|") shouldBe BottomRow(emptyList(), emptyList())
        }
        test("unknown ids from a newer version and duplicates are dropped, never a broken row") {
            BottomRow.parse("comma,teleport,comma|period,emoji,period") shouldBe
                BottomRow(listOf(COMMA), listOf(PERIOD, EMOJI))
        }
        test("never more keys than the row holds") {
            val ids = BottomKeyAction.entries.joinToString(",") { it.id }
            val row = BottomRow.parse("$ids|")!!
            row.size shouldBe BottomRow.MAX_KEYS
        }
        test("action ids are unique and found again") {
            BottomKeyAction.entries.map { it.id }.toSet().size shouldBe BottomKeyAction.entries.size
            BottomKeyAction.entries.forEach { BottomKeyAction.fromId(it.id) shouldBe it }
        }
    }

    context("stock row matches what the keyboard showed before") {
        test("default setting, one language: comma, emoji | period") {
            BottomRow.stock(true, UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE, multipleLanguages = false) shouldBe stockRow
        }
        test("default setting, two languages: the globe joins") {
            BottomRow.stock(true, UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE, multipleLanguages = true) shouldBe
                BottomRow(listOf(COMMA, LANGUAGE, EMOJI), listOf(PERIOD))
        }
        test("utility key off: comma | period") {
            BottomRow.stock(false, UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE, multipleLanguages = true) shouldBe
                BottomRow(listOf(COMMA), listOf(PERIOD))
        }
    }

    context("changing a key") {
        test("to an action not on the row just changes it") {
            val change = stockRow.assign(KeySlot(RowSide.LEFT, 1), VOICE)
            change.row shouldBe BottomRow(listOf(COMMA, VOICE), listOf(PERIOD))
            change.needsConfirmation shouldBe false
        }
        test("to an action on another key swaps the two, and asks first") {
            val change = stockRow.assign(KeySlot(RowSide.LEFT, 1), PERIOD)
            change.needsConfirmation shouldBe true
            change.swappedWith shouldBe KeySlot(RowSide.RIGHT, 0)
            change.displaced shouldBe EMOJI
            change.row shouldBe BottomRow(listOf(COMMA, PERIOD), listOf(EMOJI))
        }
        test("to the action it already has changes nothing") {
            val change = stockRow.assign(KeySlot(RowSide.LEFT, 0), COMMA)
            change.row shouldBe stockRow
            change.needsConfirmation shouldBe false
        }
        test("a slot that does not exist changes nothing") {
            stockRow.assign(KeySlot(RowSide.RIGHT, 5), VOICE).row shouldBe stockRow
        }
        test("no action ever ends up on two keys") {
            val row = BottomRow(listOf(COMMA, LANGUAGE, EMOJI), listOf(PERIOD, UNDO))
            for (side in RowSide.entries) for (i in 0 until 3) for (action in BottomKeyAction.entries) {
                val result = row.assign(KeySlot(side, i), action).row
                val all = result.left + result.right
                all.toSet().size shouldBe all.size
                result.size shouldBe row.size
            }
        }
    }

    context("adding and removing keys") {
        test("adds next to the space bar on either side") {
            stockRow.add(RowSide.LEFT, VOICE) shouldBe BottomRow(listOf(COMMA, EMOJI, VOICE), listOf(PERIOD))
            stockRow.add(RowSide.RIGHT, QUESTION) shouldBe BottomRow(listOf(COMMA, EMOJI), listOf(QUESTION, PERIOD))
        }
        test("adding an action already on the row moves it") {
            stockRow.add(RowSide.RIGHT, COMMA) shouldBe BottomRow(listOf(EMOJI), listOf(COMMA, PERIOD))
        }
        test("a full row takes no more keys") {
            val full = BottomRow(listOf(COMMA, EMOJI, LANGUAGE), listOf(PERIOD, UNDO, CLIPBOARD))
            full.canAdd shouldBe false
            full.add(RowSide.LEFT, VOICE) shouldBe full
            // but moving a key that is already there still works
            full.add(RowSide.LEFT, UNDO) shouldBe BottomRow(listOf(COMMA, EMOJI, LANGUAGE, UNDO), listOf(PERIOD, CLIPBOARD))
        }
        test("removes") {
            stockRow.remove(KeySlot(RowSide.LEFT, 1)) shouldBe BottomRow(listOf(COMMA), listOf(PERIOD))
            stockRow.remove(KeySlot(RowSide.RIGHT, 0)) shouldBe BottomRow(listOf(COMMA, EMOJI), emptyList())
            stockRow.remove(KeySlot(RowSide.RIGHT, 3)) shouldBe stockRow
        }
    }

    context("building the keyboard row") {
        // The stock bottom row of charactersMod/default.json, by key code (the comma selector has none).
        val stock = listOf(KeyCode.VIEW_SYMBOLS, KeyCode.UNSPECIFIED, KeyCode.LANGUAGE_SWITCH, KeyCode.IME_UI_MODE_MEDIA,
            KeyCode.SPACE, 46, KeyCode.ENTER).map { Key(it) }

        test("?123, space and Enter stay the very same keys, the user's keys go around space") {
            val row = BottomRow(listOf(VOICE), listOf(QUESTION, SLASH))
            val built = BottomRow.applyTo(stock, { it.code }, row) { Key(code(it), it) }!!
            built.map { it.code } shouldBe listOf(KeyCode.VIEW_SYMBOLS, KeyCode.IME_UI_MODE_DICTATE, KeyCode.SPACE, 63, 47, KeyCode.ENTER)
            (built[0] === stock[0]) shouldBe true
            (built[2] === stock[4]) shouldBe true
            (built.last() === stock.last()) shouldBe true
        }
        test("an empty row leaves only the fixed keys") {
            val built = BottomRow.applyTo(stock, { it.code }, BottomRow(emptyList(), emptyList())) { Key(code(it), it) }!!
            built.map { it.code } shouldBe listOf(KeyCode.VIEW_SYMBOLS, KeyCode.SPACE, KeyCode.ENTER)
        }
        test("a row this editor does not know is left alone") {
            val noSpace = stock.filter { it.code != KeyCode.SPACE }
            BottomRow.applyTo(noSpace, { it.code }, stockRow) { Key(code(it), it) } shouldBe null
        }
        test("every action is a real key") {
            BottomKeyAction.entries.forEach { code(it) shouldNotBe KeyCode.UNSPECIFIED }
        }
        test("the comma keeps @ for email fields and / for web addresses") {
            val comma = COMMA.data as VariationSelector
            (comma.email as TextKeyData).code shouldBe AT.let { (it.data as TextKeyData).code }
            (comma.uri as TextKeyData).code shouldBe 47
        }
    }
})

private data class Key(val code: Int, val action: BottomKeyAction? = null)

private fun code(action: BottomKeyAction): Int = when (val data = action.data) {
    is TextKeyData -> data.code
    is VariationSelector -> (data.default as TextKeyData).code
    else -> KeyCode.UNSPECIFIED
}
