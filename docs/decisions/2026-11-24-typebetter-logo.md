# TypeBetter logo (2026-11-24)

**Decision.** TypeBetter gets its own logo in place of Dictate's blue microphone: a bold navy **T** over
a red typo squiggle that straightens into a clean green line, with five dictation sound-wave bars
underneath, on a cream background (`#FFF3DE`).

**How it was chosen.** David was shown three original concepts (Checked Key, Voice to Text, Typo
Fixed) rendered in every launcher mask, at home-screen size, as a themed icon and as a status-bar icon.
He picked Typo Fixed and asked for "a little dictation look below it". Of three dictation versions
(sound waves, microphone, microphone plus waves) he picked the sound waves.

**What changed.**

- `ic_app_icon_foreground.xml` (new vector, replaces Dictate's five density WebPs) and
  `ic_app_icon_monochrome.xml` in `app/` and `wear/`. The monochrome copy is the same shapes in one
  colour; it is the Android 13+ themed icon and the notification small icon.
- `ic_app_icon_background` is now cream. It also colours the splash-screen icon backdrop.
- Debug and beta builds drop FlorisBoard's old lotus icons and use the same logo on a lavender
  (debug) or light blue (beta) background, so they stay easy to tell apart from release.
- `app/src/main/ic_app_icon-playstore.png` is the 512 × 512 Play Store icon (full square, opaque; Play
  rounds the corners itself), rendered from the same vector so the store and launcher match.

**Constraints checked.** All drawing stays inside the 66 dp safe circle of the 108 dp adaptive canvas
(rendered at 10 px/dp: 0 pixels outside, farthest drawn point 32.6 dp from centre), so no launcher
mask crops it. The artwork is drawn from plain strokes, no fonts or third-party art.

`img/Icon_512x512_2_round.png` stays: it is Dictate's logo and is only used by `UPSTREAM_README.md`.
