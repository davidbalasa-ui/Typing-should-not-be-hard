# My corrections screen, and English typos are never "protected" as German

**Asked by David, 2026-11-25:** "there's a setting that I can type like common corrections. If I type in
DOR, it autocorrects to FOR. And that can be based off of what I put in", "I don't know why we're looking
at German words in general", and settings should be simple.

**Decision.**

1. **My corrections** is the first row of Settings ("Type dor, get for."). The screen has two boxes,
   *When I type* and *Change it to*, and an Add button at the top; the list below is newest first, a tap
   edits (including the existing per-fix "Fix it automatically" switch and Delete), and a bin icon deletes
   after a confirm. A new correction is always made by itself on Space. The keyboard's test box at the
   bottom of the screen lets the correction be tried at once. It is also in Settings search (Popular).
   The data is the existing My fixes list (`personal-rules.json`), so nothing moves and backup, export
   and dictation already cover it. *My words & fixes* stays as it was, one tap away from the new screen.
2. **A correction that cannot work says so.** If *Display suggestions* or *Auto-correct* is off, the
   screen shows why the corrections are not being made, with a Turn on button.
3. **The keyboard already made user fixes on Space for words the dictionary knows** (`dor` is in
   `en.json`): `TypingRules.apply` puts an AUTO fix first and auto-commits it, whatever the dictionary
   says. Now pinned by `TypingRulesTest` ("my own fix for a word the dictionary knows is still made on
   Space"). The gap was finding the setting, not the engine.
4. **`wich` is removed from `en.json`**: it is now treated as a typo and offered with its fixes (*with*, *which*) instead of being accepted as a word. It is not swapped in by itself, because it is a prefix of *Wichita* and one edit from both *with* and *which*, so the keyboard cannot know which was meant; a `wich → which` entry in My corrections makes it automatic. German was never used for English
   typing; it only appeared in the tests, where German words stand in for "correctly typed words the
   dictionary does not know". Those stand-ins now leave out the known English typos
   (`EvalKeyboard.ENGLISH_TYPOS`, one list shared with `ContextualFixesTest`): for an English keyboard,
   correcting `wich` is the goal, not harm. Every budget is unchanged; the populations are the same
   words as before `wich` was removed, so the sampled numbers do not move.

5. **The correction boxes are typed verbatim.** The *When I type* / *Change it to* boxes (and the same
   boxes in My words & fixes) carry a `privateImeOptions` marker (`VerbatimField`); TypeBetter's keyboard
   then never swaps a word there on Space, so typing "dor " or "fey wild" into them is not "fixed" before
   it is saved. Suggestions still show. Android's auto-correct input flag could not be used: ordinary
   fields leave it unset too.

**Not changed.** German stays a keyboard language you can add, and its own dictionary is used only when
the German keyboard is active (`GlideDictionaryCatalog`, `LatinLanguageProvider`).
