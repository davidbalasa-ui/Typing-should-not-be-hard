# Android keyboard source research for David

Research date: October 31, 2026. Goal: an Android keyboard for a Samsung phone with a comfortable interface and symbol layout, useful clipboard history and GIFs, less intrusive correction, and substantially better dictation.

## Recommendation

Evaluate **LeanType and Dictate Keyboard first**, with **WM Keyboard** as a promising third candidate. Keep **HeliBoard** as the simpler foundation/reference. Do not start by writing a keyboard from scratch or patching Gboard.

These rankings are engineering judgments about fit, not measured typing-accuracy scores. I searched GitHub, Reddit, F-Droid, project documentation, release notes, issue trackers, GrapheneOS/Privacy Guides forums, and Hacker News. Exa searches requested 116 results across 12 queries; those results include duplicates and irrelevant matches, not 116 independently audited sources. Searches covered five angles: complete keyboard foundations, user experience and failures, clipboard/GIF/layout implementations, speech engines, and licensing/maintenance. Targeted web searches and direct repository inspection supplemented that discovery.

Twelve repositories were cloned, with exact revisions recorded in repository-manifest.json. I inspected relevant source paths and licenses, but did not compile these Android apps, install them on David's phone, execute their test suites, or measure speech accuracy. A file or test being present does not prove the feature works reliably. Default-branch source can contain changes newer than a release.

## Main candidates

| Candidate | Why it fits | Main concern | Declared top-level license | Role |
|---|---|---|---|---|
| [LeanType](https://github.com/LeanBitLab/LeanType) | HeliBoard foundation, custom layouts, searchable/editable clipboard, GIF integration, separate Whisper voice plugin | Large and fast-changing feature surface; inherited correction engine is not automatically better; plugins add integration work | GPL-3.0 | Best initial feature-fit experiment |
| [Dictate Keyboard](https://github.com/DevEmperor/DictateKeyboard) | Full FlorisBoard-derived keyboard with local/cloud speech engines, correction controls, clipboard, editable symbols and GIF search | Major rebuild and broad scope; provider/setup complexity; needs real typing and app compatibility tests | Apache-2.0 | Strongest voice-centered candidate |
| [WM Keyboard](https://github.com/wasi-master/wmkeyboard) | Editable/importable layouts, correction confidence controls and undo, clipboard, GIFs, multiple local speech options | New pre-1.0 project, very broad feature/dependency surface; independent reliability evidence is limited | MIT for project code | Third trial candidate, potentially flexible foundation |
| [HeliBoard](https://github.com/HeliBorg/HeliBoard) | Established editable layouts, dictionaries, clipboard history, smaller feature scope than its forks | Multiple user reports of poor or intrusive autocorrection; stock repo does not provide the desired integrated GIF search; glide library caveat | GPL-3.0 | Conservative foundation/reference |
| [FUTO Keyboard](https://github.com/futo-org/android-keyboard) | Integrated offline dictation, predictions, swipe typing, LatinIME foundation | Source-available license restricts modifications to noncommercial purposes and distribution; do not treat as freely mixable open-source code | FUTO Source First 1.1-kb | Personal-use comparison, especially dictation |

Latest non-prerelease releases returned by GitHub during this research: LeanType **v4.3.0, October 30**; Dictate **v6.4.0, October 7**; WM **v0.6.4, October 28**; HeliBoard **v4.2, October 12**; FUTO **0.1.30, August 4**. Release numbers are not quality ratings. Dictate's latest release returned no GitHub APK assets, so its release page is not necessarily a direct APK installation route.

## 1. LeanType: nearest match to the requested feature set

LeanType retains the HeliBoard/AOSP family of keyboard behavior while adding clipboard editing/search, snippets, GIFs, and optional AI tools. Its voice and GIF functionality is split into companion apps:

- [Voice plugin](https://github.com/LeanBitLab/Leantype-Voice-Plugin): whisper.cpp-based on-device transcription, exposed to the keyboard over an AIDL service.
- [GIF plugin](https://github.com/LeanBitLab/LeanType-Gif-Plugin): GIPHY, KLIPY, and local media; native insertion into compatible apps.

This is actual source integration, rather than a README-only promise. In the checked-out keyboard, see:

- `app/src/main/java/helium314/keyboard/latin/ClipboardHistoryManager.kt`
- `app/src/main/java/helium314/keyboard/keyboard/clipboard/ClipboardHistoryView.kt`
- `app/src/main/java/helium314/keyboard/latin/voice/VoiceInputManager.kt`
- `app/src/main/java/helium314/keyboard/latin/voice/VoicePluginManager.kt`
- `app/src/main/java/helium314/keyboard/latin/gif/GifPluginManager.kt`
- `app/src/main/java/helium314/keyboard/latin/gif/GifInserter.kt`

Important implementation detail: the GIF manager pins plugin signing certificates and uses fixed package/action names. A renamed or independently signed fork needs a deliberate compatibility/signing strategy. Simply copying the plugin into a new app name is not sufficient.

Current checked-out code and the GIF plugin use **GIPHY and KLIPY**. Some indexed documentation/release descriptions still say Tenor. Treat the checked-out implementation as authoritative for this snapshot. Provider API keys and terms remain separate from the keyboard's source license.

Potential drawback: its inherited dictionary/correction machinery does not inherently solve David's unwanted replacements. First try suggestions with automatic replacement disabled or made conservative. The issue tracker contains reports about rapid typing capitalization, toolbar placement, and clipboard presentation. Those are reports to reproduce, not findings verified on David's phone. See [#598](https://github.com/LeanBitLab/LeanType/issues/598).

## 2. Dictate Keyboard: particularly relevant for frequent voice use

This is no longer just a microphone-only utility. Current main is a complete keyboard rebuilt on FlorisBoard, with its own prediction/correction additions. This distinction matters: upstream FlorisBoard's missing released suggestions do not mean this fork also lacks them.

It documents local models, direct cloud-provider keys, self-hosted endpoints, live transcription, editable symbol pages, clipboard tools, and KLIPY GIF search. Cloud provider charges are separate from owning/building the app; offline recognition needs model downloads and enough phone resources. Any optional rewriting should stay explicitly separate from faithful transcription, because silently changing intended wording is one of David's existing complaints.

Relevant source inspected:

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/AutoCorrectStrength.kt`: cautious, balanced, and aggressive policies; comments distinguish suggestion generation from silent replacement.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/TouchBeamDecoder.kt`: touch-based candidate decoding.
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/AutocorrectEvalTest.kt`: existing evaluation code, not run in this research.
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/gif/KlipyGifProvider.kt`: actual GIF provider integration.
- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/audio/LiveSpeechSplitter.kt`: speech segmentation implementation.

The developer's [correction investigation, #242](https://github.com/DevEmperor/DictateKeyboard/issues/242), is unusually useful: it measures tradeoffs and explains why contextual replacement can damage correctly typed words. Its quoted evaluation figures are largely simulated, not proof of accuracy on a particular user's typing. The [v6.4.0 release](https://github.com/DevEmperor/DictateKeyboard/releases/tag/v6.4.0) specifically includes customizable symbol pages.

This should be tested alongside LeanType before picking a base. If dictation is the largest source of frustration, Dictate may become the better choice despite LeanType's closer conventional feature fit.

## 3. WM Keyboard: flexible, but validate the basics first

WM is a newer Kotlin/Compose implementation rather than another straightforward LatinIME skin. It documents layout importing/editing, confidence-gated correction, undo, local learning, clipboard search/pinning, GIF search, and numerous on-device speech engines.

Relevant checked-out documentation/source includes:

- `docs/src/content/docs/smart/autocorrect.mdx`
- `docs/src/content/docs/tools/whisper.mdx`
- `docs/src/content/docs/emoji/gifs.mdx`
- `native/harper-jni/` and bundled third-party license notices.

Its correction documentation exposes precisely the sort of controls David may want: when space/Enter/punctuation trigger replacement, confidence thresholds, and undo. This is a good design direction; it is not evidence that its corrections are superior.

Reddit reactions are mixed: enthusiasm about breadth of features, alongside correction and voice-integration problems and warnings about pre-1.0 maturity. See [user discussion](https://www.reddit.com/r/degoogle/comments/1wtpfd0/keyboard_you_use/) and [feature discussion](https://www.reddit.com/r/fossdroid/comments/1wwkqf6/which_is_best_keyboard_app_with_tons_of_features/). Version-specific complaints may already be fixed. Because the project has a very large scope for its age, verify everyday typing, startup time, memory, and app compatibility before treating it as the lowest-rework option.

## Other useful codebases

| Repository | Useful part | Why it is not the first choice here |
|---|---|---|
| [AnySoftKeyboard](https://github.com/AnySoftKeyboard/AnySoftKeyboard) | Long-running Apache-2.0 implementation, language packages, configurable correction, gestures | More work to reach David's desired integrated media/clipboard/interface experience |
| [FlorisBoard](https://github.com/florisboard/florisboard) | Apache-2.0 UI, theme/extension system, clipboard | README still explicitly warns that released word suggestions/spell checking are absent; check Dictate's fork first |
| [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) | GPL-3.0 corner-swipe symbol access, custom layouts, developer-friendly keys | Interaction differs substantially from familiar phone typing; good symbol-layout inspiration |
| [Urik](https://github.com/urikdev/Urik) | GPL-3.0 custom symbol assignments, local dictionaries, encrypted clipboard storage | Beta; less direct fit for integrated GIF/dictation needs |
| [CleverKeys](https://github.com/tribixbite/CleverKeys) | GPL-3.0 power-user clipboard, customizable gestures, offline GIF packs | Offline packs are not equivalent to searchable online GIF catalogs; substantial gesture complexity; validate independently |
| [GifBoard](https://github.com/gifboard/gifboard) | GPL-3.0 GIF search, native content insertion and URL fallback | GIF-only companion, not a complete text keyboard |
| [Simple Keyboard](https://github.com/rkkr/simple-keyboard) | Small Apache-2.0 baseline | Explicitly excludes GIFs, spell checker, emojis and swipe typing, so it removes several features David wants |
| [Thumb-Key](https://github.com/dessalines/thumb-key) | Alternative thumb-oriented layout, AGPL-3.0 | Requires learning a different typing method; not the easiest route to preserving Gboard conveniences |

Also surveyed OpenBoard, Fossify Keyboard, Hacker's Keyboard, Fcitx5/Rime/Trime, FluxBoard, Iris, and smaller recent voice keyboard projects. They did not displace the finalists for this particular English-first, clipboard/GIF/dictation combination. That is a fit judgment, not a claim that all of those repositories received a deep code audit.

CleverKeys has an F-Droid [discussion about AI-assisted development and provenance](https://gitlab.com/fdroid/fdroiddata/-/work_items/4056). The issue was closed, and the maintainer declined the proposed action. It is not an established finding of infringement. The project's own comparative feature table is promotional and was not treated as an independent benchmark.

## Voice engines deserve a separate comparison

[Marmalade STT](https://github.com/maxwhipw/marmalade-stt-android) is an MIT-licensed, recently created voice-input project with multiple local engine options. It can pair with compatible keyboards without merging recognition code into the keyboard. It documents HeliBoard, FlorisBoard, AnySoftKeyboard and Unexpected support, and provides benchmark notes. The repository and benchmark claims need device verification; the project was only created in October 2026.

Other references found: [FUTO Voice Input](https://github.com/futo-org/voice-input), [Outspoke](https://github.com/minburg/outspoke), [WhisperInput](https://github.com/alex-vt/WhisperInput), [VoxPen](https://github.com/soanseng/voxpen-android), and [whisper.cpp](https://github.com/ggml-org/whisper.cpp). FUTO's standalone voice repository says development focus has shifted to its integrated keyboard. Voice-project code licenses do not automatically cover every downloaded model's license.

For David, compare the same recordings with the same names, technical terms, pauses and self-corrections. Measure wrong words, inserted words, premature stops, delay after finishing, and whether editing during recognition resurrects deleted text. Test short messages and longer conversational prompts, not only carefully read sentences. Keep automatic text rewriting off initially.

## What Reddit changes about the recommendation

Reddit is useful evidence about things to test, not a controlled accuracy study. It repeatedly surfaces HeliBoard/FUTO as alternatives, but also includes frustrated HeliBoard users describing aggressive wrong replacements. That makes recommending HeliBoard as a guaranteed correction upgrade inappropriate.

- [HeliBoard correction complaints, February 2026](https://www.reddit.com/r/degoogle/comments/1r0cjfg/how_do_you_guys_get_by_with_heliboard/)
- [HeliBoard typing fit discussion, September 2026](https://www.reddit.com/r/degoogle/comments/1w5679f/heliboard/)
- [Mixed keyboard recommendations, August 2026](https://www.reddit.com/r/androidapps/comments/1w2hmjv/any_secure_nonai_keyboard_apps/)
- [Earlier multilingual correction experiences](https://www.reddit.com/r/degoogle/comments/1ha84lw/)

FUTO has favorable voice reports, but users also report device/model/language-specific slowness. Its developer discussion [#1492](https://github.com/futo-org/android-keyboard/issues/1492) makes clear that using a larger or different speech engine is a performance tradeoff, not an automatic win.

## Reuse constraints

The code can be legitimately reused within each license. Keep copyright notices, license files and attribution. GPL forks are viable for personal use; distributing a modified app introduces GPL source/license obligations. Apache-2.0 and MIT project code are more permissive, but bundled libraries, dictionaries, artwork and model weights may carry different terms. WM's top-level MIT license does not convert all of its vendored dependencies into MIT. Do not copy GPL implementation into an Apache/MIT-only distributed fork without addressing the resulting obligations.

FUTO is materially different: the inspected Source First 1.1-kb text permits modification only for noncommercial purposes, restricts distribution to free noncommercial copies, and requires retaining payment-related functionality in distributed copies. Personal experimentation is a possible route; commercial derivative use needs a separate permission route. Do not merge FUTO code into a generally reusable base on the assumption that visible code means unrestricted open source.

## Collected source and next decision

The accompanying source bundle contains three finalist keyboard snapshots plus LeanType's two companion plugins. Original licenses are retained. Git history, uninitialized submodule contents, downloaded speech models, dependency caches and build outputs are not included. The manifest records all twelve cloned repositories and exact commits. Submodule status beginning with `-` means it has not been downloaded; initialize it at the recorded revision before building the relevant project.

Recommended first comparison: **LeanType versus Dictate**, with **WM** available if either fails the layout/correction requirements. Choose one foundation after a short real-phone trial, then change its interface and correction policy. Combining three entire keyboard engines would increase rework; use other repositories as focused references or compatible components only.

This research establishes candidates and reusable source availability. It does not establish that any keyboard already meets every requirement on David's Samsung.
