CREATE EXTENSION IF NOT EXISTS postgis;

-- 배차 로직 전용 (락 대상)
CREATE TABLE driver_status (
  driver_id BIGINT PRIMARY KEY,
  status    VARCHAR(20) NOT NULL DEFAULT 'IDLE',  -- IDLE / ASSIGNED / ON_TRIP
  version   BIGINT NOT NULL DEFAULT 0,            -- 낙관적 락용
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 고빈도 위치 업데이트 전용 (락 스코프 분리)
-- 한국 한정 서비스 전제로 GEOGRAPHY 대신 투영좌표계(EPSG:5179, 국토지리정보원 통일좌표계) 사용.
-- WGS84 위경도는 쓰기 시점에 ST_Transform으로 변환해서 저장 (DriverLocationRepository 참고).
CREATE TABLE driver_location (
  driver_id BIGINT PRIMARY KEY,
  location  GEOMETRY(Point, 5179) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_driver_location_gist ON driver_location USING GIST(location);
CREATE INDEX idx_driver_status ON driver_status(status);
