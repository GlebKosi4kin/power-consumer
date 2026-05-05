"""
Energy consumption data generator.

Generates hourly data for 01.01.2026 – 01.02.2026 based on realistic
factory load profiles, inserts into PostgreSQL, and publishes each reading
to MQTT for real-time processing by the Scala backend.

Time periods and expected consumption ranges:
  T1_NIGHT   00-05  5–10  MWh  (minimum load)
  T2_MORNING 06-09  10–22 MWh  (ramp-up)
  T3_DAY     10-18  22–27 MWh  (peak production)
  T2_EVENING 19-23  20→10 MWh  (declining, ramp-down)

Anomaly thresholds (values that trigger a recommendation):
  T1_NIGHT   > 12 MWh
  T2_MORNING > 25 MWh
  T3_DAY     > 30 MWh
  T2_EVENING > 23 MWh
"""

import os
import json
import time
import random
from datetime import date, timedelta

import psycopg2
import paho.mqtt.client as mqtt

# ── configuration ────────────────────────────────────────────────────────────

MQTT_BROKER = os.getenv("MQTT_BROKER", "localhost")
MQTT_PORT   = int(os.getenv("MQTT_PORT", "1883"))
MQTT_TOPIC  = os.getenv("MQTT_TOPIC", "mqtt_telemetry")

DB_HOST     = os.getenv("DB_HOST", "localhost")
DB_PORT     = int(os.getenv("DB_PORT", "5432"))
DB_NAME     = os.getenv("DB_NAME", "power_cons")
DB_USER     = os.getenv("DB_USER", "power_user")
DB_PASSWORD = os.getenv("DB_PASSWORD", "power_pass")

START_DATE = date(2026, 1, 1)
END_DATE   = date(2026, 2, 1)

ANOMALY_CHANCE = 0.04  # 4 % probability per hour

# ── period helpers ────────────────────────────────────────────────────────────

def get_period(hour: int) -> str:
    if 0 <= hour <= 5:
        return "T1_NIGHT"
    elif 6 <= hour <= 9:
        return "T2_MORNING"
    elif 10 <= hour <= 18:
        return "T3_DAY"
    else:
        return "T2_EVENING"


def base_consumption(hour: int) -> float:
    """Return a random consumption value for a normal (non-anomaly) reading."""
    if 0 <= hour <= 5:
        return round(random.uniform(5.0, 10.0), 2)
    elif 6 <= hour <= 9:
        return round(random.uniform(10.0, 22.0), 2)
    elif 10 <= hour <= 18:
        return round(random.uniform(22.0, 27.0), 2)
    else:
        # Evening declines from 20 → 10 across hours 19-23
        progress = (hour - 19) / 4.0            # 0.0 at 19:00, 1.0 at 23:00
        upper = 20.0 - progress * 10.0          # 20 → 10
        lower = max(upper - 5.0, 5.0)
        return round(random.uniform(lower, upper), 2)


def anomaly_consumption(hour: int) -> float:
    """Return a value that exceeds the anomaly threshold for the period."""
    if 0 <= hour <= 5:
        return round(random.uniform(13.0, 18.0), 2)
    elif 6 <= hour <= 9:
        return round(random.uniform(26.0, 32.0), 2)
    elif 10 <= hour <= 18:
        return round(random.uniform(31.0, 38.0), 2)
    else:
        return round(random.uniform(24.0, 30.0), 2)


def is_anomaly(hour: int, value: float) -> bool:
    thresholds = {
        "T1_NIGHT":   12.0,
        "T2_MORNING": 25.0,
        "T3_DAY":     30.0,
        "T2_EVENING": 23.0,
    }
    return value > thresholds[get_period(hour)]


RECOMMENDATIONS = {
    "T1_NIGHT":   "Ночное потребление выше нормы. Проверьте незапланированное оборудование и устраните утечки энергии.",
    "T2_MORNING": "Утренний пик превышен. Распределите запуск оборудования по времени для снижения пиковой нагрузки.",
    "T3_DAY":     "Дневной пик превышен. Перенесите энергоёмкие операции на период T1 (ночь) для экономии.",
    "T2_EVENING": "Вечерний расход выше нормы. Убедитесь, что оборудование отключено после окончания смены.",
}


def get_recommendation(hour: int, anomaly: bool) -> str | None:
    if not anomaly:
        return None
    return RECOMMENDATIONS[get_period(hour)]


# ── database ──────────────────────────────────────────────────────────────────

INSERT_SQL = """
INSERT INTO energy_readings
    (date, hour, consumption_mwh, period, is_anomaly, recommendation)
VALUES
    (%s, %s, %s, %s, %s, %s)
ON CONFLICT (date, hour) DO NOTHING;
"""


def connect_db():
    for attempt in range(10):
        try:
            conn = psycopg2.connect(
                host=DB_HOST, port=DB_PORT,
                dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD,
            )
            print(f"Connected to PostgreSQL at {DB_HOST}:{DB_PORT}/{DB_NAME}")
            return conn
        except psycopg2.OperationalError as e:
            print(f"DB not ready (attempt {attempt + 1}/10): {e}")
            time.sleep(3)
    raise RuntimeError("Could not connect to PostgreSQL after 10 attempts")


# ── MQTT ──────────────────────────────────────────────────────────────────────

def connect_mqtt() -> mqtt.Client:
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    for attempt in range(10):
        try:
            client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
            client.loop_start()
            print(f"Connected to MQTT broker at {MQTT_BROKER}:{MQTT_PORT}")
            return client
        except Exception as e:
            print(f"MQTT not ready (attempt {attempt + 1}/10): {e}")
            time.sleep(2)
    raise RuntimeError("Could not connect to MQTT after 10 attempts")


# ── main ──────────────────────────────────────────────────────────────────────

def generate():
    conn   = connect_db()
    cursor = conn.cursor()
    mqtt_client = connect_mqtt()

    current = START_DATE
    total_rows = 0
    total_anomalies = 0

    print(f"Generating data from {START_DATE} to {END_DATE} …")

    while current < END_DATE:
        for hour in range(24):
            anomaly = random.random() < ANOMALY_CHANCE
            value   = anomaly_consumption(hour) if anomaly else base_consumption(hour)
            period  = get_period(hour)
            anomaly = is_anomaly(hour, value)          # double-check threshold
            rec     = get_recommendation(hour, anomaly)

            cursor.execute(INSERT_SQL, (current, hour, value, period, anomaly, rec))

            payload = json.dumps({
                "date":            current.isoformat(),
                "hour":            hour,
                "consumption_mwh": value,
                "period":          period,
                "is_anomaly":      anomaly,
                "recommendation":  rec,
            })
            mqtt_client.publish(MQTT_TOPIC, payload)

            total_rows += 1
            if anomaly:
                total_anomalies += 1

        conn.commit()
        print(f"  {current.isoformat()} — written 24 hours")
        current += timedelta(days=1)

    cursor.close()
    conn.close()
    mqtt_client.loop_stop()
    mqtt_client.disconnect()

    print(f"\nDone. Inserted {total_rows} readings, {total_anomalies} anomalies detected.")


if __name__ == "__main__":
    generate()
