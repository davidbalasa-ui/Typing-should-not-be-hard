# TypeBetter signing key

Android installs an update only when it is signed with the same key as the app already on the phone, so
every TypeBetter build must be signed with one key that never changes.

That key is `typebetter-release.p12` in this folder. The file is encrypted: it opens only with the
password stored in the repository secret `TYPEBETTER_KEYSTORE_PASSWORD`, which nobody can read back,
not even the repository owner. The build workflow (`.github/workflows/typebetter.yml`) creates the key
and commits the encrypted file the first time it runs with the secret set, then reuses it for every
build after that.

- **Set the secret once**: repository Settings → Secrets and variables → Actions → New repository
  secret, name `TYPEBETTER_KEYSTORE_PASSWORD`, value any long password. Keep a copy somewhere safe.
- **Never change or delete the secret or the file** once a build has been installed. A new key means
  Android refuses the update, and the app has to be uninstalled (export your words first).
- Without the secret, builds still run and test, but the APK is signed with a throwaway key and is not
  published as a release.
