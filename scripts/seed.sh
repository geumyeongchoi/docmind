#!/usr/bin/env bash
# eval/docs/*.md 를 기동 중인 docmind 에 업로드하고 전부 DONE 될 때까지 대기한다.
# 사용: scripts/seed.sh [BASE_URL=http://localhost:8080]
set -euo pipefail
cd "$(dirname "$0")/.."
BASE="${1:-http://localhost:8080}"
args=()
for f in eval/docs/*.md; do args+=(-F "files=@$f;type=text/markdown"); done
echo "▶ upload $(ls eval/docs/*.md | wc -l | tr -d ' ') files → $BASE"
curl -sS -X POST "$BASE/api/documents" "${args[@]}" | python3 -c 'import sys,json; [print(f" {d[\"status\"]:10} {d[\"filename\"]}") for d in json.load(sys.stdin)["documents"]]'
echo "▶ waiting for ingest..."
for i in $(seq 1 120); do
  pending=$(curl -sS "$BASE/api/documents" | python3 -c 'import sys,json; print(sum(1 for d in json.load(sys.stdin) if d["status"] in ("QUEUED","PROCESSING")))')
  [ "$pending" = "0" ] && break
  sleep 2
done
curl -sS "$BASE/api/documents" | python3 -c 'import sys,json
rows=json.load(sys.stdin)
tot=0
for d in rows:
    print(f" {d[\"status\"]:10} {d[\"chunkCount\"]:3} chunks {str(d[\"elapsedMs\"]):>6} ms  {d[\"filename\"]}" + (f"  ! {d[\"error\"]}" if d.get("error") else ""))
    tot+= d["elapsedMs"] or 0
print(f"total ingest time: {tot} ms")'
curl -sS "$BASE/api/health/ai"; echo
