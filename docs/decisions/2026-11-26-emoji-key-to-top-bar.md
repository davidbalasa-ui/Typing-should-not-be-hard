# Emoji key moves to the top bar, space bar gets its room

**Asked.** David, 2026-11-26: "I want to remove and organize buttons. I want to pull out emoji button and
make space bar bigger."

**Decision.**

- New utility-key mode `LANGUAGE_WHEN_NEEDED` ("None (emojis are in the top bar), plus switch language when
  you have more than one") is the default. The bottom row is now `?123 , space . ↵` with one language and
  `?123 , 🌐 space . ↵` with two. The space bar is the only bottom-row key that stretches (`flayGrow`), so it
  takes the emoji key's whole width with no layout change.
- Emojis are not removed, they move: the emoji button is now second in the top bar's default order (right
  after the dictation history, ahead of clipboard), so it is on the bar on every phone width rather than in
  the ⋯ grid. Long-press Enter still opens emojis. Keys & buttons can still add an emoji key to the bottom
  row (Add key → Emoji).
- One-time move for existing installs (`EmojiKeyMove.runIfNeeded`, guard `keyboard__emoji_key_moved_to_top_bar`):
  the old default `EMOJIS_AND_SWITCH_LANGUAGE` becomes the new one; a custom Keys & buttons row loses its
  emoji key; and if the device had an emoji key, the top bar's emoji button is moved (or brought back from
  hidden) to second place on the bar. Other utility modes, including "Switch to emojis", were chosen by
  hand and are left alone.
- "Organize buttons": Keys & buttons already removes, adds and swaps bottom-row keys and top-bar buttons,
  and removing a key widens the space bar the same way. Nothing else on the row was reordered without a
  specific ask.

Supersedes the default in 2026-11-25-emoji-key-always-visible.md (that mode is still selectable).

**Tested.** `EmojiKeyMoveTest` (each migration rule, fresh install is a no-op), `UtilityKeyActionTest`
(new mode with one and two languages), `BottomRowTest` (stock row for the new default).
