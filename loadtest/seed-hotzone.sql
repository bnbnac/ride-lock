-- loadtest/seed-hotzone.sql
-- 핫존 부하테스트용 고정 기사 5명 시드/리셋. 실행할 때마다 무조건 IDLE로 되돌린다
-- 이전 조합 실행 중 ASSIGNED로 남았을 수 있어 멱등적으로 여러 번 재실행 가능해야 한다.
INSERT INTO driver_status (driver_id, status, version, updated_at)
VALUES (9001, 'IDLE', 0, now()), (9002, 'IDLE', 0, now()), (9003, 'IDLE', 0, now()),
       (9004, 'IDLE', 0, now()), (9005, 'IDLE', 0, now())
ON CONFLICT (driver_id) DO UPDATE
    SET status = 'IDLE', version = driver_status.version + 1, updated_at = now();

INSERT INTO driver_location (driver_id, location, updated_at)
VALUES (9001, ST_Transform(ST_SetSRID(ST_MakePoint(126.9707, 37.5547), 4326), 5179), now()),
       (9002, ST_Transform(ST_SetSRID(ST_MakePoint(126.9710, 37.5549), 4326), 5179), now()),
       (9003, ST_Transform(ST_SetSRID(ST_MakePoint(126.9704, 37.5545), 4326), 5179), now()),
       (9004, ST_Transform(ST_SetSRID(ST_MakePoint(126.9712, 37.5544), 4326), 5179), now()),
       (9005, ST_Transform(ST_SetSRID(ST_MakePoint(126.9702, 37.5550), 4326), 5179), now())
ON CONFLICT (driver_id) DO UPDATE
    SET location = EXCLUDED.location, updated_at = EXCLUDED.updated_at;

DELETE FROM trip WHERE driver_id IN (9001, 9002, 9003, 9004, 9005);
