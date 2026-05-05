package models

import java.time.LocalDate
import play.api.libs.json._

case class EnergyReading(
  id:             Option[Long],
  date:           LocalDate,
  hour:           Int,
  consumptionMwh: BigDecimal,
  period:         String,
  isAnomaly:      Boolean,
  recommendation: Option[String],
)

case class DailyStats(
  date:         LocalDate,
  avgMwh:       BigDecimal,
  peakMwh:      BigDecimal,
  totalMwh:     BigDecimal,
  anomalyCount: Int,
)

object EnergyReading {
  implicit val writes: Writes[EnergyReading] = Json.writes[EnergyReading]
}

object DailyStats {
  implicit val writes: Writes[DailyStats] = Json.writes[DailyStats]
}
