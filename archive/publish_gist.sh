#!/usr/bin/env bash
# ينشئ Gist سرياً بكل ملفات الكود والنصوص للمشروع.
# الاستخدام: GITHUB_TOKEN=ghp_xxxx bash publish_gist.sh
set -euo pipefail
: "${GITHUB_TOKEN:?صدّر توكن أولاً: GITHUB_TOKEN=...}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY' > /tmp/gist_payload.json
import json, os
files = {}
pick = [
    "jforex/TTFMCore.java",
    "jforex/tests/TTFMCoreCoreTest.java",
    "jforex/docs/settings-guide.md",
    "jforex/docs/video-match-notes.md",
    "jforex/docs/tspot-pixel-verification.md",
    "jforex/tools/verify_tspot.py",
    "jforex/tools/measure_official_euraud2.py",
    "archive/discussion-log.md",
    "archive/PUBLISH-AR.md",
    "../ttfm-fractal-model-report.md",
]
for p in pick:
    if os.path.exists(p):
        files[os.path.basename(p)] = {"content": open(p, encoding="utf-8").read()}
print(json.dumps({
    "description": "TTFM Fractal Model - full discussion log + indicator code (archive 2026-09-08)",
    "public": False,
    "files": files,
}))
PY

RESP=$(curl -s -X POST https://api.github.com/gists \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  --data @/tmp/gist_payload.json)
echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print('GIST URL:', d.get('html_url', d))"
