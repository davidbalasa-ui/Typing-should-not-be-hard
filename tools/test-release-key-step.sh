#!/usr/bin/env bash
# Proves the workflow's "Signing setup" step never replaces the release key on main.
# Runs the real step from .github/workflows/typebetter.yml against a local bare repository:
#   1. a first main build makes the key and saves it to main;
#   2. a second build of an OLDER main commit (a re-run, or a push queued behind the first) runs next.
# The key on main must be byte-for-byte the same afterwards. Needs git, python3 + PyYAML, keytool.
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
python3 - "$repo/.github/workflows/typebetter.yml" "$T/signing.sh" <<'PY'
import sys, yaml
w = yaml.safe_load(open(sys.argv[1]))
run = [s for s in w["jobs"]["apk"]["steps"] if s.get("id") == "signing"][0]["run"]
remote = 'remote="https://x-access-token:${PUSH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"'
assert remote in run, "signing step no longer builds the remote URL the way this test expects"
run = run.replace(remote, 'remote="$TEST_REMOTE"').replace("sleep $((attempt * 10))", "true")
open(sys.argv[2], "w").write(run)
PY
cd "$T"
git init -q --bare origin.git
git clone -q origin.git seed 2>/dev/null
(cd seed && git -c user.name=t -c user.email=t@t commit -q --allow-empty -m first && git push -q origin HEAD:main)
old=$(git --git-dir=origin.git rev-parse main)
export SECRET=test-secret-long-enough-for-the-check REF=main PUSH_TOKEN=unused TEST_REMOTE="$T/origin.git" \
  GITHUB_OUTPUT=/dev/null RUNNER_TEMP="$T"
build() { rm -rf "$1"; git clone -q origin.git "$1" 2>/dev/null; (cd "$1" && git checkout -q "$old" && bash "$T/signing.sh" > /dev/null); }
build first
k1=$(git --git-dir=origin.git rev-parse main:signing/typebetter-release.p12)
build stale
k2=$(git --git-dir=origin.git rev-parse main:signing/typebetter-release.p12)
cmp -s first/signing/typebetter-release.p12 stale/signing/typebetter-release.p12 || { echo "FAIL: the stale build signed with a different key"; exit 1; }
[ "$k1" = "$k2" ] || { echo "FAIL: the release key on main was replaced"; exit 1; }
echo "OK: the stale build reused the key on main and did not replace it"
