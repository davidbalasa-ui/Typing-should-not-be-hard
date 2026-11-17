#!/usr/bin/env bash
# Runs tools/play-upload.sh against a local mock of Google's token endpoint and the Play Developer API.
# Checks: the JWT is signed with the service account key and asks for the androidpublisher scope; the
# bundle bytes arrive unchanged; the track gets the uploaded version code; and the three outcomes
# (normal app, never-rolled-out "draft" app, re-run of an already uploaded version code) all end right.
# Also checks a broken secret fails with a plain message. Needs python3, openssl, jq, curl.
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
    def do_POST(self):
        p = self.path; data = self.body()
        if p == "/token":
            f = urllib.parse.parse_qs(data.decode())
            h, c, s = f["assertion"][0].split(".")
            open(os.path.join(T, "signed"), "wb").write(f"{h}.{c}".encode())
            open(os.path.join(T, "sig"), "wb").write(b64d(s))
            ok = subprocess.run(["openssl", "dgst", "-sha256", "-verify", os.path.join(T, "sa.pub"),
                                 "-signature", os.path.join(T, "sig"), os.path.join(T, "signed")],
                                capture_output=True).returncode == 0
            claims = json.loads(b64d(c))
            log({"step": "token", "sig_ok": ok, "claims": claims, "grant": f["grant_type"][0]})
            return self.send(200 if ok else 400, {"access_token": "tok-123"} if ok else {"error": "invalid_grant"})
        if not self.authed(): return
        if p.endswith("/edits"):
            log({"step": "edit"}); return self.send(200, {"id": "e1"})
        if "/bundles?uploadType=media" in p:
            same = data == open(os.path.join(T, "app.aab"), "rb").read()
            log({"step": "upload", "same_bytes": same, "type": self.headers.get("Content-Type")})
            if mode() == "duplicate":
                return self.send(403, {"error": {"message": "APK specifies a version code that has already been used."}})
            return self.send(200, {"versionCode": 142031})
        if p.endswith(":commit"):
            status = json.load(open(os.path.join(T, "track.json")))["releases"][0]["status"]
            log({"step": "commit", "status": status})
            if mode() == "draft" and status != "draft":
                return self.send(400, {"error": {"message": "Only releases with status draft may be created on draft app."}})
            return self.send(200, {"id": "e1"})
        self.send(404, {})
    def do_PUT(self):
        if not self.authed(): return
        data = self.body(); open(os.path.join(T, "track.json"), "wb").write(data)
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
up() { bash "$repo/tools/play-upload.sh" "$T/app.aab" com.typebetter.keyboard internal "Build 31" > "$T/out" 2>&1; }
check() { jq -se "$1" "$T/log.jsonl" > /dev/null || { echo "FAIL: $2"; cat "$T/out"; cat "$T/log.jsonl"; exit 1; }; }

rm -f "$T/log.jsonl"; up || { echo "FAIL: normal upload exited non-zero"; cat "$T/out"; exit 1; }
check '.[0].sig_ok and .[0].claims.scope=="https://www.googleapis.com/auth/androidpublisher" and .[0].claims.iss=="ci@p.iam.gserviceaccount.com" and (.[0].claims.exp-.[0].claims.iat)==3600 and .[0].grant=="urn:ietf:params:oauth:grant-type:jwt-bearer"' "JWT is not a valid signed service-account assertion"
check 'map(select(.step=="upload"))[0] | .same_bytes and .type=="application/octet-stream"' "bundle bytes changed on the way"
check 'map(select(.step=="track"))[0] | .path=="/androidpublisher/v3/applications/com.typebetter.keyboard/edits/e1/tracks/internal" and .body.releases[0].versionCodes==["142031"] and .body.releases[0].status=="completed" and .body.releases[0].name=="Build 31"' "track not set to the uploaded version"
check 'map(select(.step=="commit")) | length==1 and .[0].status=="completed"' "edit not committed as a completed release"
echo "OK: normal app: signed JWT, bundle uploaded unchanged, internal track released"

echo draft > "$T/mode"; rm -f "$T/log.jsonl"
up || { echo "FAIL: draft-app upload exited non-zero"; cat "$T/out"; exit 1; }
check 'map(select(.step=="commit")) | length==2 and .[1].status=="draft"' "never-rolled-out app did not fall back to a draft release"
grep -q "DRAFT" "$T/out" || { echo "FAIL: no draft warning"; cat "$T/out"; exit 1; }
echo "OK: never-rolled-out app: saved as a draft, with the one-tap instruction"

echo duplicate > "$T/mode"; rm -f "$T/log.jsonl"
up || { echo "FAIL: re-run of an uploaded version code exited non-zero"; cat "$T/out"; exit 1; }
check 'map(.step) | (index("track")==null) and (index("commit")==null) and (index("delete")!=null)' "re-run touched the track or left the edit open"
echo "OK: re-run of an uploaded version code: no change, exits cleanly"

echo normal > "$T/mode"
if PLAY_SERVICE_ACCOUNT_JSON='not json' up; then echo "FAIL: a broken secret was accepted"; exit 1; fi
grep -q "not a service account key file" "$T/out" || { echo "FAIL: broken secret gave no plain message"; cat "$T/out"; exit 1; }
echo "OK: a broken secret fails with a plain message"
