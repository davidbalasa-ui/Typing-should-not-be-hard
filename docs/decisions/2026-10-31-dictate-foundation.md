# Dictate Keyboard foundation

Status: selected by the project owner on October 31, 2026.

Use [Dictate Keyboard](https://github.com/DevEmperor/DictateKeyboard) as the upstream basis for this project. Initial pinned revision: `39f33c968995796d6857f56b07df148df6ba963b`.

The choice follows the repository comparison and the owner's preference for Dictate's combination of full keyboard input, dictation engines, correction controls, clipboard features, GIF support and editable symbol pages. LeanType and WM remain references; they are no longer competing application foundations.

The selected upstream declares Apache-2.0. Preserve upstream attribution, NOTICE and applicable third-party notices when integrating source. Do not mix implementations from differently licensed projects merely because they are public.

The initial targeted Dictate component baseline passed 33 tests. A full Android build, microphone-to-editor flow, recognition-accuracy comparison, and device typing trial are still outstanding. Selecting a foundation does not turn these gaps into completed validation.

The upstream source has been downloaded and pinned for evaluation. This decision records the foundation; it does not claim that a customized application or APK has already been created.

## Personalization direction

Use real input evidence to choose correction behavior and vocabulary support. Keep typed/dictated origin unknown for historical messages unless the owner or reliable metadata identifies it. Never treat every unusual word or conversational filler as an error, and never learn another participant's spelling as the owner's habit.

Keep private ChatGPT and SMS content outside the public repository. Only synthetic or non-identifying test cases and general behavior requirements should be committed. Candidate substitutions need confirmation before becoming unconditional replacement rules.
