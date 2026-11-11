# Current handoff

## Goal

Build a comfortable Android keyboard for David's Samsung phone. Preserve the convenient clipboard and GIF features he likes while improving symbol access, unwanted corrections, and dictation. Root access is not part of the proposed approach.

## Selected foundation

The project owner selected **Dictate Keyboard** on October 31, 2026. Use its pinned source as the basis for development; LeanType and WM remain references. See [decision record](decisions/2026-10-31-dictate-foundation.md). This is a product choice, not a claim of measured superiority.

## Completed

- Research compared complete keyboards and voice/media components before the owner selected Dictate.
- Five source repositories were downloaded at pinned revisions: three keyboards, LeanType Voice Plugin, and LeanType GIF Plugin.
- A targeted JVM run passed 51 tests: LeanType 3, Dictate 33, WM 15. The separate Dictate Hindi layout audit also passed.
- Full Android baseline attempts and their blockers are retained under `evaluation/evidence/`.
- Upstream application code remains unchanged. The test harness lives outside those checkouts.

## Next useful work

1. Resolve the Android build/dependency environment using each repository's own build configuration. Determine whether the observed errors are transport restrictions or dependency availability; do not downgrade dependencies blindly.
2. Build and launch Dictate, then test keyboard activation, cursor/selection behavior, clipboard history, and GIF insertion in representative apps.
3. Compare the same dictated recordings and deliberate typing mistakes. Include technical terms, names, pauses, self-corrections, numbers, and punctuation. Keep optional rewriting off at first.
4. Document the first custom layout and correction behavior on the selected Dictate foundation before changing product code.

## Decisions still open

- Exact symbol/toolbar layout and which corrections may happen automatically.
- Offline, cloud, or selectable dictation engines; latency, accuracy, and cost priorities.
- Personal-only distribution versus eventual publication, which affects upstream licensing choices.

Do not infer an overall typing-quality winner from the selected component test counts. No native recognition accuracy, battery measurement, phone typing trial, or complete Android test-suite pass has occurred. No custom APK exists yet.

## Licensing boundaries

The checkpoint contains project-owned evaluation scripts and tests plus research and logs; upstream implementations are restored from their original repositories. Preserve upstream notices and assess bundled data/library/model terms when adopting a foundation. LeanType is GPL-3.0, Dictate is Apache-2.0, and WM declares MIT for its own code. FUTO's inspected Source First license has noncommercial modification/distribution restrictions, so it is a comparison candidate rather than an unrestricted code source.

## Personalization evidence

Historical chat excerpts can suggest error categories but usually do not establish whether a message was typed or dictated. Record input origin as unknown unless independently supplied; keep observed text, proposed intent, and confirmed correction separate. Preserve intentional vocabulary and casual writing. Analyze sent messages rather than learning from other participants. Private chat/SMS corpora and identifying examples belong outside this public repository; use synthetic equivalents for public regression tests. No historical export has been ingested yet.
