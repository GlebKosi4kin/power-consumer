CREATE TABLE IF NOT EXISTS energy_readings (
    id             SERIAL PRIMARY KEY,
    date           DATE        NOT NULL,
    hour           INTEGER     NOT NULL CHECK (hour >= 0 AND hour <= 23),
    consumption_mwh DECIMAL(10, 2) NOT NULL,
    period         VARCHAR(20) NOT NULL,  -- T1_NIGHT, T2_MORNING, T3_DAY, T2_EVENING
    is_anomaly     BOOLEAN     DEFAULT FALSE,
    recommendation TEXT,
    created_at     TIMESTAMP   DEFAULT NOW(),
    UNIQUE(date, hour)
);

CREATE INDEX IF NOT EXISTS idx_energy_date       ON energy_readings(date);
CREATE INDEX IF NOT EXISTS idx_energy_anomaly    ON energy_readings(is_anomaly);
CREATE INDEX IF NOT EXISTS idx_energy_date_hour  ON energy_readings(date, hour);
