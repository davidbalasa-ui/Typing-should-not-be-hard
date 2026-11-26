# Emoji key always next to the space bar

> **Superseded 2026-11-26** by 2026-11-26-emoji-key-to-top-bar.md: David asked for the emoji key off the bottom row; emojis are a top-bar button now. The mode below is still selectable.

**Problem.** David asked "No emojis?" on 2026-11-25. The emoji panel existed, but the key for it sat in
the one slot next to the space bar that upstream FlorisBoard shares with the language (globe) key. Its
default, "Dynamic", shows the emoji key with one language and swaps it for the globe as soon as a second
language is added. From then on emojis were only reachable by long-pressing Enter or through the ⋯ grid
in the Smartbar, neither of which anyone finds by looking.

**Decision.** A new utility-key mode, `EMOJIS_AND_SWITCH_LANGUAGE` ("Emojis, plus switch language when you
have more than one"), is the default: the emoji key is always shown, and the globe joins it only when
there is more than one language. The bottom row already declared both keys, so with two languages it
reads `?123 , 🌐 😊 space . ↵` and the space bar is one key narrower.

- The change is additive: the old modes are untouched and still selectable in Settings → Keyboard →
  Utility key action. A device where someone picked a mode by hand keeps it; every device still on the
  default (David's included) gets the new one.
- Long-press Enter and the ⋯ grid still open emojis too.

**Tested.** `UtilityKeyActionTest` covers every mode with one and with two languages, the off switch,
and that the default bottom row declares both keys.
