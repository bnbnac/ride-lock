CREATE EXTENSION IF NOT EXISTS postgis;

-- 배차 로직 전용 (락 대상)
CREATE TABLE driver_status (
  driver_id BIGINT PRIMARY KEY,
  status    VARCHAR(20) NOT NULL DEFAULT 'IDLE',  -- IDLE / ASSIGNED / ON_TRIP
  version   BIGINT NOT NULL DEFAULT 0,            -- 낙관적 락용
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 고빈도 위치 업데이트 전용 (락 스코프 분리)
CREATE TABLE driver_location (
  driver_id BIGINT PRIMARY KEY,
  location  GEOGRAPHY(Point, 4326) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_driver_location_gist ON driver_location USING GIST(location);
CREATE INDEX idx_driver_status ON driver_status(status);
