#!/usr/bin/env bash
# Prints a short-lived Google access token for the Play Developer API, with no key file (keyless sign-in,
# "Workload Identity Federation"). Used because Google blocks service account key files on David's
# Cloud project (org policy iam.disableServiceAccountKeyCreation).
#
#   PLAY_GCP_PROJECT_NUMBER  the Cloud project number (repository variable, not a secret)
#   PLAY_SERVICE_ACCOUNT     the service account email, typebetter-upload@<project>.iam.gserviceaccount.com
#   tools/play-wif-token.sh > token
#
# Steps, all plain curl + jq: ask GitHub Actions for this run's signed identity token (needs the job
# permission id-token: write), trade it at Google's Security Token Service for a federated token through
# the pool "github" / provider "github" (which only accepts this repository), then use that to borrow
# the service account for one hour with only the androidpublisher scope.
#
# PLAY_STS_URL and PLAY_IAMCREDENTIALS_BASE exist only so tools/test-play-upload.sh can run this against
# a local mock; the workflow never sets them. Nothing printed to the log holds a token.
set -euo pipefail

: "${PLAY_GCP_PROJECT_NUMBER:?PLAY_GCP_PROJECT_NUMBER is not set}"
: "${PLAY_SERVICE_ACCOUNT:?PLAY_SERVICE_ACCOUNT is not set}"
pool=${PLAY_WIF_POOL:-github}
provider_id=${PLAY_WIF_PROVIDER:-github}
sts=${PLAY_STS_URL:-https://sts.googleapis.com/v1/token}
iamc=${PLAY_IAMCREDENTIALS_BASE:-https://iamcredentials.googleapis.com}

case "$PLAY_GCP_PROJECT_NUMBER" in
  *[!0-9]*|'') echo "::error::PLAY_GCP_PROJECT_NUMBER must be the project NUMBER (digits only, from the Cloud console's project info card), not the project name or id." >&2; exit 1 ;;
esac
case "$PLAY_SERVICE_ACCOUNT" in
  *@*.iam.gserviceaccount.com) ;;
  *) echo "::error::PLAY_SERVICE_ACCOUNT must be the service account email, ending in .iam.gserviceaccount.com." >&2; exit 1 ;;
esac
if [ -z "${ACTIONS_ID_TOKEN_REQUEST_URL:-}" ] || [ -z "${ACTIONS_ID_TOKEN_REQUEST_TOKEN:-}" ]; then
  echo "::error::GitHub gave this job no identity token. The job needs 'permissions: id-token: write'." >&2
  exit 1
fi

provider="projects/$PLAY_GCP_PROJECT_NUMBER/locations/global/workloadIdentityPools/$pool/providers/$provider_id"
audience="https://iam.googleapis.com/$provider"

# Google's error text never holds a token, so it is safe to show.
why() { jq -r '[.error, .error_description, .error.message?] | map(select(type=="string")) | join(": ")' 2>/dev/null || true; }

gh_jwt=$(curl -sS --max-time 60 --retry 3 -G "$ACTIONS_ID_TOKEN_REQUEST_URL" \
  --data-urlencode "audience=$audience" -H "Authorization: bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN" | jq -r '.value // empty')
[ -n "$gh_jwt" ] || { echo "::error::GitHub did not hand out an identity token for this run." >&2; exit 1; }
echo "::add-mask::$gh_jwt" >&2

resp=$(jq -n --arg aud "//iam.googleapis.com/$provider" --arg t "$gh_jwt" '{
  grantType: "urn:ietf:params:oauth:grant-type:token-exchange",
  audience: $aud,
  scope: "https://www.googleapis.com/auth/cloud-platform",
  requestedTokenType: "urn:ietf:params:oauth:token-type:access_token",
  subjectTokenType: "urn:ietf:params:oauth:token-type:jwt",
  subjectToken: $t}' | curl -sS --max-time 60 --retry 3 "$sts" -H 'Content-Type: application/json' --data-binary @-) || resp='{}'
federated=$(printf '%s' "$resp" | jq -r '.access_token // empty' 2>/dev/null || true)
if [ -z "$federated" ]; then
  echo "::error::Google did not accept this run's GitHub identity ($(printf '%s' "$resp" | why)). Check that the Cloud project number is right, the pool and provider are both called 'github', and the provider's condition names davidbalasa-ui/Typing-should-not-be-hard." >&2
  exit 1
fi
echo "::add-mask::$federated" >&2

resp=$(curl -sS --max-time 60 --retry 3 \
  "$iamc/v1/projects/-/serviceAccounts/$PLAY_SERVICE_ACCOUNT:generateAccessToken" \
  -H "Authorization: Bearer $federated" -H 'Content-Type: application/json' \
  -d '{"scope":["https://www.googleapis.com/auth/androidpublisher"],"lifetime":"3600s"}') || resp='{}'
access=$(printf '%s' "$resp" | jq -r '.accessToken // empty' 2>/dev/null || true)
if [ -z "$access" ]; then
  echo "::error::Google would not let this repository act as $PLAY_SERVICE_ACCOUNT ($(printf '%s' "$resp" | why)). On that service account, the principal set for repository davidbalasa-ui/Typing-should-not-be-hard needs the role Workload Identity User. A new grant can take a few minutes to work." >&2
  exit 1
fi
echo "::add-mask::$access" >&2
printf '%s\n' "$access"
