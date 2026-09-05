#!/usr/bin/env bash
# verify.sh --fast : compile + ktlint + 단위/아키텍처 테스트 (Docker 불필요)
# verify.sh --full : + Testcontainers 통합 테스트 (Docker 필요)
set -euo pipefail
cd "$(dirname "$0")/.."
MODE="${1:---fast}"
step() { echo; echo "▶ $1"; }
step "compile";  ./gradlew -q compileKotlin compileTestKotlin
step "ktlint";   ./gradlew -q ktlintCheck || { echo "ktlint 실패 → ./gradlew ktlintFormat 으로 자동 수정 가능"; exit 1; }
if [[ "$MODE" == "--fast" ]]; then
  step "unit + arch tests"; ./gradlew -q test -PexcludeTags=integration
else
  docker info >/dev/null 2>&1 || { echo "✗ Docker 가 필요합니다 (--full)"; exit 2; }
  step "all tests (incl. Testcontainers)"; ./gradlew -q test
fi
echo; echo "✓ verify $MODE passed"
