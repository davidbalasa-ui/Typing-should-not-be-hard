# Typing should not be hard

An Android keyboard project focused on comfortable typing, predictable corrections, useful clipboard tools, GIFs, and reliable dictation.

## Install on your phone

Open **https://github.com/davidbalasa-ui/Typing-should-not-be-hard/releases/latest/download/TypeBetter.apk** on the phone, then tap the download and allow the install. TypeBetter installs next to any other keyboard. Its setup screen then turns it on.

For automatic updates, add this repository's URL in [Obtainium](https://github.com/ImranR98/Obtainium).

## Priorities

- Put punctuation, symbols, and shortcuts where they are easy to reach.
- Make corrections predictable, reversible, and respectful of intentionally typed names and terms.
- Preserve convenient clipboard history, pinned snippets, and GIF access.
- Compare speech-recognition engines using real conversational dictation.

## Current stage

Dictate Keyboard is the selected upstream foundation, chosen by the project owner on October 31, 2026. The pinned source revision and adoption constraints are recorded in [the foundation decision](docs/decisions/2026-10-31-dictate-foundation.md). The initial comparison remains available as historical research.

An initial targeted component baseline passed 51 tests. Full Android builds remain blocked in the initial test environment by dependency-download failures; phone usability and speech-recognition accuracy are still untested. TypeBetter, the custom keyboard built on that foundation, is built and released by GitHub Actions (`.github/workflows/typebetter.yml`).

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

These commands download third-party sources and test dependencies. Full repositories, SDKs, model weights, credentials, and build outputs are excluded from version control. Upstream code retains its original license; the product license remains to be finalized around the selected Apache-2.0 upstream and its dependency terms.
