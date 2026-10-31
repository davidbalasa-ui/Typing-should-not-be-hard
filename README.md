# Typing should not be hard

An Android keyboard project focused on comfortable typing, predictable corrections, useful clipboard tools, GIFs, and reliable dictation.

## Priorities

- Put punctuation, symbols, and shortcuts where they are easy to reach.
- Make corrections predictable, reversible, and respectful of intentionally typed names and terms.
- Preserve convenient clipboard history, pinned snippets, and GIF access.
- Compare speech-recognition engines using real conversational dictation.

## Current stage

Repository evaluation and testing. LeanType and Dictate Keyboard are the leading candidates; WM Keyboard is an additional candidate. The application foundation has not been selected yet.

An initial targeted component baseline passed 51 tests. Full Android builds remain blocked in the initial test environment by dependency-download failures; phone usability and speech-recognition accuracy are still untested. This repository does not yet contain a custom Android app or an installable APK.

## Research and test checkpoint

- [Repository comparison](docs/research/keyboard-repository-research.md)
- [Executed test baseline and limitations](evaluation/Keyboard_Test_Baseline.md)
- [Current handoff](docs/NEXT-STEPS.md)

To restore the pinned evaluation sources and run the targeted JVM baseline, use Git, curl, Python 3, and Java 17+:

```sh
cd evaluation
python3 restore_sources.py
python3 restore_test_toolchain.py
python3 run_component_tests.py
```

These commands download third-party sources and test dependencies. Full repositories, SDKs, model weights, credentials, and build outputs are excluded from version control. Upstream code retains its original license; no product license or upstream foundation has been selected for this project.
