package com.typebetter.keyboard.keys

import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement

/**
 * The edits the Keys & buttons screen makes to the top bar (the Smartbar's quick-action buttons),
 * docs/decisions/2026-11-25-keys-and-buttons-editor.md.
 *
 * The keyboard draws the bar from the stored [QuickActionArrangement] exactly as before: the first
 * buttons of `dynamicActions` sit on the bar, the rest wait in the ⋯ menu, `hiddenActions` appear
 * nowhere, and `stickyAction` (the mic) is always shown at the edge. This object only reorders those
 * lists, so nothing about how the bar runs changes and the existing in-keyboard drag editor keeps
 * working on the same data. [count] is how many buttons the user wants on the bar; [isOnBar] filters
 * out buttons another setting keeps off it (the magic wand while the prompts sit in their own row).
 */
object TopBarEditor {
    /** Hard ceiling of the "how many" stepper. More than this do not fit on any phone held upright. */
    const val MAX_COUNT = 8

    sealed interface Place {
        data object Sticky : Place
        data class Bar(val index: Int) : Place
        data object More : Place
        data object Hidden : Place
    }

    fun onBar(arrangement: QuickActionArrangement, count: Int, isOnBar: (QuickAction) -> Boolean = { true }): List<QuickAction> =
        arrangement.dynamicActions.filter(isOnBar).take(count.coerceAtLeast(0))

    fun inMore(arrangement: QuickActionArrangement, count: Int, isOnBar: (QuickAction) -> Boolean = { true }): List<QuickAction> =
        arrangement.dynamicActions.filter(isOnBar).drop(count.coerceAtLeast(0))

    fun placeOf(
        arrangement: QuickActionArrangement,
        action: QuickAction,
        count: Int,
        isOnBar: (QuickAction) -> Boolean = { true },
    ): Place? {
        if (arrangement.stickyAction == action) return Place.Sticky
        val bar = onBar(arrangement, count, isOnBar)
        bar.indexOf(action).takeIf { it >= 0 }?.let { return Place.Bar(it) }
        if (action in arrangement.dynamicActions) return Place.More
        if (action in arrangement.hiddenActions) return Place.Hidden
        return null
    }

    /**
     * Puts [replacement] where [current] is (a bar button or the sticky one). [current] goes to where
     * [replacement] was: two bar buttons, or the bar and the sticky button, trade places; a button from
     * the ⋯ menu or the hidden list takes the spot and [current] moves into the ⋯ menu, so it is never
     * lost from sight.
     */
    fun replace(arrangement: QuickActionArrangement, current: QuickAction, replacement: QuickAction): QuickActionArrangement {
        if (current == replacement) return arrangement
        val sticky = arrangement.stickyAction
        val dynamic = arrangement.dynamicActions.toMutableList()
        val hidden = arrangement.hiddenActions.toMutableList()
        return when {
            current == sticky -> {
                val i = dynamic.indexOf(replacement)
                if (i >= 0) {
                    dynamic[i] = current
                } else {
                    hidden.remove(replacement)
                    dynamic.add(current)
                }
                QuickActionArrangement(replacement, dynamic, hidden)
            }
            current in dynamic -> {
                val at = dynamic.indexOf(current)
                when {
                    replacement == sticky -> {
                        dynamic[at] = replacement
                        QuickActionArrangement(current, dynamic, hidden)
                    }
                    replacement in dynamic -> {
                        val other = dynamic.indexOf(replacement)
                        dynamic[at] = replacement
                        dynamic[other] = current
                        QuickActionArrangement(sticky, dynamic, hidden)
                    }
                    else -> {
                        hidden.remove(replacement)
                        dynamic[at] = replacement
                        dynamic.add(current)
                        QuickActionArrangement(sticky, dynamic, hidden)
                    }
                }
            }
            else -> arrangement
        }.distinct()
    }

    /** True when [replace] would also move a button the user can see on the bar (so the editor asks). */
    fun replaceMovesAnotherVisibleButton(
        arrangement: QuickActionArrangement,
        current: QuickAction,
        replacement: QuickAction,
        count: Int,
        isOnBar: (QuickAction) -> Boolean = { true },
    ): Boolean {
        if (current == replacement) return false
        val place = placeOf(arrangement, replacement, count, isOnBar)
        return place is Place.Bar || place == Place.Sticky
    }

    /** Sets the always-shown button at the edge, or none. The old one moves into the ⋯ menu. */
    fun setSticky(arrangement: QuickActionArrangement, action: QuickAction?): QuickActionArrangement {
        if (arrangement.stickyAction == action) return arrangement
        val old = arrangement.stickyAction
        val dynamic = (arrangement.dynamicActions - setOfNotNull(action)).toMutableList()
        if (old != null) dynamic.add(old)
        return QuickActionArrangement(action, dynamic, arrangement.hiddenActions - setOfNotNull(action)).distinct()
    }

    /**
     * Moves [action] (from the ⋯ menu or the hidden list) onto the bar, right after the buttons
     * already there, so raising the count by one shows it.
     */
    fun bringToBar(
        arrangement: QuickActionArrangement,
        action: QuickAction,
        count: Int,
        isOnBar: (QuickAction) -> Boolean = { true },
    ): QuickActionArrangement {
        val bar = onBar(arrangement, count, isOnBar)
        if (action in bar || action == arrangement.stickyAction) return arrangement
        val dynamic = (arrangement.dynamicActions - action).toMutableList()
        val insertAt = bar.lastOrNull()?.let { dynamic.indexOf(it) + 1 } ?: 0
        dynamic.add(insertAt, action)
        return QuickActionArrangement(arrangement.stickyAction, dynamic, arrangement.hiddenActions - action).distinct()
    }

    /**
     * Takes [action] off the bar into the ⋯ menu, first in line so it is the next to come back. The
     * caller lowers the count by one, or the next button would simply step up into the freed place.
     */
    fun moveToMore(
        arrangement: QuickActionArrangement,
        action: QuickAction,
        count: Int,
        isOnBar: (QuickAction) -> Boolean = { true },
    ): QuickActionArrangement {
        if (action == arrangement.stickyAction) return setSticky(arrangement, null)
        val barWithout = onBar(arrangement, count, isOnBar) - action
        val dynamic = (arrangement.dynamicActions - action).toMutableList()
        val insertAt = barWithout.lastOrNull()?.let { dynamic.indexOf(it) + 1 } ?: 0
        dynamic.add(insertAt, action)
        return QuickActionArrangement(arrangement.stickyAction, dynamic, arrangement.hiddenActions - action).distinct()
    }

    /** Hides [action] from the bar and the ⋯ menu. It stays listed under Hidden, one tap from coming back. */
    fun hide(arrangement: QuickActionArrangement, action: QuickAction): QuickActionArrangement {
        val sticky = arrangement.stickyAction.takeUnless { it == action }
        return QuickActionArrangement(
            sticky,
            arrangement.dynamicActions - action,
            (arrangement.hiddenActions - action) + action,
        ).distinct()
    }

    /** Puts a hidden [action] back, at the end of the ⋯ menu. */
    fun unhide(arrangement: QuickActionArrangement, action: QuickAction): QuickActionArrangement {
        if (action !in arrangement.hiddenActions) return arrangement
        return QuickActionArrangement(
            arrangement.stickyAction,
            arrangement.dynamicActions + action,
            arrangement.hiddenActions - action,
        ).distinct()
    }
}
