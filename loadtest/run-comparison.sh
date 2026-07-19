#!/usr/bin/env bash
# loadtest/run-comparison.sh
# 락 3종(pessimistic/optimistic/redis) × 스레드 모델 2종(platform/virtual) = 6개 조합을
# 순회하며 앱을 재기동하고 핫존 부하테스트를 실행한다. 매 조합 전 기사 상태를 리셋해야
# 이전 조합의 잔여 배정이 다음 조합 결과를 오염시키지 않는다.
set -euo pipefail

STRATEGIES=(pessimistic optimistic redis)
VT_OPTIONS=(false true)
DURATION="${DURATION:-3m}"

./gradlew bootJar
JAR=$(ls build/libs/*.jar | grep -v plain | head -1)
mkdir -p results logs

docker start ride-lock-postgres >/dev/null
docker start ride-lock-temp-redis >/dev/null

for strategy in "${STRATEGIES[@]}"; do
  for vt in "${VT_OPTIONS[@]}"; do
    label="${strategy}-vt${vt}"
    echo "=== ${label} ==="

    docker exec -i ride-lock-postgres psql -U ridelock_user -d ridelock < loadtest/seed-hotzone.sql

    SPRING_PROFILES_ACTIVE=local \
    MATCHING_LOCK_STRATEGY=$strategy \
    SPRING_THREADS_VIRTUAL_ENABLED=$vt \
    MATCHING_ASSIGN_TIMEOUT_SECONDS=5 \
    MATCHING_TIMEOUT_CHECK_INTERVAL_SECONDS=1 \
    java -jar "$JAR" > "logs/${label}.log" 2>&1 &
    APP_PID=$!

    ready=false
    for i in $(seq 1 30); do
      if grep -q "실행 설정 확인" "logs/${label}.log"; then
        ready=true
        break
      fi
      sleep 1
    done
    if [ "$ready" != "true" ]; then
      echo "${label}: 30초 안에 기동 신호를 못 찾음 - logs/${label}.log 확인 필요" >&2
      kill $APP_PID 2>/dev/null || true
      exit 1
    fi

    DURATION=$DURATION k6 run loadtest/hotzone-matching.js --out json="results/${label}.json"

    kill $APP_PID
    wait $APP_PID 2>/dev/null || true
    sleep 2
  done
done

echo "전체 6개 조합 완료. results/*.json, logs/*.log 확인."
