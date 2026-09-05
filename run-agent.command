#!/bin/bash
# docmind agent runner
# Claude 세션이 .agent/cmd.sh 를 쓰면 실행하고 결과를 .agent/out.log 에 기록합니다. 종료: Ctrl+C
cd "$(dirname "$0")" || exit 1
mkdir -p .agent
export LANG=ko_KR.UTF-8
echo "runner started $(date) pid=$$" > .agent/runner.status
echo "[docmind runner] 대기 중... (.agent/cmd.sh 감시)"
while true; do
  if [ -f .agent/cmd.sh ]; then
    mv .agent/cmd.sh .agent/running.sh
    echo "RUNNING $(date)" > .agent/status
    echo "[docmind runner] 실행: $(head -c 200 .agent/running.sh | tr '\n' ' ')"
    bash .agent/running.sh > .agent/out.log 2>&1
    echo "EXIT=$?" >> .agent/out.log
    echo "DONE $(date)" > .agent/status
    mv .agent/running.sh .agent/last.sh
    echo "[docmind runner] 완료 → .agent/out.log"
  fi
  sleep 2
done
