#!/usr/bin/env bash
# Uploads one app bundle to a Google Play testing track through the Play Developer API (v3).
#
#   PLAY_ACCESS_TOKEN          a ready Google access token (keyless sign-in, from tools/play-wif-token.sh), or
#   PLAY_SERVICE_ACCOUNT_JSON  the service account key (JSON text, from the repository secret)
#   tools/play-upload.sh <bundle.aab> <package name> <track> <release name> <version code>
#
# Plain curl + openssl + jq, so no third-party action ever holds the Play key. Steps: sign a JWT with
# the service account's key, trade it for an access token, open an edit, upload the bundle, point the
# track at the new version code, commit the edit.
#
# Three answers from Play are handled, not failures:
#   - the version code was already uploaded (a re-run of the same build): the track is still pointed at
#     it and committed, so a run whose upload worked but whose release did not is finished by the re-run;
#   - "only releases with status draft may be created on draft app": the app has never been rolled out
#     in the Play Console yet, so the release is saved as a draft for one tap in the Console;
#   - "changes cannot be sent for review automatically": committed again with changesNotSentForReview.
#
# PLAY_API_BASE and PLAY_TOKEN_URL exist only so tools/test-play-upload.sh can run this against a local
# mock server; the workflow never sets them.
set -euo pipefail

aab=${1:?bundle path}
pkg=${2:?package name}
track=${3:?track}
name=${4:?release name}
expected_version=${5:?version code}
api=${PLAY_API_BASE:-https://androidpublisher.googleapis.com}
if [ -z "${PLAY_ACCESS_TOKEN:-}" ] && [ -z "${PLAY_SERVICE_ACCOUNT_JSON:-}" ]; then
  echo "::error::Neither PLAY_ACCESS_TOKEN nor PLAY_SERVICE_ACCOUNT_JSON is set."; exit 1
fi
[ -s "$aab" ] || { echo "::error::No bundle at $aab"; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
umask 077

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# call METHOD URL [curl args...]: prints the body; returns 1 on an HTTP error, with the body in $work/err.
call() {
  local method=$1 url=$2; shift 2
  local code
  code=$(curl -sS --max-time 1800 --retry 2 -o "$work/body" -w '%{http_code}' -X "$method" "$url" \
    -H "Authorization: Bearer $access" "$@") || { echo "network error" > "$work/err"; return 1; }
  if [ "$code" -ge 200 ] && [ "$code" -lt 300 ]; then cat "$work/body"; return 0; fi
  { echo "HTTP $code"; cat "$work/body"; } > "$work/err"
  return 1
}
fail() { echo "::error::$1"; sed 's/^/  /' "$work/err" >&2; exit 1; }

if [ -n "${PLAY_ACCESS_TOKEN:-}" ]; then
  # Keyless sign-in: the token was already made for the androidpublisher scope.
  access=$PLAY_ACCESS_TOKEN
else
  # The secret is JSON text; anything else is the most likely setup mistake, so say so plainly.
  if ! printf '%s' "$PLAY_SERVICE_ACCOUNT_JSON" | jq -e '.client_email and .private_key' > /dev/null 2>&1; then
    echo "::error::PLAY_SERVICE_ACCOUNT_JSON is not a service account key file. Paste the whole downloaded .json file as the secret value."
    exit 1
  fi
  email=$(printf '%s' "$PLAY_SERVICE_ACCOUNT_JSON" | jq -r .client_email)
  printf '%s' "$PLAY_SERVICE_ACCOUNT_JSON" | jq -r .private_key > "$work/key.pem"
  token_url=${PLAY_TOKEN_URL:-$(printf '%s' "$PLAY_SERVICE_ACCOUNT_JSON" | jq -r '.token_uri // "https://oauth2.googleapis.com/token"')}

  now=$(date +%s)
  header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
  claims=$(jq -cn --arg iss "$email" --arg aud "$token_url" --argjson iat "$now" \
    '{iss:$iss, scope:"https://www.googleapis.com/auth/androidpublisher", aud:$aud, iat:$iat, exp:($iat+3600)}' | b64url)
  signature=$(printf '%s.%s' "$header" "$claims" | openssl dgst -sha256 -sign "$work/key.pem" | b64url)
  rm -f "$work/key.pem"

  token=$(curl -sS --max-time 60 --retry 3 "$token_url" \
    --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
    --data-urlencode "assertion=$header.$claims.$signature") || token='{}'
  access=$(printf '%s' "$token" | jq -r '.access_token // empty' 2>/dev/null || true)
  if [ -z "$access" ]; then
    # Google's error text (for example invalid_grant, or a deleted key) holds no secret.
    why=$(printf '%s' "$token" | jq -r '[.error, .error_description] | map(select(.)) | join(": ")' 2>/dev/null || true)
    echo "::error::Google refused the service account key${why:+ ($why)}. Check that PLAY_SERVICE_ACCOUNT_JSON is the current key file."
    exit 1
  fi
fi
echo "::add-mask::$access"

base="$api/androidpublisher/v3/applications/$pkg"
edit=$(call POST "$base/edits" -H 'Content-Type: application/json' -d '{}' | jq -er .id) \
  || fail "Play did not open an edit for $pkg. Is the app created in the Play Console with this package name, and is the service account invited with release rights?"

if ! uploaded=$(call POST "$api/upload/androidpublisher/v3/applications/$pkg/edits/$edit/bundles?uploadType=media" \
    -H 'Content-Type: application/octet-stream' --data-binary "@$aab"); then
  if grep -qi 'version code.*already been used' "$work/err"; then
    echo "Version code $expected_version is already on Play (a re-run of the same build). Releasing it again."
    version=$expected_version
  else
    fail "Play refused the bundle."
  fi
else
  version=$(printf '%s' "$uploaded" | jq -r '.versionCode // empty')
  [ "$version" = "$expected_version" ] || { echo "::error::Play read version code '${version}' from the bundle, expected $expected_version."; exit 1; }
  echo "Uploaded version code $version."
fi

release() {
  jq -cn --arg track "$track" --arg v "$version" --arg name "$name" --arg status "$1" \
    '{track:$track, releases:[{name:$name, versionCodes:[$v], status:$status}]}' > "$work/track.json"
  call PUT "$base/edits/$edit/tracks/$track" -H 'Content-Type: application/json' --data-binary "@$work/track.json" > /dev/null \
    && commit
}
commit() {
  call POST "$base/edits/$edit:commit" > /dev/null && return 0
  grep -qi 'changesNotSentForReview' "$work/err" || return 1
  call POST "$base/edits/$edit:commit?changesNotSentForReview=true" > /dev/null
}
if release completed; then
  echo "Released $name (version code $version) to the $track track. Testers get it from the Play Store."
elif grep -qi 'status draft may be created on draft app' "$work/err"; then
  # A failed commit leaves the edit open, so the same edit can be committed as a draft.
  release draft || fail "Play refused the draft release."
  echo "::warning::Saved $name to the $track track as a DRAFT: the app has never been rolled out in the Play Console. Open Play Console > Test and release > Internal testing and tap 'Review release', then 'Start rollout'. Later builds then release on their own."
else
  fail "Play refused the $track release."
fi
