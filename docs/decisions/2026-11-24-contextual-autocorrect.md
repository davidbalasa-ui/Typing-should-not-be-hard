# Autocorrect: rare real words in context, and sound-alike spellings

**Decision.** English autocorrect gets two new rules (`ContextualFixes.kt`), both on by default and
following the existing Autocorrect strength setting:

1. **Rare real word in context.** A word that is in the dictionary but rare (frequency under 170) is
   offered a fix when one neighbouring key gives a much more common word (+40) that the previous word
   is often followed by (30+ times in the bigram table), and the typed word was never seen after that
   previous word. At Balanced it is swapped in on Space only with stronger evidence (typed word under
   150, gap 50, context 50). Cautious only offers. Never applied to a word in My words, a learned word,
   or a word of another keyboard language. Example: "responsible dor" → "for".
2. **Sound-alike spelling.** An unknown word of 5+ letters is matched on its consonant skeleton (vowels
   dropped, doubled letters collapsed, same first letter, within 3 edits). Up to two matches are
   offered. One is swapped in on Space only when nothing else would be swapped in, there is no
   dictionary word one edit away, and it is the only common match (frequency 170+, at most one letter
   longer or shorter, at most two letters different). Example: "siezers" → "seizures".

**How it was chosen.** David's phone left "dor siezers" alone (2026-11-24). The cause: "dor" is in
`en.json` (the subtitle corpus holds many rare strings like dor, thar, yiu), and the keyboard never
corrected a known word. "siezers" is three edits from "seizures", past the corrector's two-edit limit.
Thresholds were tuned on the shipped files and are asserted in `ContextualFixesTest`:
- rule 1 at Balanced swaps 0.46 % of rare words typed correctly after 20 common words (an upper bound)
  and fixes about 50 % of key slips that land on a rare word;
- rule 2 swaps 0.4 % of German words typed into the English keyboard that nothing else would change
  (the same harm measure `AutocorrectEvalTest` uses, budget under 1 % at Balanced).

**What changed.** `ContextualFixes.kt` (rules), `LatinLanguageProvider.suggest()` (wiring),
`ContextualFixesTest.kt` (measurements). English only, because only English ships a bigram table and
the vowel set is English.
