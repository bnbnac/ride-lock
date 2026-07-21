#!/usr/bin/env bash
# loadtest/run-routing-delay-comparison.sh
# 락을 쥔 채 느린 I/O(라우팅 API 시뮬레이션)를 하면 어떤 대가를 치르는지 확인하는 실험.
# 비관적/Redis x routing-delay-inside-lock(false=before-lock/true=inside-lock) 4개 조합을
# 순회한다. 낙관적은 배치 선택지가 없어(보유할 락이 없음) 이 실험에서 제외한다. 스레드 모델
# 축은 이 질문과 무관해 플랫폼으로 고정한다.
set -euo pipefail

STRATEGIES=(pessimistic redis)
PLACEMENTS=(false true)
DURATION="${DURATION:-2m}"
ROUTING_DELAY_MS="${ROUTING_DELAY_MS:-200}"

DOUBLE_BOOKING_QUERY="
SELECT count(*) FROM trip t1
JOIN trip t2 ON t1.driver_id = t2.driver_id AND t1.id < t2.id
WHERE t1.driver_id IN (9001,9002,9003,9004,9005)
  AND t1.assigned_at < (CASE WHEN t2.status = 'CANCELLED' THEN t2.updated_at ELSE now() END)
  AND t2.assigned_at < (CASE WHEN t1.status = 'CANCELLED' THEN t1.updated_at ELSE now() END);
"

./gradlew bootJar
JAR=$(ls build/libs/*.jar | grep -v plain | head -1)
mkdir -p results logs
: > results/routing-delay-doublebooking-summary.txt

docker start ride-lock-postgres >/dev/null
docker start ride-lock-temp-redis >/dev/null

for strategy in "${STRATEGIES[@]}"; do
  for inside in "${PLACEMENTS[@]}"; do
    placement_label="before-lock"
    if [ "$inside" = "true" ]; then
      placement_label="inside-lock"
    fi
    label="${strategy}-${placement_label}"
    echo "=== ${label} (routing-delay=${ROUTING_DELAY_MS}ms) ==="

    docker exec -i ride-lock-postgres psql -U ridelock_user -d ridelock < loadtest/seed-hotzone.sql

    SPRING_PROFILES_ACTIVE=local \
    MATCHING_LOCK_STRATEGY=$strategy \
    MATCHING_ROUTING_DELAY_MS=$ROUTING_DELAY_MS \
    MATCHING_ROUTING_DELAY_INSIDE_LOCK=$inside \
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

    # 비관적 조합에서만 pg_stat_activity를 캡처한다 - FOR UPDATE 대기는 DB 세션에 남지만,
    # Redisson 락 대기(WAIT_SECONDS=0, 즉시 실패)는 DB 세션에 안 남아 캡처 대상이 아니다.
    PG_WATCH_PID=""
    if [ "$strategy" = "pessimistic" ]; then
      (
        while true; do
          echo "--- $(date '+%H:%M:%S.%3N') ---"
          docker exec -i ride-lock-postgres psql -U ridelock_user -d ridelock -c \
            "SELECT pid, state, wait_event_type, wait_event, query FROM pg_stat_activity WHERE datname='ridelock' AND wait_event_type IS NOT NULL;"
          sleep 1
        done
      ) > "results/${label}-pg_stat_activity.log" 2>&1 &
      PG_WATCH_PID=$!
    fi

    DURATION=$DURATION k6 run loadtest/hotzone-matching.js --out json="results/${label}.json"

    if [ -n "$PG_WATCH_PID" ]; then
      kill "$PG_WATCH_PID" 2>/dev/null || true
    fi

    kill $APP_PID
    wait $APP_PID 2>/dev/null || true

    VIOLATIONS=$(docker exec -i ride-lock-postgres psql -U ridelock_user -d ridelock -t -A -c "$DOUBLE_BOOKING_QUERY")
    echo "${label}: 이중 배정(겹치는 Trip 쌍) ${VIOLATIONS}건" | tee -a results/routing-delay-doublebooking-summary.txt

    sleep 2
  done
done

echo "전체 4개 조합 완료. results/*.json, logs/*.log, results/*-pg_stat_activity.log, results/routing-delay-doublebooking-summary.txt 확인."
