# Keys & buttons: a picture of the keyboard to remap keys and top buttons

Date: 2026-11-25. Asked by David: "as easy as an old PlayStation controller" screen; tap a key, pick
what it does from a list, warn when it takes the job from another key, add or remove keys; the same for
the top quick buttons (magic wand, history, translate, clipboard, GIFs), which he could not find how to
change. Also: settings are overly complicated.

## What was there

- The top bar (Smartbar quick actions) already had an editor, but the only way in was the keyboard's ⋯
  menu, then "Reorder actions", then long-press-and-drag across three grids. Nothing in Settings led
  there. How many buttons showed was not a setting at all (as many as fit the width).
- The bottom row came from `charactersMod/default.json`; the only control was the utility-key setting
  (which of globe/emoji shows). No key could be given another job.

## Decisions

1. **One screen, "Keys & buttons"**, third on the settings home, drawing the top bar, faded letter rows
   and the bottom row. Every top button and bottom key is tappable. The text box under the screen shows
   the live keyboard, so every change can be tried at once.
2. **Bottom row model** (`com.typebetter.keyboard.keys.BottomRow`): `?123`, keys left of space, space,
   keys right of space, Enter. `?123`, space and Enter are fixed (the only way to symbols, to space and
   to send); tapping them says so instead of doing nothing. Up to 6 other keys, chosen from 21 actions
   (punctuation, emoji, mic, magic wand, clipboard, translate, undo, cursor, number pad, language,
   keyboard picker, hide, settings). The same action is never on two keys: picking one that sits on
   another key swaps the two after a confirmation that names both.
3. **Stored as a string pref** `keyboard__bottom_row_keys` (`"comma,emoji|period"`). Empty = the stock row,
   exactly as before, so nobody's keyboard changes until they edit it. Unknown ids are dropped on read,
   so a newer backup can't break an older app. "Reset keys" goes back to empty.
4. **Applied in `LayoutManager.mergeLayouts`**, after the symbol hints, only for the letters with the stock
   bottom row. Languages that bring their own bottom row (Arabic, Hebrew, kana...) keep theirs: their
   extra keys are part of typing that script. The fixed keys stay the very same key objects, so long
   presses and space-bar gestures are untouched. Keys the user placed are pinned visible
   (`TextKey.placedByUser`), because the utility-key rules would otherwise hide a globe placed on purpose.
   While a custom row is on, the utility-key on/off switch on the Keyboard screen is greyed and points
   here; the utility-key action stays, since it still decides what a globe does with two languages.
   The top-bar picker leaves out a button another setting keeps off the bar (the magic wand while the
   prompts have their own row), since it would vanish the moment it was picked.
5. **Language key with one language** is no longer greyed out: it moves to the next keyboard app instead
   of doing nothing. The stock globe is hidden with one language anyway, so this changes only a globe the
   user placed, the "Switch language" quick action, and the old "Switch language" utility mode, all of
   which used to be dead keys with one language.
6. **Top bar edits reuse the stored arrangement** (sticky / bar-then-⋯ / hidden) through
   `TopBarEditor`, so the keyboard draws it exactly as before and the in-keyboard drag editor still
   works on the same data. New int pref `smartbar__visible_actions_count` (0 = as many as fit, the old
   behaviour) caps the bar; a narrow screen can still show fewer, and the screen says how many fit once
   the keyboard has been opened. Replacing a button with one from the ⋯ menu or the hidden list moves
   the old one into the ⋯ menu (never silently hidden); replacing it with another visible one asks and
   swaps.
7. **Settings home**: My corrections (PR #14), Dictate, My words & fixes, Keys & buttons, Typing, Gestures, Languages, Theme on
   top; Keyboard, Smartbar, Clipboard, Media, Translation, Extensions and Other behind one "More
   settings" row. Nothing was removed, and settings search still finds everything. The Smartbar screen
   links to Keys & buttons. The disabled "auto expand/collapse" row (deprecated, always on, with an
   untranslated note) was removed from the Smartbar screen and from search.

## Not done here

- Shift and Delete, and the symbol pages' bottom rows, are not editable yet.
- The Dictate settings screen (about 40 settings) is not simplified in this change.
