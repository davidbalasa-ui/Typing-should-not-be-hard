# One voice model choice, Parakeet first for English (2026-11-27)

David found the "On-device (offline)" dialog under AI providers confusing and asked which model is best
for English only, and why he has that dialog and the Whisper picker in Dictation settings.

## What the two were

Both pick the same thing: the on-device engine's `transcriptionModel` on the LOCAL account. The Dictation
settings section (and the keyboard's setup sheet) showed only Whisper Tiny, Base and Small; the AI
providers dialog is the full upstream catalog. Whichever was used last wins.

## Decision

- **English: Parakeet TDT 110M is the recommended model** ("Best for English"). NVIDIA's card gives
  7.49 % mean WER on the Open ASR leaderboard sets (2.4 / 5.2 on LibriSpeech clean / other), it is a
  non-autoregressive transducer (fast on a phone) and it writes punctuation and capitals itself. Canary
  180M Flash is 7.12 % but larger, slower and limited to ~35 s per pass; multilingual Whisper Base is
  roughly 10-13 %.
- The simple picker (Dictation settings and the setup sheet) is now language-aware
  (`DictationReadiness.choicesFor`): for English (the active dictation language, or the phone's when it is
  set to detect) it shows Parakeet, Whisper Base and Whisper Small; for every other language Tiny, Base and
  Small with Base recommended, as before. Tiny is left out for English because Parakeet beats it on both
  bars; an installed model is always kept in the list (`visibleChoices`).
- The section title is "Voice model", not "Whisper model". Each row says "English only" or "Any language".
- The AI providers dialog keeps the full list (nothing removed) with plain-language text, a "Best for
  English" badge on Parakeet TDT 110M, and the hold-Send checkbox and the idle-unload slider folded under
  "Advanced" below the list.
- `ProviderRegistry.LOCAL.defaultTranscriptionModel` stays `whisper-base`: it is what a blank pick resolves
  to for anyone, in any language, so changing it could move people who never chose.
