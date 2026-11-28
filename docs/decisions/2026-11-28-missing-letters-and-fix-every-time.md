# A missing letter is fixed even when the typo starts a rarer word; My corrections apply every time

David, 2026-11-28: "it's not remembering NAYA to NIAH all the time, and if I type continue and forget the U
it doesn't change it to continue. It doesn't even offer it as an option."

## 1. `contine` → `continue`

**Cause.** `contine` is not a word, but it is the start of `continent`, `continental` and `continents`. The
keyboard treated any typo that starts a dictionary word as possibly half-typed: those completions went
first, the fixes after them (`continue` landed fifth, off the three visible slots), and Space never swapped
(`!hadCandidatesBefore` in `LatinLanguageProvider.suggest()`). The tap reader cannot see a missing letter
either, because it reads one letter per tap.

**Decision** (`PrefixShadowGate`, English only):
- When the best fix is more common than every word the typo starts, it is shown right behind the typed
  word, ahead of those words. Otherwise it is still moved into the third visible slot at the latest.
- Space swaps it in when it is the typed word with one letter put back, an everyday word, clearly more
  common than those words, and the typed word has 5+ letters. Balanced: frequency 200+, 5 ahead.
  Aggressive: 185+, 5 ahead. Cautious: offered only.
- Never when the taps already decided, past a fix of the user's own, when one of the user's own or learned
  words starts the typo, or for a capitalised word after another word (most likely a name).
- A changed letter or a letter too many is not swapped this way: without the taps that is how names and
  foreign words differ from English ones (`frust` → `trust`, `wolfs` → `wolf` about 20 % of those foreign words swapped when allowed).

**Measured** (`PrefixShadowEvalTest`, shipped en.json; 2,616 everyday words with one letter left out where
what is left starts another word; 885 German words absent from en.json that start an English word):

```
strength     offered first   fixed on Space   wrong word   foreign word swapped
Cautious            33.5 %            0 %          0 %                  0 %
Balanced            33.5 %         14.0 %        1.5 %               0.68 %
Aggressive          33.5 %         18.5 %        1.7 %               1.58 %
before                 0 %            0 %          0 %                  0 %
```

The population is every eligible dictionary word equally; most of the ones not fixed are rare words
(under 200) or short ones. `wich` (four letters) now shows `which` ahead of `wichita`, without swapping.

## 2. My corrections every time

**Causes found** (file:line at main b70e9a2):
- Space or a punctuation mark pressed before the suggestions for the last letter arrived: the keyboard
  refused the strip as stale (`NlpManager.getAutoCommitCandidate`) and the word stood as typed, My
  correction included. Typing fast makes this common, which is the "not all the time".
- A glided word never passed through My corrections at all (`KeyboardManager.commitGesture`). `naya` is in
  en.json (135), so glide can write it.
- Dictation fixed only the exact spelling `Naya`; a model that writes `Nia` or `Nyah` was fixed only if Niah
  was also in My words.

**Decision.**
- `TypingRules.autoFixFor` gives the same answer the strip does (automatic fix, typed case, Never change,
  Auto-correct setting), without the strip. Space uses it when the strip is not this word's yet; a glide
  uses it always. Backspace takes a glided fix back like any correction.
- Dictation: the capitalised target of an automatic, single-word My correction counts as a name for
  `HeardNameFixer`, with all its usual checks (sound key, rare capitalised word, no ambiguity).
