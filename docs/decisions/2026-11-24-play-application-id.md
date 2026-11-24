# Play application ID: com.poeticsquirrel.typebetter (2026-11-24)

- Play Console reported `com.typebetter.keyboard` as already in use, so David chose
  `com.poeticsquirrel.typebetter` for the Play listing.
- Changed: release `applicationId` (app and wear, which must match), the Play upload step in
  `.github/workflows/typebetter.yml`, its test `tools/test-play-upload.sh`, and the privacy policy.
  Debug/beta/bench keep their `.debug`/`.beta`/`.bench` suffixes on the new id.
- Unchanged on purpose: Gradle `namespace`, the Kotlin package `com.typebetter.keyboard` (code
  packages are independent of the app id), IME/service class names, keystore and signing.
- Restore accepts backups from both ids, so backups made by earlier sideloaded builds restore
  without a "different vendor" warning. The new id installs as a separate app; move My Words with
  Backup in the old app and Restore in the new one.
