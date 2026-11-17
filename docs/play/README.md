# TypeBetter on Google Play (private internal test)

TypeBetter goes on Google Play as an **internal test**: only the Google accounts on its tester list can
see or install it, it skips the public store, and the Play Store keeps it updated like any other app.
This is how it gets onto a phone that blocks installs from outside the Play Store.

Everything below is done on a phone browser (Chrome works; if a Play Console page looks cramped, open
Chrome's ⋮ menu and tick **Desktop site**). Steps 1 to 5 are needed once. Step 6 is optional.

## 1. Create the developer account (once, $25)

1. Open **https://play.google.com/console/signup** signed in with the Google account that should own
   the app.
2. Choose **Yourself** (a personal account), fill in the details, and pay the $25 fee.
3. Google asks for identity verification (a photo of an ID) and to verify a phone number and email.
   It can also ask you to confirm you have an Android device, through the **Play Console** app from the
   Play Store. Verification can take a few days; Google emails when it is done.

## 2. Create the app

1. In the Play Console tap **Create app**.
2. App name **TypeBetter**, language English, **App**, **Free**. Tick the two declarations and tap
   **Create app**.

## 3. Add yourself as a tester

1. Go to **Test and release → Testing → Internal testing → Testers**.
2. **Create email list**, name it `Me`, add the Gmail address signed in to the **Play Store on the
   personal side of your phone**, and save. Tick the list and tap **Save**.

## 4. Upload the first build

1. On the phone, download
   **https://github.com/davidbalasa-ui/Typing-should-not-be-hard/releases/latest/download/TypeBetter.aab**
   (well over 100 MB, best on Wi-Fi). This file is for the Play Console only; a phone cannot install it.
2. In **Internal testing** tap **Create new release**.
3. If asked about app signing, keep **Let Google manage and protect your app signing key** (the
   default). The TypeBetter release key then serves as the upload key; Google signs what phones get.
4. Under **App bundles** tap **Upload** and pick `TypeBetter.aab` from Downloads.
5. Tap **Next**, then **Save and publish** (or **Start rollout to Internal testing**). Warnings about
   an unfinished store listing should not block an internal test. If the Console insists on an App
   content form first, the answers are in [data-safety.md](data-safety.md).

## 5. Install it on your phone

1. Back on **Internal testing → Testers**, tap **Copy link** under "How testers join your test".
2. Open that link on the **personal side** of your phone, tap **Accept invite**, then **Download it on
   Google Play** and install. The first time can take up to an hour to show up.
3. Open TypeBetter; its setup screen turns the keyboard on.

## 6. Optional: automatic updates from every build

Without this, each new build is installed by repeating step 4. With it, every merge to `main` goes to
the internal test on its own and the Play Store updates the phone. It is easier on a computer.

1. Open **https://console.cloud.google.com**, create a project (any name), then search for
   **Google Play Android Developer API** and tap **Enable**.
2. Go to **IAM & Admin → Service accounts → Create service account**, name it `typebetter-upload`,
   tap **Done** (no roles needed). Open it, **Keys → Add key → Create new key → JSON**. A `.json` file
   downloads. Copy its email address (ends in `iam.gserviceaccount.com`).
3. In the Play Console: **Users and permissions → Invite new users**, paste that email, under **App
   permissions** add TypeBetter with **Release to testing tracks**, and send the invite.
4. In GitHub: the repository's **Settings → Secrets and variables → Actions → New repository secret**.
   Name `PLAY_SERVICE_ACCOUNT_JSON`, value: the whole content of the `.json` file. Never paste it into a
   chat. Delete the downloaded file afterwards.
5. The next build of `main` uploads by itself. A fresh invite can take a few hours before Google accepts
   it; until then the build log says Play refused the edit, and the next build tries again.
   The first release must already exist (step 4). If the very first release was never rolled out, the build
   saves a draft and says so in its log: open Internal testing, **Review release → Start rollout**, once.

## Good to know

- **Play and GitHub builds do not mix.** The Play copy is signed by Google, the GitHub `TypeBetter.apk`
  by the TypeBetter key, so a phone cannot update one with the other. On the work phone, use Play only.
- **Obtainium** (only if you use it elsewhere): each GitHub release now also holds `TypeBetter.aab`. If
  Obtainium asks which file to use, pick `TypeBetter.apk`.
- **What Play needs later.** A wider release (closed or production) needs the App content forms,
  including the accessibility and foreground service declarations; the answers are in
  [data-safety.md](data-safety.md). An internal test does not.
- **New personal developer accounts** must run a closed test with 12 testers for 14 days before a
  public production release. That does not affect a private internal test.
- **Privacy policy**: [privacy-policy.md](privacy-policy.md) (its GitHub link is the URL to give Play).
- **Dictate Cloud** stays in the menu but cannot sell credit in TypeBetter (its products belong to the
  original Dictate listing). Use your own provider key or on-device recognition.
