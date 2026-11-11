# Keyboard test baseline — October 31, 2026

The three finalist source trees and LeanType's two companion plugins were downloaded again at the same revisions as the research session. The earlier temporary source archive was no longer available. No upstream application code was modified, no remote repository was written, and no phone was changed.

## Executed results

| Candidate | Targeted JVM result | What actually ran |
|---|---|---|
| LeanType | 3 tests passed | Upstream AudioUtilsTest against original AudioUtils; PCM/WAV packaging |
| Dictate Keyboard | 33 tests passed | 20 upstream WordRun tests plus 13 added checks against original correction, symbol and field-text components |
| WM Keyboard | 15 tests passed | Upstream VoiceSpacingTest and VoiceClipGateTest against original implementations |
| Dictate layout asset audit | Passed | Upstream Hindi layout checker: reachable characters, matching labels, registry and subtype registration |

Total targeted JUnit result: **51 passed, 0 failed**. This is a deliberately small selection, not the complete suites. The layout audit is additional and is not counted as a JUnit test. All three source selections compiled with the Kotlin 2.3.0 compiler bundled with Gradle 9.4.1 and ran on Java 17 using JUnit 4.13.2. Upstream kotlin.test annotations in WordRunTest used the Kotlin JUnit 4 adapter; this differs from Dictate's normal Gradle test environment. No Android framework stubs or mocks were introduced.

The added Dictate checks cover:

- No silent correction without valid tap count; cautious/balanced/aggressive threshold differences.
- Remapping one symbol without moving adjacent keys; restoring defaults and preserving row sizes.
- Preserving family emoji joiners and variation selectors.
- Preserving a technical snippet containing campaign names, arrows, a URL, and query-string punctuation.
- Replacing a selected name without deleting surrounding text.
- Taking a suggestion without erasing trailing punctuation.
- Revising dictated text before the cursor without deleting text after it.
- Continuing a transcript after previously handed-over text without repeating it or inserting the tail of a partial word.

These are component checks. For example, preserving text in FieldText is not a successful clipboard-history or cross-app paste test, and testing a correction threshold is not an autocorrect accuracy benchmark.

## Android build attempts

Java 17 was already installed. Android command-line tools, Android platform 36, build tools 36.0.0, and platform-tools were installed locally. Gradle 8.13 and 9.4.1 distributions were downloaded separately after the wrappers failed.

| Candidate | Command/attempt | Result |
|---|---|---|
| LeanType | Gradle 8.13, `:app:testStandardDebugUnitTest` | Build configuration failed while fetching Android Gradle plugin 8.13.2 and Kotlin plugin 2.2.21; log includes network-unreachable errors for Google Maven/Maven Central |
| Dictate | Gradle 9.4.1, `:lib:dictate-core:testDebugUnitTest` | Build configuration failed resolving Android application plugin 9.2.1 from configured plugin repositories |
| WM | Upstream Gradle wrapper, requested unit-test baseline | Wrapper could not download Gradle 9.8.2; network-unreachable error before project configuration/task validation |

These are build/environment blockers, not failed application tests. LeanType was retried with normal proxy configuration and IPv4 preference, then with a separately downloaded Gradle; dependency resolution still failed. Dictate's wrapper also failed initially. Its later plugin resolution message does not by itself prove that the dependency is unavailable upstream. No successful full build or installable APK was produced.

The first manual Dictate component compile omitted WordChars.kt. Adding that original upstream dependency to the harness fixed the harness configuration; no application change was needed. The final logs show all selected tests passing.

## What has not been tested

No emulator or connected phone was available in this session. `/dev/kvm` was absent. No IME activation, touch typing, cursor behavior through Android InputConnection, clipboard history, GIF provider call/insertion, native Whisper inference, actual microphone recording, recognition accuracy, battery use, or real-time latency has been tested. The LeanType plugin repos were downloaded and revision-checked but not built; the voice plugin's whisper.cpp submodule is not initialized. No paid API calls were made.

## Current judgment

Keep **LeanType and Dictate** as the first two candidates. Dictate now has direct evidence for the particular symbol/field/correction controls tested here. WM's selected voice-handling tests also pass. These results do not support declaring an overall winner yet, and they do not establish that one keyboard understands David's speech better.

The next useful stage is full Android builds in an environment where Gradle dependencies resolve, followed by the same phone-based typing and recorded-dictation trials for both finalists. Test literal names and technical terms, intentional pauses, corrections during dictation, punctuation, and paste/GIF behavior in the apps David uses. Keep optional rewriting disabled for initial dictation comparisons.

## Reproduce or resume

The accompanying checkpoint includes exact source revisions, test/toolchain dependency checksums, the test harness and tests, and raw evidence logs. It omits full repositories, SDKs, dependency caches and compiled outputs to keep the checkpoint small. The upstream repositories remain the source of their respective licenses and code.

On a machine with Git, curl, Python 3 and Java 17+:

```sh
python3 restore_sources.py
python3 restore_test_toolchain.py
python3 run_component_tests.py
```

Full Android builds should use each repository's own wrapper and setup documentation. `run_tool.py` records the environment setup attempted here; it does not resolve the remaining network issue. The targeted harness is separate from all upstream checkouts and does not patch their build files or source.
