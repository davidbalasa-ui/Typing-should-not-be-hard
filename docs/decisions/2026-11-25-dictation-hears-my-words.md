# Dictation puts My words back where the speech model misheard them

**Asked by David, 2026-11-25:** "Are the words that I add supposed to work with dictate? ... names like
Niah are written as Naya when I talk."

**What was wrong (checked in code).**

- My words *are* sent to cloud speech models: `transcriptionStylePrompt` appends them to the prompt
  (`app/.../dictate/TranscriptionPrompt.kt`, `withPersonalWords`).
- The on-device Whisper never sees them. `LocalTranscriptionProvider.transcribe` does not read
  `request.prompt` at all, and it cannot: sherpa-onnx 1.13.3's `OfflineWhisperModelConfig` has only
  encoder, decoder, language, task, tailPaddings and the two timestamp flags (checked with `javap` on the
  release AAR, sha256 `243ad797…`). Its hotwords option is for transducer models, not Whisper.
- After transcription only My fixes ran (`PersonalWords.fixTranscript`, `TranscriptFixer`). "Naya" is a
  real (rare) word in `en.json` (135) and was not a My fix, so it stayed.

**Decision.**

1. After My fixes, every dictation goes through `HeardNameFixer` (lib/typebetter): a heard word that
   sounds like exactly one My word becomes that word. "Sounds like" is `SoundKey`, a coarse English key
   (consonants plus four vowel classes, silent h, ph = f, x- = s...): Niah, Naya, Nia and Nyah share `nIA`.
   This runs on the finished transcript, so it covers every chunk of a long on-device dictation (PR 16)
   and cloud transcripts too, and works for any model.
2. Real words are protected the way typing protects them (the Xylos/silos rule): a word the dictionary
   knows only changes when the model wrote it with a capital, as a name, and it is rare: under 170 in a
   sentence, under 150 at a sentence start (where every word has a capital). Lower-case and common words
   never change. Measured over the whole English list with the starter pack as My words: 24 of about
   29,000 rare words change even in the worst case (written as a name mid-sentence), and they are names
   that sound like David's (Layla → Lyla, Kass → Kas). `HeardNameFixerTest` holds that under 40.
3. Never a guess: two My words with the same key are both left out; a Never change entry blocks a pair;
   acronyms (NPC, DM) and keys under three sounds are not matched.
4. English only: it needs the English keyboard to be active, its dictionary loaded (a probe for "the"),
   and the dictation language English or "detect". With "detect", a transcript of four or more words
   must be at least 80 % English dictionary words (not counting the sound-alikes), so a Spanish or German
   dictation is left alone. A new line or a colon counts as a sentence start. Never in password or
   incognito fields (same rule as My fixes).
5. If a swap is ever wrong, adding the heard spelling to My words stops it: a My word is never
   replaced.

**Not done.** Prompting the on-device Whisper would need a newer native engine; the update to sherpa-onnx
2.x is held back for an ONNX Runtime mismatch (`tools/fetch-sherpa-onnx.sh`), and whether 2.x accepts a
Whisper prompt is unverified.
