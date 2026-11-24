# Word-list cleanup, safe contractions, glide typing on by default

**Decision.** Three changes, asked for by David on 2026-11-24 ("Yes", to cleaning the misspellings out,
and "I need swipe to type"):

1. **23 misspellings removed from `en.json`.** teh, adress, begining, commited, definately, embarassing,
   goverment, grammer, happend, millenium, occured, publically, realy, recieve, seperate, suprise,
   therefor, thier, truely, untill, wellcome, wether, wich. The upstream FlorisBoard word list was built
   without a spelling filter, so these counted as words and were never corrected. Each correct spelling
   is one edit away or its sound-alike (asserted in `ContextualFixesTest`), so ordinary autocorrect now
   fixes them. `wether` and `therefor` are archaic real words; nobody typing on a phone means them.
2. **Apostrophe-less contractions are fixed on Space** for a curated English list
   (`ContextualFixes.SAFE_CONTRACTIONS`: dont, doesnt, im, cant, thats, youre, …). Until now every
   contraction was offered but never swapped, because ill / well / were / its are real words. Those
   ambiguous ones stay offer-only. A word in My words is never swapped.
3. **Glide (swipe) typing is on by default.** A device where it was switched off by hand keeps its choice;
   every device that never touched the switch gets it on, existing ones included. While glide is on, the
   swipe gestures on letter keys (up/down/left/right) are off, as they always were with glide; the
   space-bar and delete-key gestures are unaffected. Turning glide off in Gestures brings them back.
   `wont` and `cant` are real but rare words; they are swapped anyway because on a phone they are almost
   always the contraction (as other keyboards do), and adding one to My words stops it.

**How it was chosen.** The misspellings were found by listing every `en.json` word one edit from a far
more common word through a classic slip (vowel swap, single/double consonant, vowel substitution), then
reading the list by hand. Names and real words were left in (the review kept, for example, persson,
micheal, focussed, benefitted, descendents). No outside word list was downloaded.
