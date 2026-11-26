# A settings page that feels like David's (2026-11-26)

Date: 2026-11-26. Asked by David: a settings page that is distinctly his. He was shown two mockups,
Claude's and ChatGPT's (made independently through the Agent Bridge, issue #22 / draft PR #24), liked how
clean ChatGPT's was, approved a combined version and said "Build it".

## What was there

The FlorisBoard settings home: a flat list of rows, the dictation time-saved card, "More settings" folded
(see 2026-11-25-keys-and-buttons-editor.md, item 7). My words & fixes was three separate lists (fixes,
never change, words). The Whisper Tiny/Base/Small choice existed only in the keyboard's setup sheet and
deep inside the provider dialog. The app was coloured from the Dictate light-blue accent.

## Decisions

1. **Colours from the icon.** While the settings accent (`other__accent_color`) is left at its stock value,
   the app uses `TypeBetterColors` (cream paper, navy, green, typo red; light and dark). Picking any other
   accent, or Material You, brings back the generated scheme, so nothing a user chose is overridden.
2. **The look.** Big serif titles, small monospace section labels, plain rows on hairlines
   (`TypeBetterUi.kt`). System fonts only (Serif / Monospace), nothing downloaded.
3. **Home** (`HomeScreen.kt`): "Hi David", a *Try your keyboard* box (a real text field, so the real keyboard
   with his fixes does the work; the hint uses his newest automatic fix), the dictation stats card, then
   *Words & voice* (My words with real counts and a peek at three entries; Dictation with the model in use and
   the transcribe-a-file button), *Keyboard* (switches for autocorrect, glide, next-word, GIF search; the row
   opens the full screen, the switch flips the setting; Keys & buttons), *Everything else* (Typing, Gestures,
   Languages, Theme, More settings folded as before, Help, About). The bottom preview field is off on home
   only, because the try box replaces it.
   - Autocorrect needs suggestions on: turning the switch on turns on both, so it never shows on while
     doing nothing.
   - My corrections moved from its own home row to the try box's *Add a fix* button. Nothing was removed:
     every old row is on the page or one tap away, and search finds everything as before.
   - "Hi David" is a plain string: the app is David's own.
4. **My words is one list** (`MyWordsList.kt`, pure and unit tested): one entry per word with the typos it
   fixes (`← wotj, wiht`, suggest-only ones marked) and the changes it is protected from (`↛ Nah`), tagged
   Auto-fix / Suggest only / Never change (symbol and text, not colour alone). Filters and search over words
   and typos. Data does not move: words stay in the personal dictionary, fixes and never-change rules in
   personal-rules.json. Every old action is kept: the same dialogs edit a word, a fix or a rule; *Add a word*,
   *Add a fix* (phrases too), *Never change*, *Learn from a chat*, starter pack, export/import/weekly backups.
5. **Quick add.** Word plus optional typos. With typos it saves a fix per typo (automatic or suggest-only);
   without, it adds the word to My words. A typo list never puts a common word like "for" in the dictionary.
6. **Dictation** (`VoiceModelSection.kt`, top of the Dictate screen): Whisper Tiny/Base/Small with real sizes,
   speed/accuracy bars and download state, the same download-then-switch recipe as the keyboard's setup
   sheet (`useModel`/`useDownloadedModel`, now internal, shared). Then *Names it listens for*: what the
   transcription prompt really carries (`customWordList`). All other dictation rows are unchanged below.
   Dictate Cloud stays hidden.
7. Mockup items not built: the weekly "time saved" card in the mockup is the existing real stats card; the
   mockup's sample transcript panel is not shown, since it would be fake data.
