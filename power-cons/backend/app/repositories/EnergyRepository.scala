package repositories

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import models.{DailyStats, EnergyReading}
import play.api.db.Database

@Singleton
class EnergyRepository @Inject() (db: Database) {

  private val parser: RowParser[EnergyReading] =
    (get[Long]("id") ~
      get[LocalDate]("date") ~
      get[Int]("hour") ~
      get[BigDecimal]("consumption_mwh") ~
      get[String]("period") ~
      get[Boolean]("is_anomaly") ~
      get[Option[String]]("recommendation")).map {
      case id ~ date ~ hour ~ mwh ~ period ~ anomaly ~ rec =>
        EnergyReading(Some(id), date, hour, mwh, period, anomaly, rec)
    }

  def upsert(r: EnergyReading): Unit = db.withConnection { implicit conn =>
    SQL"""
      INSERT INTO energy_readings (date, hour, consumption_mwh, period, is_anomaly, recommendation)
      VALUES (${r.date}, ${r.hour}, ${r.consumptionMwh}, ${r.period}, ${r.isAnomaly}, ${r.recommendation})
      ON CONFLICT (date, hour) DO NOTHING
    """.executeInsert()
  }

  def findByDate(date: LocalDate): Seq[EnergyReading] = db.withConnection { implicit conn =>
    SQL"SELECT * FROM energy_readings WHERE date = $date ORDER BY hour"
      .as(parser.*)
  }

  def findByRange(from: LocalDate, to: LocalDate): Seq[EnergyReading] = db.withConnection { implicit conn =>
    SQL"SELECT * FROM energy_readings WHERE date >= $from AND date < $to ORDER BY date, hour"
      .as(parser.*)
  }

  def findAnomalies(from: LocalDate, to: LocalDate): Seq[EnergyReading] = db.withConnection { implicit conn =>
    SQL"""
      SELECT * FROM energy_readings
      WHERE is_anomaly = TRUE AND date >= $from AND date < $to
      ORDER BY date, hour
    """.as(parser.*)
  }

  def dailyStats(date: LocalDate): Option[DailyStats] = db.withConnection { implicit conn =>
    SQL"""
      SELECT
        $date::date            AS date,
        AVG(consumption_mwh)   AS avg_mwh,
        MAX(consumption_mwh)   AS peak_mwh,
        SUM(consumption_mwh)   AS total_mwh,
        COUNT(*) FILTER (WHERE is_anomaly) AS anomaly_count
      FROM energy_readings
      WHERE date = $date
    """.as(
      (get[LocalDate]("date") ~
        get[BigDecimal]("avg_mwh") ~
        get[BigDecimal]("peak_mwh") ~
        get[BigDecimal]("total_mwh") ~
        get[Int]("anomaly_count")).map {
        case d ~ avg ~ peak ~ total ~ cnt =>
          DailyStats(d, avg.setScale(2, BigDecimal.RoundingMode.HALF_UP), peak, total, cnt)
      }.singleOpt
    )
  }

  def monthlyStats(year: Int, month: Int): Seq[DailyStats] = db.withConnection { implicit conn =>
    SQL"""
      SELECT
        date,
        AVG(consumption_mwh)   AS avg_mwh,
        MAX(consumption_mwh)   AS peak_mwh,
        SUM(consumption_mwh)   AS total_mwh,
        COUNT(*) FILTER (WHERE is_anomaly) AS anomaly_count
      FROM energy_readings
      WHERE EXTRACT(YEAR FROM date)  = $year
        AND EXTRACT(MONTH FROM date) = $month
      GROUP BY date
      ORDER BY date
    """.as(
      (get[LocalDate]("date") ~
        get[BigDecimal]("avg_mwh") ~
        get[BigDecimal]("peak_mwh") ~
        get[BigDecimal]("total_mwh") ~
        get[Int]("anomaly_count")).map {
        case d ~ avg ~ peak ~ total ~ cnt =>
          DailyStats(d, avg.setScale(2, BigDecimal.RoundingMode.HALF_UP), peak, total, cnt)
      }.*
    )
  }
}
