#!/usr/bin/env bash
# loadtest/run-comparison.sh
# 락 3종(pessimistic/optimistic/redis) × 스레드 모델 2종(platform/virtual) = 6개 조합을
# 순회하며 앱을 재기동하고 핫존 부하테스트를 실행한다. 매 조합 전 기사 상태를 리셋해야
# 이전 조합의 잔여 배정이 다음 조합 결과를 오염시키지 않는다.
set -euo pipefail

STRATEGIES=(pessimistic optimistic redis)
VT_OPTIONS=(false true)
DURATION="${DURATION:-3m}"

# 같은 기사(driver_id)의 두 Trip이 활성 구간([assigned_at, end))에서 겹치면 이중 배정이다.
# end는 CANCELLED면 updated_at(타임아웃 처리 시각), 아직 ASSIGNED면 now()(아직 안 끝남)로 본다.
# 이 검증은 unit 동시성 테스트(50스레드, 기사 1명)와 달리 실제 k6 부하 하에서 이중 배정이
# 없었는지를 DB 상태로 직접 확인한다 - k6 자체는 HTTP 상태 코드만 셀 뿐 이 불변조건을 보지 않는다.
DOUBLE_BOOKING_QUERY="
SELECT count(*) FROM trip t1
JOIN trip t2 ON t1.driver_id = t2.driver_id AND t1.id < t2.id
WHERE t1.driver_id IN (9001,9002,9003,9004,9005)
  AND t1.assigned_at < (CASE WHEN t2.status = 'CANCELLED' THEN t2.updated_at ELSE now() END)
  AND t2.assigned_at < (CASE WHEN t1.status = 'CANCELLED' THEN t1.updated_at ELSE now() END);
"
DOUBLE_BOOKING_DETAIL_QUERY="
SELECT t1.driver_id, t1.id, t1.assigned_at, t1.status, t2.id, t2.assigned_at, t2.status
FROM trip t1
JOIN trip t2 ON t1.driver_id = t2.driver_id AND t1.id < t2.id
WHERE t1.driver_id IN (9001,9002,9003,9004,9005)
  AND t1.assigned_at < (CASE WHEN t2.status = 'CANCELLED' THEN t2.updated_at ELSE now() END)
  AND t2.assigned_at < (CASE WHEN t1.status = 'CANCELLED' THEN t1.updated_at ELSE now() END);
"

./gradlew bootJar
JAR=$(ls build/libs/*.jar | grep -v plain | head -1)
mkdir -p results logs
: > results/doublebooking-summary.txt

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

    VIOLATIONS=$(docker exec -i ride-lock-postgres psql -U ridelock_user -d ridelock -t -A -c "$DOUBLE_BOOKING_QUERY")
    echo "${label}: 이중 배정(겹치는 Trip 쌍) ${VIOLATIONS}건" | tee -a results/doublebooking-summary.txt
    if [ "$VIOLATIONS" != "0" ]; then
      docker exec -i ride-lock-postgres psql -U ridelock_user -d ridelock -c "$DOUBLE_BOOKING_DETAIL_QUERY" \
        > "results/${label}-doublebooking-detail.txt"
    fi

    sleep 2
  done
done

echo "전체 6개 조합 완료. results/*.json, logs/*.log, results/doublebooking-summary.txt 확인."
