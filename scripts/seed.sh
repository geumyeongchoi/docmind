#!/usr/bin/env bash
# eval/docs/*.md 를 기동 중인 docmind 에 업로드하고 전부 DONE 될 때까지 대기한다.
# 사용: scripts/seed.sh [BASE_URL=http://localhost:8080]
set -euo pipefail
cd "$(dirname "$0")/.."
BASE="${1:-http://localhost:8080}"
args=()
for f in eval/docs/*.md; do args+=(-F "files=@$f;type=text/markdown"); done
echo "▶ upload $(ls eval/docs/*.md | wc -l | tr -d ' ') files → $BASE"
curl -sS -X POST "$BASE/api/documents" "${args[@]}" > /tmp/docmind-upload.json
python3 - <<'PY'
import json
for d in json.load(open('/tmp/docmind-upload.json'))["documents"]:
    print(f'  {d["status"]:10} {d["filename"]}')
PY
echo "▶ waiting for ingest..."
for i in $(seq 1 120); do
  curl -sS "$BASE/api/documents" > /tmp/docmind-docs.json
  pending=$(python3 -c 'import json; print(sum(1 for d in json.load(open("/tmp/docmind-docs.json")) if d["status"] in ("QUEUED","PROCESSING")))')
  [ "$pending" = "0" ] && break
  sleep 2
done
python3 - <<'PY'
import json
rows = json.load(open('/tmp/docmind-docs.json'))
tot = 0
for d in rows:
    err = f'  ! {d["error"]}' if d.get("error") else ""
    print(f'  {d["status"]:10} {d["chunkCount"]:3} chunks {str(d["elapsedMs"]):>6} ms  {d["filename"]}{err}')
    tot += d["elapsedMs"] or 0
print(f"total ingest time: {tot} ms")
PY
curl -sS "$BASE/api/health/ai"; echo
