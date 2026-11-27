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
the internal test on its own and the Play Store updates the phone. There is no key file: Google blocks
service account keys on new Cloud projects, so the build signs in with its own GitHub identity
("Workload Identity Federation"), which Google accepts only from this repository's `main` builds.

1. Open **https://console.cloud.google.com**, pick or create a project, search for
   **Google Play Android Developer API** and tap **Enable**. Note the project **number** (digits, on
   the Dashboard's project info card).
2. **IAM & Admin → Service accounts → Create service account**, name it `typebetter-upload`, tap
   **Done** (no roles). Copy its email (ends in `iam.gserviceaccount.com`).
3. **IAM & Admin → Workload Identity Federation → Create pool**: name and id `github`. Add a provider:
   **OpenID Connect (OIDC)**, id `github`, issuer `https://token.actions.githubusercontent.com`,
   audiences left on **Default audience**. Attribute mapping: `google.subject` = `assertion.sub` and
   `attribute.repository` = `assertion.repository`. Attribute condition:
   `assertion.repository=="davidbalasa-ui/Typing-should-not-be-hard"`. Save.
4. Open the `typebetter-upload` service account → **Principals with access → Grant access**. Principal:
   `principalSet://iam.googleapis.com/projects/<project number>/locations/global/workloadIdentityPools/github/attribute.repository/davidbalasa-ui/Typing-should-not-be-hard`,
   role **Workload Identity User**. Save.
5. In the Play Console: **Users and permissions → Invite new users**, paste the service account email,
   under **App permissions** add TypeBetter with **Release to testing tracks**, and send the invite.
6. In GitHub: the repository's **Settings → Secrets and variables → Actions → Variables → New repository
   variable**, twice: `PLAY_GCP_PROJECT_NUMBER` = the project number, `PLAY_SERVICE_ACCOUNT` = the
   service account email. Neither is a secret.
7. The next build of `main` uploads by itself. A fresh invite can take a few hours before Google accepts
   it; until then the build log says Play refused the edit, and the next build tries again.
   The first release must already exist (step 4). If the very first release was never rolled out, the build
   saves a draft and says so in its log: open Internal testing, **Review release → Start rollout**, once.

The older key-file route still works if a `PLAY_SERVICE_ACCOUNT_JSON` secret is ever set and the two
variables are not.

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
- **App icon for the store listing**: when Play asks for a 512 × 512 app icon (Grow → Store presence →
  Main store listing), open
  **https://github.com/davidbalasa-ui/Typing-should-not-be-hard/raw/main/app/src/main/ic_app_icon-playstore.png**
  on the phone, save it, and upload it. It is the same logo as the app's launcher icon.
- **Privacy policy**: [privacy-policy.md](privacy-policy.md) (its GitHub link is the URL to give Play).
- **Dictate Cloud** stays in the menu but cannot sell credit in TypeBetter (its products belong to the
  original Dictate listing). Use your own provider key or on-device recognition.
