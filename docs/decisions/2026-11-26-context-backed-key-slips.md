# Autocorrect: two key slips, when the previous word says which word was meant

**Decision.** English autocorrect may swap in a correction that needs more finger travel than the
per-tap budget allows, when the previous word backs it (`AutoCommitGate.allowsInContext`). Example:
"words wotj" → "words with" (o for i, j for h: two neighbouring keys).

At Balanced (the default) all of these must hold:
- the correction is decoded from where the fingers landed, at most 0.70 key-width² of extra travel per
  tap (two whole-key slips in a four-letter word is 0.50);
- it is an everyday word (frequency 230+ on en.json's scale);
- the bigram table saw it after the previous word 20+ times, **and** that is at least 0.5 % of everything
  seen after the previous word (`words with` is 35 of about 1,700; `to such` is 149 of far more, so the
  German `dich` after `to` is left alone);
- its score (taps, frequency and context together) is at least 0.5 ahead of the runner-up.

Aggressive: 0.90 per tap, frequency 200+, 12+ times, 0.3 % share, 0.3 ahead. Cautious: off.

**Why.** David typed "wotj" for "with" (2026-11-26) and it stayed. The keyboard already reads every tap by
its position on the screen (the beam decoder tries the six nearest keys per tap and charges each by its
squared distance, so for h the touching keys y, u, g, j, b, n cost least). "with" was the top reading, but
two whole-key slips cost 2.0 over four taps, 0.50 per tap, over the Balanced budget of 0.30, and "work"
was almost as good on taps alone. The previous word settles it.

**Measured** (`ContextSlipEvalTest`, shipped files, English words typed after a word they really follow,
German words absent from en.json typed after each of the 20 commonest English words):

```
rule                   1-slip fixed   2-slip fixed   →wrong word   correct word changed
Balanced (before)            57.7 %         10.6 %        1.79 %                 1.76 %
Balanced (now)               86.3 %         35.9 %        1.85 %                 1.76 %
Aggressive (before)          88.8 %         40.7 %        4.87 %                 3.42 %
Aggressive (now)             97.2 %         70.7 %        4.90 %                 3.42 %
```

The one-slip gain is mostly short words: one whole-key slip in a three-letter word ("of rhe") is 0.33 per
tap, which the 0.30 budget never allowed.

**Not covered.** "see jack flu" → "fly": "flu" is a common real word (frequency 200), and the bigram table
has neither "jack fly" nor "jack flu", so nothing in the shipped data says "fly" was meant. Fixing that
needs a grammar-aware model, not more key geometry. "Jack" is also a common word, so it is not capitalised
as a name; the sentence-start capital and the full stop are the existing auto-capitalisation and
double-space rules.

**What changed.** `AutoCommitGate.kt` (rule and thresholds), `CorrectionReaders.kt` (score margin over the
runner-up; context weight moved here so the test scores exactly like the app), `NgramIndex.totalAfter`,
`LatinLanguageProvider.suggest()` (wiring, English only), tests `ContextSlipEvalTest`, `NgramIndexTest`.
