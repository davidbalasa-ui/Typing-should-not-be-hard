# GIF search works without setup (built-in KLIPY key)

Date: 2026-11-26. Asked by David: "Can you automate getting the API key" (the GIF panel said
"Add a free KLIPY API key in Settings to search GIFs").

## Decision

- Release builds from `main` carry one KLIPY key, taken from the repository secret `KLIPY_API_KEY`
  (Settings > Secrets and variables > Actions). It is compiled in as `BuildConfig.KLIPY_BUILT_IN_API_KEY`.
- `GifApiKey.effective()` picks the key per request: the user's saved key if any, else the built-in one.
  The GIF panel, the provider and the Settings row all use it, so a fresh install shows trending GIFs
  with no setup, and the setup dialog says the user's own key is optional.
- Without the secret (branch builds, local builds, or before David adds it) the built-in key is empty
  and the app behaves exactly as before.
- Only `[A-Za-z0-9_-]` is accepted from the secret, so it cannot break the generated Java source; any
  other value fails the build (the value is never printed) rather than shipping GIFs switched off.

## Why not fetch a key automatically

A KLIPY key needs a KLIPY Partner Panel account and acceptance of the KLIPY API Terms
(https://klipy.com/blog/klipy-partner-panel). There is no API that hands out keys, and creating an
account for David is something only he can do. So the honest automation is: David makes one key once,
puts it in the secret, and every build from then on has it.

## Limits and risks (checked 2026-11-26)

- KLIPY test keys allow about 100 calls per hour (https://klipy.com/api-overview). One person's
  typing stays well under that (each panel open or search page is one call). A production key ("Request
  Production" in the Partner Panel, reviewed by KLIPY with a screen recording) has no stated limit and
  is only needed if TypeBetter gets many users.
- The key travels in the request URL and sits inside a public APK, so anyone could extract it and use
  its quota. That is true of every GIF key shipped in an app. If it is abused, make a new key in the
  Partner Panel and replace the secret; the next build uses it. A user's own key is never affected.
- Not verified: the text of the KLIPY API Terms could not be read (the page is script-rendered). Nothing
  found forbids an embedded key; nothing found explicitly allows it either. Attribution ("Search KLIPY",
  "Powered by KLIPY") was already shown and is unchanged.
