# TypeBetter signing key

Android installs an update only when it is signed with the same key as the app already on the phone, so
every TypeBetter build must be signed with one key that never changes.

That key is `typebetter-release.p12` in this folder, created by the first build of `main` that runs with
the repository secret `TYPEBETTER_KEYSTORE_PASSWORD` set. This repository is public, so anyone can
download that file. What protects it:

- The file is encrypted (AES-256, 1,000,000 PBKDF2 rounds) with a password that is **not** the secret
  itself but the secret run through scrypt (about one second and 1 GiB of memory per guess). Guessing a
  long secret offline is impractical.
- The build refuses a secret shorter than 16 characters.
- Only `main` builds ever receive the secret. Work branches and pull requests are built with a throwaway
  key and never published, so unreviewed code never holds the key and never reaches the phone.

What to do:

- **Set the secret once**: repository Settings → Secrets and variables → Actions → New repository
  secret, name `TYPEBETTER_KEYSTORE_PASSWORD`, value a long password: four or more random words, or 20+
  random characters (a password manager's generator is ideal). Keep a copy somewhere safe.
- **Never change or delete the secret or the file** once a build has been installed. A new key means
  Android refuses the update, and the app has to be uninstalled (export your words first). If the secret
  no longer opens the file, the build stops with an error instead of publishing.
- Without the secret, builds still run and test, but the APK is signed with a throwaway key and is not
  published as a release.
