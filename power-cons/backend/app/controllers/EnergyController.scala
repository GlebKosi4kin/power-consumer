package controllers

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

import models.{DailyStats, EnergyReading}
import play.api.libs.json._
import play.api.mvc._
import repositories.EnergyRepository

@Singleton
class EnergyController @Inject() (
  cc:         ControllerComponents,
  repository: EnergyRepository,
) extends AbstractController(cc) {

  implicit val localDateWrites: Writes[LocalDate] =
    Writes[LocalDate](d => JsString(d.toString))

  def readings(date: Option[String]): Action[AnyContent] = Action {
    val d = date.map(LocalDate.parse).getOrElse(LocalDate.now())
    Ok(Json.toJson(repository.findByDate(d)))
  }

  def readingsRange(from: String, to: String): Action[AnyContent] = Action {
    val rows = repository.findByRange(LocalDate.parse(from), LocalDate.parse(to))
    Ok(Json.toJson(rows))
  }

  def anomalies(from: String, to: String): Action[AnyContent] = Action {
    val rows = repository.findAnomalies(LocalDate.parse(from), LocalDate.parse(to))
    Ok(Json.toJson(rows))
  }

  def dailyStats(date: String): Action[AnyContent] = Action {
    repository.dailyStats(LocalDate.parse(date)) match {
      case Some(stats) => Ok(Json.toJson(stats))
      case None        => NotFound(Json.obj("error" -> s"No data for $date"))
    }
  }

  def monthlyStats(year: Int, month: Int): Action[AnyContent] = Action {
    Ok(Json.toJson(repository.monthlyStats(year, month)))
  }

  def preflight(path: String): Action[AnyContent] = Action {
    Ok.withHeaders(
      "Access-Control-Allow-Origin"  -> "*",
      "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers" -> "Content-Type",
    )
  }
}
