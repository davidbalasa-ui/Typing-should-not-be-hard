package com.typebetter.keyboard.keys

import dev.patrickgold.florisboard.ime.keyboard.AbstractKeyData
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.keyboard.VariationSelector
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData

/**
 * What one of the user's bottom-row keys does (Keys & buttons editor,
 * docs/decisions/2026-11-25-keys-and-buttons-editor.md).
 *
 * [id] is what the preference stores, so it never changes once shipped. [data] is the key the keyboard
 * builds for it: every action is an ordinary key code the keyboard already handles, so a remapped key
 * behaves exactly like the same key anywhere else (the emoji key opens emojis, the mic key dictates).
 */
enum class BottomKeyAction(val id: String, val data: AbstractKeyData, val kind: Kind) {
    // The comma keeps its smart behaviour: "@" in an email field, "/" in a web address.
    COMMA("comma", VariationSelector(
        default = TextKeyData(code = 44, label = ",", groupId = KeyData.GROUP_LEFT),
        email = TextKeyData(code = 64, label = "@", groupId = KeyData.GROUP_LEFT),
        uri = TextKeyData(code = 47, label = "/", groupId = KeyData.GROUP_LEFT),
    ), Kind.CHARACTER),
    PERIOD("period", TextKeyData(code = 46, label = ".", groupId = KeyData.GROUP_RIGHT), Kind.CHARACTER),
    QUESTION("question", TextKeyData(code = 63, label = "?"), Kind.CHARACTER),
    EXCLAMATION("exclamation", TextKeyData(code = 33, label = "!"), Kind.CHARACTER),
    APOSTROPHE("apostrophe", TextKeyData(code = 39, label = "'"), Kind.CHARACTER),
    AT("at", TextKeyData(code = 64, label = "@"), Kind.CHARACTER),
    SLASH("slash", TextKeyData(code = 47, label = "/"), Kind.CHARACTER),
    HYPHEN("hyphen", TextKeyData(code = 45, label = "-"), Kind.CHARACTER),
    EMOJI("emoji", TextKeyData.IME_UI_MODE_MEDIA, Kind.TOOL),
    VOICE("voice", TextKeyData.IME_UI_MODE_DICTATE, Kind.TOOL),
    AI_PROMPTS("ai_prompts", TextKeyData.DICTATE_PROMPTS, Kind.TOOL),
    CLIPBOARD("clipboard", TextKeyData.IME_UI_MODE_CLIPBOARD, Kind.TOOL),
    TRANSLATE("translate", TextKeyData.TRANSLATE, Kind.TOOL),
    UNDO("undo", TextKeyData.UNDO, Kind.TOOL),
    CURSOR_LEFT("cursor_left", TextKeyData.ARROW_LEFT, Kind.TOOL),
    CURSOR_RIGHT("cursor_right", TextKeyData.ARROW_RIGHT, Kind.TOOL),
    NUMBER_PAD("number_pad", TextKeyData.VIEW_NUMERIC_ADVANCED, Kind.TOOL),
    LANGUAGE("language", TextKeyData.LANGUAGE_SWITCH, Kind.KEYBOARD),
    KEYBOARD_PICKER("keyboard_picker", TextKeyData.SYSTEM_INPUT_METHOD_PICKER, Kind.KEYBOARD),
    HIDE_KEYBOARD("hide_keyboard", TextKeyData.IME_HIDE_UI, Kind.KEYBOARD),
    SETTINGS("settings", TextKeyData.SETTINGS, Kind.KEYBOARD);

    /** The groups the picker lists the actions in. */
    enum class Kind { CHARACTER, TOOL, KEYBOARD }

    companion object {
        fun fromId(id: String): BottomKeyAction? = entries.firstOrNull { it.id == id }
    }
}

/** Which side of the space bar a key sits on. */
enum class RowSide { LEFT, RIGHT }

/** One editable key: its side of the space bar and its position counted outward from the left edge. */
data class KeySlot(val side: RowSide, val index: Int)

/**
 * The letters' bottom row as the user arranged it:
 * `?123`, then [left], then the space bar, then [right], then Enter.
 *
 * `?123`, space and Enter are fixed. They are the only way to reach the symbols, to type a space and to
 * send, so no arrangement can lose them, and every arrangement this class can hold is a working row.
 * The same action never appears twice: putting it on a second key is a swap (see [assign]).
 */
data class BottomRow(val left: List<BottomKeyAction>, val right: List<BottomKeyAction>) {

    val size: Int get() = left.size + right.size

    val canAdd: Boolean get() = size < MAX_KEYS

    operator fun get(slot: KeySlot): BottomKeyAction? = sideList(slot.side).getOrNull(slot.index)

    fun slotOf(action: BottomKeyAction): KeySlot? {
        left.indexOf(action).takeIf { it >= 0 }?.let { return KeySlot(RowSide.LEFT, it) }
        right.indexOf(action).takeIf { it >= 0 }?.let { return KeySlot(RowSide.RIGHT, it) }
        return null
    }

    /**
     * What putting [action] on [slot] does. When [action] is already on another key the two trade
     * places, and the editor asks first ([Change.needsConfirmation]): the other key changes too.
     */
    fun assign(slot: KeySlot, action: BottomKeyAction): Change {
        val current = this[slot] ?: return Change(this, null, null)
        if (current == action) return Change(this, null, null)
        val other = slotOf(action)
        var result = with(slot, action)
        if (other != null) result = result.with(other, current)
        return Change(result, swappedWith = other, displaced = if (other != null) current else null)
    }

    /** Adds a key with [action] at the space-bar end of [side]; an action already on the row moves there. */
    fun add(side: RowSide, action: BottomKeyAction): BottomRow {
        val without = BottomRow(left - action, right - action)
        if (!without.canAdd) return this
        return when (side) {
            RowSide.LEFT -> without.copy(left = without.left + action)
            RowSide.RIGHT -> without.copy(right = listOf(action) + without.right)
        }
    }

    fun remove(slot: KeySlot): BottomRow {
        val list = sideList(slot.side)
        if (slot.index !in list.indices) return this
        val updated = list.toMutableList().also { it.removeAt(slot.index) }
        return when (slot.side) {
            RowSide.LEFT -> copy(left = updated)
            RowSide.RIGHT -> copy(right = updated)
        }
    }

    fun serialize(): String = left.joinToString(",") { it.id } + "|" + right.joinToString(",") { it.id }

    private fun sideList(side: RowSide) = if (side == RowSide.LEFT) left else right

    private fun with(slot: KeySlot, action: BottomKeyAction): BottomRow {
        val updated = sideList(slot.side).toMutableList().also { it[slot.index] = action }
        return when (slot.side) {
            RowSide.LEFT -> copy(left = updated)
            RowSide.RIGHT -> copy(right = updated)
        }
    }

    /**
     * The row after an edit. [swappedWith] is the other key that changed when the action was already
     * on it, and [displaced] the action that key holds now.
     */
    data class Change(val row: BottomRow, val swappedWith: KeySlot?, val displaced: BottomKeyAction?) {
        val needsConfirmation: Boolean get() = swappedWith != null
    }

    companion object {
        /** Keys besides ?123, space and Enter. Eight would leave the space bar too narrow to hit. */
        const val MAX_KEYS = 6

        /**
         * The row from the preference, or null while the user has not arranged their own (the empty
         * string): the keyboard then builds the stock row from the layout file, as it always did.
         * Unknown ids (from a newer version) are skipped and duplicates kept once, so a stored value
         * can never produce a broken row.
         */
        fun parse(value: String): BottomRow? {
            if (value.isBlank()) return null
            val parts = value.split("|")
            val seen = mutableSetOf<BottomKeyAction>()
            fun side(text: String?) = text.orEmpty().split(",").mapNotNull { id ->
                BottomKeyAction.fromId(id.trim())?.takeIf { seen.add(it) }
            }
            val left = side(parts.getOrNull(0))
            val right = side(parts.getOrNull(1))
            val all = (left.map { RowSide.LEFT to it } + right.map { RowSide.RIGHT to it }).take(MAX_KEYS)
            return BottomRow(
                left = all.filter { it.first == RowSide.LEFT }.map { it.second },
                right = all.filter { it.first == RowSide.RIGHT }.map { it.second },
            )
        }

        /**
         * The row the stock layout shows today, so the editor opens on exactly what is on screen:
         * `,` then the globe and the emoji key as the utility-key setting shows them, then `.`.
         */
        fun stock(utilityEnabled: Boolean, utilityAction: UtilityKeyAction, multipleLanguages: Boolean): BottomRow {
            val left = buildList {
                add(BottomKeyAction.COMMA)
                if (UtilityKeyAction.showsLanguageKey(utilityEnabled, utilityAction, multipleLanguages)) {
                    add(BottomKeyAction.LANGUAGE)
                }
                if (UtilityKeyAction.showsEmojiKey(utilityEnabled, utilityAction, multipleLanguages)) {
                    add(BottomKeyAction.EMOJI)
                }
            }
            return BottomRow(left, listOf(BottomKeyAction.PERIOD))
        }

        /** The fixed keys a custom row keeps from the layout file. */
        val FIXED_CODES = setOf(KeyCode.VIEW_SYMBOLS, KeyCode.SPACE, KeyCode.ENTER)

        /**
         * Puts [row] into the stock bottom row [stockKeys] (any key type, read through [codeOf]): the
         * fixed keys stay the very same objects, so their long presses and gestures are untouched, and
         * the user's keys are made with [make]. Returns null when the stock row is not the one this
         * editor knows (no space bar, or a language whose own row has other keys), so it is left as is.
         */
        fun <K> applyTo(stockKeys: List<K>, codeOf: (K) -> Int, row: BottomRow, make: (BottomKeyAction) -> K): List<K>? {
            val symbols = stockKeys.firstOrNull { codeOf(it) == KeyCode.VIEW_SYMBOLS } ?: return null
            val space = stockKeys.firstOrNull { codeOf(it) == KeyCode.SPACE } ?: return null
            val enter = stockKeys.firstOrNull { codeOf(it) == KeyCode.ENTER } ?: return null
            return buildList {
                add(symbols)
                row.left.forEach { add(make(it)) }
                add(space)
                row.right.forEach { add(make(it)) }
                add(enter)
            }
        }
    }
}
