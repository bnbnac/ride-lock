CREATE TABLE trip (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  driver_id   BIGINT NOT NULL,
  status      VARCHAR(20) NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_status_assigned_at ON trip(status, assigned_at);
