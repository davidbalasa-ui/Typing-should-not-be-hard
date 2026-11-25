# Faster on-device dictation

**Problem.** David, 2026-11-25: a short dictation comes back in about a second, but a long one takes
about ten seconds after he stops talking. The cloud Whisper app he compares it to answers almost at once.

**Why it was slow** (read from the code; no device timing was available):

1. Nothing was transcribed while he talked. The whole recording was decoded after the stop
   (`DictateController.transcribe` → `LocalTranscriptionProvider.transcribe`), so a minute of speech meant
   a minute's worth of decoding while he watched the spinner.
2. Recordings over 28 s were cut by a VAD at every 0.25 s pause and **each phrase was decoded on its own**
   (`transcribeSegmented` → `drainSegments` → `decodeOnce`). Whisper pads every decode to a full 30 s
   window, so a one-minute dictation with a breath every few seconds cost 15 to 20 full encoder passes
   instead of 3.
3. The recognizer always ran on 2 CPU threads (`numThreads: Int = 2`, no caller overrode it), leaving
   most of an 8-core phone idle.

**Decision.**

- **Transcribe while you talk** (`LocalEarlyDecoder`). For an on-device batch dictation, the microphone
  frames that go into the WAV are also fed to a Silero VAD on a background thread. Speech is packed into
  windows of about 12 s and each is decoded as soon as it is full, so by the stop only the last window is
  left. Nothing else changes for the user: the WAV is still written, kept in history and resendable. If
  the early decoder fails, hears no speech, or the model or language changed during the recording, the
  dictation takes the old path exactly as before (silence gate and all).
  It only answers for recordings longer than one decode (28 s for Whisper). Shorter ones were always a
  single quick pass over the whole clip and stay exactly that, so short dictations transcribe the same as
  before. Longer ones were already cut at pauses by the old path, so windows change only the speed.
  Decoding waits until a window's audio has fully arrived, and audio no window can still need is dropped,
  so memory stays bounded on very long or silent recordings. It also applies to system voice input,
  which records in the same batch mode.
- **Pack speech into full windows** (`SpeechWindowPlanner`) on the long-audio path too (resends, picked
  files, the offline fallback): segments are joined, with 0.2 s of padding each side, until a window is
  as full as the model takes (28 s for Whisper). Long pauses shrink to two pads, short pauses stay in.
- **Threads**: half the phone's cores, between 2 and 4 (`LocalTranscriptionProvider.defaultThreads`). 4 on
  a typical 8-core phone, which is its fast cluster; small phones stay at 2.
- **Safety**: decodes are serialised process-wide (one native recognizer is never decoded from two threads
  at once), and the recognizer cache now waits for a borrowed recognizer to be handed back before it
  swaps models or languages, instead of freeing it under a running decode.
- **Measurable**: logcat tag `DictateLatency` now has `earlyWindow` (per window: audio length, decode ms,
  whether after the stop), `earlyDecodeFinished` (windows, tail ms after stop), `earlyDecodeCompleted`
  (the controller's wait), `localSinglePass` and `localSegmented`.

Side effects, accepted: the thread rule also applies to every other on-device path (resend, import,
the offline fallback, batch use of a streaming model); serialised decodes mean two long-form segments no
longer decode at the same moment on one recognizer (they queue). If a resend in another language runs
during a recording, the two take turns with the cached recognizer, which can mean a reload; rare.

No setting was added: it is how dictation should work, and David asked for simpler settings, not more.
Live (streaming) on-device models, long-form mode and cloud providers are untouched.

**Tested.** `SpeechWindowPlannerTest`: a minute of short phrases packs into 3 windows instead of 20, windows
never overlap, every speech sample lands in exactly one window, long pauses shrink and short ones stay,
the window limit and target are honoured, and the thread rule. The native decode cannot run on the JVM;
it is the same `recognizer.decode` call as before, now fed longer windows.
