#!/usr/bin/env bash
# Runs tools/play-upload.sh against a local mock of Google's token endpoint and the Play Developer API.
# Checks: the JWT is signed with the service account key and asks for the androidpublisher scope; the
# bundle bytes arrive unchanged; the track gets the uploaded version code; and the three outcomes
# (normal app, never-rolled-out "draft" app, re-run of an already uploaded version code) all end right.
# Also checks a broken secret fails with a plain message, and the keyless route (tools/play-wif-token.sh):
# GitHub identity token -> Google STS -> service account access token -> the same upload.
# Needs python3, openssl, jq, curl.
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
T=$(mktemp -d)
pid=
trap '[ -n "$pid" ] && kill "$pid" 2>/dev/null; rm -rf "$T"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$T/sa.pem" 2>/dev/null
openssl pkey -in "$T/sa.pem" -pubout -out "$T/sa.pub"
head -c 300000 /dev/urandom > "$T/app.aab"

cat > "$T/mock.py" <<'PY'
import base64, json, os, subprocess, sys, urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer
T = sys.argv[1]
def b64d(s): return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))
def log(e):
    with open(os.path.join(T, "log.jsonl"), "a") as f: f.write(json.dumps(e) + "\n")
def mode(): return open(os.path.join(T, "mode")).read().strip()
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def body(self): return self.rfile.read(int(self.headers.get("Content-Length", 0)))
    def authed(self):
        if self.headers.get("Authorization") != "Bearer tok-123":
            self.send(401, {"error": "bad token"}); return False
        return True
    def do_GET(self):
        if self.path.startswith("/oidc?"):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            log({"step": "oidc", "audience": q.get("audience", [""])[0], "auth": self.headers.get("Authorization")})
            return self.send(200, {"value": "gh-jwt-1"})
        self.send(404, {})
    def do_POST(self):
        p = self.path; data = self.body()
        if p == "/sts":
            f = json.loads(data)
            log({"step": "sts", "body": f})
            if mode() == "sts-refuse" or f.get("subjectToken") != "gh-jwt-1":
                return self.send(400, {"error": "invalid_grant", "error_description": "The given credential is rejected by the attribute condition."})
            return self.send(200, {"access_token": "fed-1", "token_type": "Bearer"})
        if p.startswith("/v1/projects/-/serviceAccounts/") and p.endswith(":generateAccessToken"):
            log({"step": "impersonate", "path": p, "auth": self.headers.get("Authorization"), "body": json.loads(data)})
            if mode() == "sa-refuse" or self.headers.get("Authorization") != "Bearer fed-1":
                return self.send(403, {"error": {"code": 403, "message": "Permission 'iam.serviceAccounts.getAccessToken' denied", "status": "PERMISSION_DENIED"}})
            return self.send(200, {"accessToken": "tok-123", "expireTime": "2099-01-01T00:00:00Z"})
        if p == "/token":
            f = urllib.parse.parse_qs(data.decode())
            h, c, s = f["assertion"][0].split(".")
            open(os.path.join(T, "signed"), "wb").write(f"{h}.{c}".encode())
            open(os.path.join(T, "sig"), "wb").write(b64d(s))
            ok = subprocess.run(["openssl", "dgst", "-sha256", "-verify", os.path.join(T, "sa.pub"),
                                 "-signature", os.path.join(T, "sig"), os.path.join(T, "signed")],
                                capture_output=True).returncode == 0
            claims = json.loads(b64d(c))
            log({"step": "token", "sig_ok": ok, "claims": claims, "grant": f["grant_type"][0],
                 "type": self.headers.get("Content-Type")})
            return self.send(200 if ok else 400, {"access_token": "tok-123"} if ok else {"error": "invalid_grant"})
        if not self.authed(): return
        if p.endswith("/edits"):
            log({"step": "edit"}); return self.send(200, {"id": "e1"})
        if p == "/upload/androidpublisher/v3/applications/com.poeticsquirrel.typebetter/edits/e1/bundles?uploadType=media":
            same = data == open(os.path.join(T, "app.aab"), "rb").read()
            log({"step": "upload", "same_bytes": same, "type": self.headers.get("Content-Type")})
            if mode() == "duplicate":
                return self.send(403, {"error": {"message": "APK specifies a version code that has already been used."}})
            return self.send(200, {"versionCode": 142031})
        if p.endswith(":commit") or p.endswith(":commit?changesNotSentForReview=true"):
            status = json.load(open(os.path.join(T, "track.json")))["releases"][0]["status"]
            log({"step": "commit", "status": status, "path": p})
            if mode() == "review" and "changesNotSentForReview" not in p:
                return self.send(400, {"error": {"message": "Changes cannot be sent for review automatically. Please set the query parameter changesNotSentForReview to true."}})
            if mode() == "draft" and status != "draft":
                return self.send(400, {"error": {"message": "Only releases with status draft may be created on draft app."}})
            return self.send(200, {"id": "e1"})
        self.send(404, {})
    def do_PUT(self):
        if not self.authed(): return
        data = self.body()
        if mode() == "draft-at-track" and json.loads(data)["releases"][0]["status"] != "draft":
            log({"step": "track-refused"})
            return self.send(400, {"error": {"message": "Only releases with status draft may be created on draft app."}})
        open(os.path.join(T, "track.json"), "wb").write(data)
        log({"step": "track", "path": self.path, "body": json.loads(data)}); self.send(200, json.loads(data))
    def do_DELETE(self):
        if not self.authed(): return
        log({"step": "delete"}); self.send(204, {})
srv = HTTPServer(("127.0.0.1", 0), H)
open(os.path.join(T, "port"), "w").write(str(srv.server_port))
srv.serve_forever()
PY
echo normal > "$T/mode"
python3 "$T/mock.py" "$T" & pid=$!
for _ in $(seq 50); do [ -s "$T/port" ] && break; sleep 0.1; done
port=$(cat "$T/port")
export PLAY_API_BASE="http://127.0.0.1:$port" PLAY_TOKEN_URL="http://127.0.0.1:$port/token"
export PLAY_SERVICE_ACCOUNT_JSON
PLAY_SERVICE_ACCOUNT_JSON=$(jq -n --rawfile k "$T/sa.pem" '{type:"service_account", client_email:"ci@p.iam.gserviceaccount.com", private_key:$k}')
up() { bash "$repo/tools/play-upload.sh" "$T/app.aab" com.poeticsquirrel.typebetter internal "Build 31" 142031 > "$T/out" 2>&1; }
check() { local a=(); [ "$1" = --arg ] && { a=(--arg "$2" "$3"); shift 3; }
  jq -se ${a[@]+"${a[@]}"} "$1" "$T/log.jsonl" > /dev/null || { echo "FAIL: $2"; cat "$T/out"; cat "$T/log.jsonl"; exit 1; }; }

rm -f "$T/log.jsonl"; up || { echo "FAIL: normal upload exited non-zero"; cat "$T/out"; exit 1; }
check '.[0].type=="application/x-www-form-urlencoded" and .[0].claims.aud==env.PLAY_TOKEN_URL and .[0].sig_ok and .[0].claims.scope=="https://www.googleapis.com/auth/androidpublisher" and .[0].claims.iss=="ci@p.iam.gserviceaccount.com" and (.[0].claims.exp-.[0].claims.iat)==3600 and .[0].grant=="urn:ietf:params:oauth:grant-type:jwt-bearer"' "JWT is not a valid signed service-account assertion"
check 'map(select(.step=="upload"))[0] | .same_bytes and .type=="application/octet-stream"' "bundle bytes changed on the way"
check 'map(select(.step=="track"))[0] | .path=="/androidpublisher/v3/applications/com.poeticsquirrel.typebetter/edits/e1/tracks/internal" and .body.releases[0].versionCodes==["142031"] and .body.releases[0].status=="completed" and .body.releases[0].name=="Build 31"' "track not set to the uploaded version"
check 'map(select(.step=="commit")) | length==1 and .[0].status=="completed"' "edit not committed as a completed release"
echo "OK: normal app: signed JWT, bundle uploaded unchanged, internal track released"

echo draft > "$T/mode"; rm -f "$T/log.jsonl"
up || { echo "FAIL: draft-app upload exited non-zero"; cat "$T/out"; exit 1; }
check 'map(select(.step=="commit")) | length==2 and .[1].status=="draft"' "never-rolled-out app did not fall back to a draft release"
grep -q "DRAFT" "$T/out" || { echo "FAIL: no draft warning"; cat "$T/out"; exit 1; }
echo "OK: never-rolled-out app: saved as a draft, with the one-tap instruction"

echo draft-at-track > "$T/mode"; rm -f "$T/log.jsonl"
up || { echo "FAIL: draft refusal at the track step exited non-zero"; cat "$T/out"; exit 1; }
check 'map(select(.step=="track"))[0].body.releases[0].status=="draft" and (map(select(.step=="commit"))|length==1)' "draft refusal at the track step not handled"
echo "OK: draft refusal at the track step: saved as a draft"

echo review > "$T/mode"; rm -f "$T/log.jsonl"
up || { echo "FAIL: 'cannot be sent for review' exited non-zero"; cat "$T/out"; exit 1; }
check 'map(select(.step=="commit")) | length==2 and (.[1].path|endswith("changesNotSentForReview=true"))' "commit not retried with changesNotSentForReview"
grep -q "Send changes for review" "$T/out" || { echo "FAIL: a release held for review gave no warning"; cat "$T/out"; exit 1; }
echo "OK: 'cannot be sent for review automatically': committed with changesNotSentForReview, and says to send it"

echo duplicate > "$T/mode"; rm -f "$T/log.jsonl"
up || { echo "FAIL: re-run of an uploaded version code exited non-zero"; cat "$T/out"; exit 1; }
check 'map(select(.step=="track"))[0].body.releases[0].versionCodes==["142031"] and (map(select(.step=="commit"))|length==1)' "re-run did not release the already uploaded version code"
echo "OK: re-run of an uploaded version code: the track is released with it again"

echo normal > "$T/mode"
if PLAY_SERVICE_ACCOUNT_JSON='not json' up; then echo "FAIL: a broken secret was accepted"; exit 1; fi
grep -q "not a service account key file" "$T/out" || { echo "FAIL: broken secret gave no plain message"; cat "$T/out"; exit 1; }
echo "OK: a broken secret fails with a plain message"

# Keyless route: no key file, a GitHub identity traded for a service account token.
echo normal > "$T/mode"; rm -f "$T/log.jsonl"
wif() {
  env -u PLAY_SERVICE_ACCOUNT_JSON ACTIONS_ID_TOKEN_REQUEST_URL="http://127.0.0.1:$port/oidc?x=1" \
    ACTIONS_ID_TOKEN_REQUEST_TOKEN=req-1 PLAY_STS_URL="http://127.0.0.1:$port/sts" \
    PLAY_IAMCREDENTIALS_BASE="http://127.0.0.1:$port" PLAY_GCP_PROJECT_NUMBER="${1:-123456789012}" PLAY_WIF_POOL="${3:-}" PLAY_WIF_PROVIDER="${4:-}" \
    PLAY_SERVICE_ACCOUNT="${2:-typebetter-upload@p.iam.gserviceaccount.com}" \
    bash "$repo/tools/play-wif-token.sh" > "$T/tok" 2> "$T/out"
}
wif || { echo "FAIL: keyless token exited non-zero"; cat "$T/out"; exit 1; }
[ "$(cat "$T/tok")" = tok-123 ] || { echo "FAIL: keyless token printed the wrong thing"; cat "$T/tok"; exit 1; }
grep -E 'tok-123|fed-1|gh-jwt-1' "$T/out" | grep -qv '^::add-mask::' && { echo "FAIL: a token reached the log without a mask"; cat "$T/out"; exit 1; }
for t in gh-jwt-1 fed-1 tok-123; do grep -qx "::add-mask::$t" "$T/out" || { echo "FAIL: $t not masked"; exit 1; }; done
prov=projects/123456789012/locations/global/workloadIdentityPools/github/providers/github
check --arg p "$prov" '.[0].step=="oidc" and .[0].audience==("https://iam.googleapis.com/"+$p) and .[0].auth=="bearer req-1"' "GitHub identity token not asked for the provider's audience"
check --arg p "$prov" 'map(select(.step=="sts"))[0].body | .audience==("//iam.googleapis.com/"+$p) and .subjectToken=="gh-jwt-1" and .grantType=="urn:ietf:params:oauth:grant-type:token-exchange" and .subjectTokenType=="urn:ietf:params:oauth:token-type:jwt"' "STS exchange malformed"
check 'map(select(.step=="impersonate"))[0] | .path=="/v1/projects/-/serviceAccounts/typebetter-upload@p.iam.gserviceaccount.com:generateAccessToken" and .auth=="Bearer fed-1" and .body.scope==["https://www.googleapis.com/auth/androidpublisher"]' "service account not borrowed with the Play scope only"
rm -f "$T/log.jsonl"
PLAY_ACCESS_TOKEN=$(cat "$T/tok") PLAY_SERVICE_ACCOUNT_JSON= up || { echo "FAIL: upload with a keyless token exited non-zero"; cat "$T/out"; exit 1; }
check '(map(select(.step=="token"))|length==0) and (map(select(.step=="commit"))|length==1) and .[0].step=="edit"' "keyless upload still asked for a key-file token"
echo "OK: keyless route: GitHub identity -> Google STS -> service account token, then the same upload"

echo sts-refuse > "$T/mode"
if wif; then echo "FAIL: a refused GitHub identity was accepted"; exit 1; fi
grep -q "did not accept this run's GitHub identity (invalid_grant" "$T/out" || { echo "FAIL: STS refusal gave no plain message"; cat "$T/out"; exit 1; }
echo sa-refuse > "$T/mode"
if wif; then echo "FAIL: a refused service account was accepted"; exit 1; fi
grep -q "Workload Identity User" "$T/out" || { echo "FAIL: impersonation refusal gave no plain message"; cat "$T/out"; exit 1; }
echo normal > "$T/mode"
if wif my-project-id; then echo "FAIL: a project id was accepted as the number"; exit 1; fi
grep -q "project NUMBER" "$T/out" || { echo "FAIL: bad project number gave no plain message"; cat "$T/out"; exit 1; }
rm -f "$T/log.jsonl"
wif " 123456789012
" " typebetter-upload@p.iam.gserviceaccount.com " gh-pool gh-prov || { echo "FAIL: pasted spaces or custom pool ids broke the keyless token"; cat "$T/out"; exit 1; }
check '.[0].audience=="https://iam.googleapis.com/projects/123456789012/locations/global/workloadIdentityPools/gh-pool/providers/gh-prov"' "custom pool/provider ids or trimmed number not used"
check 'map(select(.step=="impersonate"))[0].path=="/v1/projects/-/serviceAccounts/typebetter-upload@p.iam.gserviceaccount.com:generateAccessToken"' "service account email not trimmed"
if env -u PLAY_SERVICE_ACCOUNT_JSON ACTIONS_ID_TOKEN_REQUEST_URL="http://127.0.0.1:1/oidc?x=1" ACTIONS_ID_TOKEN_REQUEST_TOKEN=r \
    PLAY_GCP_PROJECT_NUMBER=1 PLAY_SERVICE_ACCOUNT=a@p.iam.gserviceaccount.com bash "$repo/tools/play-wif-token.sh" > /dev/null 2> "$T/out"; then
  echo "FAIL: unreachable GitHub token endpoint was accepted"; exit 1; fi
grep -q "::error::GitHub did not hand out an identity token" "$T/out" || { echo "FAIL: unreachable GitHub token endpoint gave no plain message"; cat "$T/out"; exit 1; }
echo "OK: keyless refusals (identity, service account, project number, no GitHub token) fail with plain messages; pasted spaces and custom pool ids work"
