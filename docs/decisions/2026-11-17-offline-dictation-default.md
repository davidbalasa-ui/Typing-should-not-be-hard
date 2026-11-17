# Offline dictation by default, set up from the mic

Date: 2026-11-17. Requested by David.

## What David asked for

On a fresh install the dictation (mic) button is greyed out, because nothing can transcribe yet. Tapping
it offers the three Whisper models, with simple bar graphs for speed, "power" and size, so he does not have
to think about it. Picking one downloads it, and dictation then works.

## Decisions

| Id | Decision | Why |
|---|---|---|
| OD-1 | The default transcription provider is the on-device engine (`local`), not OpenAI. | A fresh install has no API key, so OpenAI could never work out of the box; on-device works offline once a model is downloaded. |
| OD-2 | "Power" is shown as **Accuracy**. | It is the trade-off a bigger Whisper model buys; "power" alone does not say what you get. |
| OD-3 | Bars are relative, on a 1–5 scale: Tiny speed 5 / accuracy 2, Base 4 / 3, Small 2 / 5. Size is the real download size (104, 161, 376 MB) and is drawn in a neutral colour, because more of it is not better. | Whisper's published figures put Tiny at about twice Base's speed and Small well under half, with the error rate falling in the same order. |
| OD-4 | Base is recommended (badge and accent border), as the on-device engine's own default already was. | Best balance for most people; the picker and a blank model setting agree. |
| OD-5 | The grey mic stays tappable. A tap opens the picker as a keyboard sheet, so the text field keeps focus; it never starts a recording. | A greyed-out button that does nothing is a dead end. |
| OD-6 | The mic is grey whenever the active provider cannot transcribe: on-device without a model, or a cloud provider without its key. Keyless servers and custom endpoints never grey out. | Same rules the controller already applies when the mic is pressed, so the grey mic and the error never disagree. |
| OD-7 | Upgrades: if the old default was in use (setting reads as on-device, no model on disk) and an OpenAI key is stored, the provider is written back to OpenAI once. | Nobody who dictated through OpenAI finds a grey mic after updating. |
| OD-8 | The download runs in the app's background download service; the switch-over to the new model happens when it finishes even if the sheet was closed, followed by a toast. Only one of the three downloads at a time. | Downloads of 100–400 MB take a while on mobile data; closing the sheet must not lose the result. |
| OD-9 | Failures say what to do: offline ("connect, then tap to retry"), phone full, or other (retry). A download the phone has no room for is refused up front with the numbers. There is no network pre-check. | The app deliberately does not request network-state permission (see LanguageDataDownload). |
| OD-10 | Recording is refused before it starts when the on-device engine has no model (floating button, bubble, auto-start). | It used to record first and fail afterwards, losing what was said. |
| OD-11 | The cloud option stays: the picker ends with "Have an API key? Use a cloud service instead", which opens Add provider in settings. | Nothing David already has is removed. |
