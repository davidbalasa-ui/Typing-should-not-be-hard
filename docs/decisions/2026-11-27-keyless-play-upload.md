# Play uploads sign in without a key file (Workload Identity Federation)

Date: 2026-11-27. David's Cloud project refused to create a service account key (org policy
`iam.disableServiceAccountKeyCreation`, applied by Google's Secure by Default), and he chose the
keyless route.

## Decision

- The `play` job (main pushes and manual runs of main only, signed builds only) gets
  `permissions: id-token: write`. `tools/play-wif-token.sh` asks GitHub for the run's signed identity
  token, trades it at Google STS through pool `github` / provider `github`, then borrows the
  `typebetter-upload` service account for one hour with only the `androidpublisher` scope. The token
  goes to `tools/play-upload.sh` as `PLAY_ACCESS_TOKEN`; the upload steps are unchanged.
- Two repository **variables** (not secrets): `PLAY_GCP_PROJECT_NUMBER`, `PLAY_SERVICE_ACCOUNT`.
  The pool and provider ids default to `github`.
- Google only accepts the identity from this repository (provider condition on
  `assertion.repository`, and the Workload Identity User grant is on that repository's principal set).
  Only main builds run the job, as before.
- Plain curl + jq, no third-party action, matching the existing upload script (no outside code ever
  holds a Play credential). Every token is masked in the log; Google's error text is shown with a
  plain line naming the console setting to check.
- Kept, not removed: the `PLAY_SERVICE_ACCOUNT_JSON` key-file route still works when the two
  variables are not set.
- Tested in `tools/test-play-upload.sh` against a local mock of GitHub's token endpoint, STS and the
  IAM Credentials API: request shapes, masking, and three refusals with plain messages.
