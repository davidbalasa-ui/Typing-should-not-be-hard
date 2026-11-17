# TypeBetter privacy policy

**Effective date:** 17 November 2026

TypeBetter (application ID `com.typebetter.keyboard`) is a personal Android keyboard built on the open
source Dictate Keyboard. It is published on Google Play by the owner of the Play developer account named
on its store page ("we" below), as a private test for its own developer. Questions: open an issue at
https://github.com/davidbalasa-ui/Typing-should-not-be-hard/issues.

## The short version

- **We run no server and receive none of your data.** No account, no analytics, no ads, no crash
  reporting.
- **What you type stays on your phone.** The keyboard does not log keystrokes or send typed text
  anywhere, except text you explicitly ask it to reword (below).
- **Dictation goes where you choose.** When you press the microphone, the recording goes directly from
  your phone to the speech service you set up with your own API key (for example OpenAI, Groq, Google
  Gemini, Deepgram or your own server), or stays on the phone if you pick on-device recognition.
- All of the App's source code is public in the repository above, so every statement here can be
  checked.

## What the App handles, and where it goes

| Data | When | Where it goes |
|---|---|---|
| Voice recordings | Only while you dictate | Your chosen speech service, over an encrypted connection. With on-device recognition, nowhere. The temporary recording on the phone is deleted after transcription. |
| Words from your lists (My words, My fixes) | Sent along with a recording | The same speech service, as spelling hints. Not sent with on-device recognition. |
| Text you ask to reword, and your prompts | Only when you tap a rewording prompt | Your chosen text service (for example OpenAI or Anthropic). |
| API keys | Stored when you enter them | App-private storage on your phone. Each key is sent only to its own service. |
| GIF searches | Only when you search GIFs | KLIPY, with your own KLIPY key. |
| Dictation history, clipboard history, My words, My fixes, Never change | On your phone | Nowhere. You can view and delete them in Settings. Clipboard history is off unless you turn it on. |
| Scan text photos and translations | On your phone | Nowhere. Google ML Kit's usage reporting is switched off. |
| Model and dictionary downloads | When you add a language or a model | Downloaded from GitHub. GitHub sees your IP address and the file requested, nothing else. |

Each speech or text service handles your data under its own privacy policy. Choose one you trust, or
use on-device recognition.

### Floating button (accessibility service)

The optional floating dictation button uses Android's accessibility service. It is off until you turn
it on, after an explanation screen in the App. It detects when a text field is focused so the button
can appear, and inserts your dictated text into that field. It reads the focused field's text only when
you apply a rewording prompt, and then sends it only to your chosen text service. Text dictated into a
password field is kept out of the dictation history. It does not collect, store or share other screen
content.

### Smartwatch

If you use the Wear OS companion, the phone sends your TypeBetter settings to your watch (including
your API key while standalone mode is on, so the watch can dictate on its own), and the watch can send
recordings to the phone. This uses Google's Wear OS data layer between your own devices.

### Backup

Settings, API keys and your word lists are included in Android's own backup, which Google encrypts
with your screen lock before it leaves the phone. Dictation history, recordings and clipboard history
are not backed up. You can turn backup off in Android's settings.

### Dictate Cloud

The App still contains the menu for Dictate Cloud, a paid service run by the original Dictate developer.
It does not work in TypeBetter, because its purchases belong to the original app's Play listing. If it
is ever used, recordings go to that operator's server under the Dictate Keyboard privacy policy
(`PRIVACY_POLICY.md` in the repository).

## Your choices

- Use on-device recognition so recordings never leave the phone.
- Delete dictation history, clipboard items and list entries in Settings at any time.
- Uninstalling the App deletes everything it stored on the phone.

## Children

The App is not directed at children.

## Changes

Changes to this policy are made in this file; its history is public in the repository.
