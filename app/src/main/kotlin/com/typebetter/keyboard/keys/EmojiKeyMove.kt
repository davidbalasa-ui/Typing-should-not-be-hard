package com.typebetter.keyboard.keys

import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.smartbar.quickaction.filterDictateHidden
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData

/**
 * Moves the emoji key off the bottom row and into the top bar, once, on devices that had it
 * (docs/decisions/2026-11-26-emoji-key-to-top-bar.md). The space bar grows into the freed room by
 * itself: it is the only bottom-row key that stretches.
 *
 * Nothing is lost: emojis stay one tap away as a top-bar button, long-press Enter still opens them, and
 * Keys & buttons can put an emoji key back on the bottom row.
 *
 * The decisions are plain functions so they are unit tested; [runIfNeeded] only reads and writes prefs.
 */
object EmojiKeyMove {

    /** The utility-key mode after the move: only the old "emoji key always" default changes. */
    fun utilityAction(current: UtilityKeyAction): UtilityKeyAction? =
        if (current == UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE) UtilityKeyAction.LANGUAGE_WHEN_NEEDED else null

    /** The user's own bottom row without its emoji key, or null when there is nothing to change. */
    fun bottomRow(stored: String): String? {
        val row = BottomRow.parse(stored) ?: return null
        val slot = row.slotOf(BottomKeyAction.EMOJI) ?: return null
        return row.remove(slot).serialize()
    }

    /**
     * Whether the bottom row showed an emoji key before the move. [utilityAction] is read *after* the
     * new default shipped: [UtilityKeyAction.LANGUAGE_WHEN_NEEDED] did not exist before, so reading it
     * here means the setting was never changed, which means the old default (emoji key shown) applied.
     */
    fun hadEmojiKey(storedRow: String, utilityEnabled: Boolean, utilityAction: UtilityKeyAction): Boolean {
        val row = BottomRow.parse(storedRow)
        if (row != null) return row.slotOf(BottomKeyAction.EMOJI) != null
        // "Switch to emojis" (emoji key only) is a choice someone made by hand; it keeps its key.
        return utilityEnabled && (
            utilityAction == UtilityKeyAction.EMOJIS_AND_SWITCH_LANGUAGE ||
                utilityAction == UtilityKeyAction.LANGUAGE_WHEN_NEEDED
            )
    }

    /**
     * The top bar with the emoji button second among the buttons on the bar (first when the bar is
     * empty), so it shows on every phone width; null when it is already the pinned button or one of the
     * first two. It comes out of the hidden list too: hiding it made sense while a key was next to space.
     */
    fun topBar(arrangement: QuickActionArrangement, isOnBar: (QuickAction) -> Boolean): QuickActionArrangement? {
        val isEmoji = { a: QuickAction -> a.keyData().code == KeyCode.IME_UI_MODE_MEDIA }
        if (arrangement.stickyAction?.let(isEmoji) == true) return null
        val shown = arrangement.dynamicActions.filter(isOnBar)
        if (shown.indexOfFirst(isEmoji) in 0..1) return null
        val emoji = arrangement.dynamicActions.firstOrNull(isEmoji)
            ?: arrangement.hiddenActions.firstOrNull(isEmoji)
            ?: QuickAction.InsertKey(TextKeyData.IME_UI_MODE_MEDIA)
        val dynamic = arrangement.dynamicActions.filterNot(isEmoji)
        val firstShown = dynamic.indexOfFirst(isOnBar)
        val at = if (firstShown < 0) 0 else firstShown + 1
        return arrangement.copy(
            dynamicActions = dynamic.toMutableList().apply { add(at, emoji) },
            hiddenActions = arrangement.hiddenActions.filterNot(isEmoji),
        )
    }

    suspend fun runIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.keyboard.emojiKeyMovedToTopBar.get()) return
        prefs.keyboard.emojiKeyMovedToTopBar.set(true)

        val storedRow = prefs.keyboard.bottomRowKeys.get()
        val action = prefs.keyboard.utilityKeyAction.get()
        val hadKey = hadEmojiKey(storedRow, prefs.keyboard.utilityKeyEnabled.get(), action)

        utilityAction(action)?.let { prefs.keyboard.utilityKeyAction.set(it) }
        bottomRow(storedRow)?.let { prefs.keyboard.bottomRowKeys.set(it) }
        if (hadKey) {
            val promptsLayout = prefs.dictate.promptsLayout.get()
            val isOnBar = { a: QuickAction -> listOf(a).filterDictateHidden(promptsLayout).isNotEmpty() }
            topBar(prefs.smartbar.actionArrangement.get(), isOnBar)?.let { prefs.smartbar.actionArrangement.set(it) }
        }
    }
}
