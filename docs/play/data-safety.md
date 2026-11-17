# Play Console answers for TypeBetter

What to pick in the Play Console's **App content** forms, with the code behind each answer. Google's
help says an internal test can start before app setup is complete; if the Console still insists on a
form, the answers are here. All of them are required before a closed, open or production release. Evidence was gathered from the code on 2026-11-17 (file:line references are to that
commit and may drift).

## Privacy policy URL

`https://github.com/davidbalasa-ui/Typing-should-not-be-hard/blob/main/docs/play/privacy-policy.md`

## Ads

No ads. (No ads SDK in `app/build.gradle.kts` dependencies.)

## App access

All functionality is available without special access. (No login. Dictation needs the user's own
provider key or on-device recognition; say so in the notes field.)

## Data safety

**Does your app collect or share any of the required user data types?** Yes.

**Is all of the user data collected by your app encrypted in transit?** Yes. Every built-in provider
and download URL is https/wss (`lib/dictate-core/.../provider/ProviderRegistry.kt`), except the built-in
local Ollama preset, `http://localhost:11434` (`ProviderRegistry.kt:902`), which never leaves the phone.
Other plain-http traffic only goes to an address the user types in themselves (allowed by
`usesCleartextTraffic`, `app/src/main/AndroidManifest.xml:71`).

**Do you provide a way for users to request that their data is deleted?** Yes: everything is stored on
the phone and can be deleted in Settings or by uninstalling.

Data types to tick (all: **Collected**, **not Shared**, **Optional**, purpose **App functionality**):

| Play data type | Why | Evidence |
|---|---|---|
| Audio → Voice or sound recordings | Dictation is sent to the speech service the user picked | `PRIVACY_POLICY.md:73-80`, provider presets in `ProviderRegistry.kt` |
| App activity → Other user-generated content | Text the user asks to reword, and word-list spelling hints sent with a recording | `PRIVACY_POLICY.md:85-89, 189-191`, `DictateController.kt:2905, 3468` |
| App activity → In-app search history | GIF search terms go to KLIPY with the user's key | `KlipyGifProvider.kt:68, 105` |

"Not shared": Google's form exempts transfers the user starts to a service they chose themselves, which is
every case above. Leave "Processed ephemerally" unticked: the chosen provider's retention is outside our
control.

Not collected (nothing leaves the phone): typed text and keystrokes, clipboard, contacts, location,
photos (Scan text runs on-device ML Kit with its telemetry removed, `AndroidManifest.xml:407-411`), device
IDs, crash logs (local crash screen only), analytics (none in the dependency list).

Dictate Cloud (`api.dictatekeyboard.com`, Play Billing in `DictateCloudBilling.kt:107-134`) is still in
the code but cannot sell anything under this package name, because its products exist only on the
original app's listing. If it is ever made to work, add **Financial info → Purchase history**.

## Content rating

Category: Utility / productivity. Answer **No** to every violence, sexuality, language, drugs and
gambling question. Users can interact or exchange content: **No** (no user-to-user features). Shares
location: No. Digital purchases: No (Dictate Cloud cannot sell anything, see above).

## Target audience

18 and over. Not designed for children.

## Sensitive permissions and APIs (needed before closed or production testing, not for internal)

1. **Accessibility API.** `DictateAccessibilityService` (`AndroidManifest.xml:93-104`) is not an
   accessibility tool (no `isAccessibilityTool`). Declaration text: *"The optional floating dictation
   button uses the accessibility service to detect when a text field is focused in any app, so the button
   can appear there, and to insert the user's dictated text into that field. It reads the focused field's
   text only when the user applies a rewording prompt, which is then sent to the user's own chosen text
   service. No other screen content is collected, stored or shared."* The in-app disclosure exists
   (`DictateFloatingButtonScreen.kt:380-394`, strings `dictate__floating_button_disclosure_*`) but says
   "Dictate" and does not mention that reworded text goes to the provider; it needs a TypeBetter wording
   fix before a declaration is submitted. Google also asks for a short screen video of the disclosure,
   accepting and declining.
2. **Foreground service types.** `microphone` (accessibility service while recording,
   `AndroidManifest.xml:97`) and `dataSync` (`ModelDownloadService`, model downloads the user starts).
   Declare both with a short video each. `shortService` needs no declaration.
3. **Recognition service / RECORD_AUDIO.** No form, covered by the data safety audio answer.
