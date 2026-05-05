package services

import javax.inject.Singleton

@Singleton
class AnomalyService {

  private val thresholds: Map[String, Double] = Map(
    "T1_NIGHT"   -> 12.0,
    "T2_MORNING" -> 25.0,
    "T3_DAY"     -> 30.0,
    "T2_EVENING" -> 23.0,
  )

  private val recommendations: Map[String, String] = Map(
    "T1_NIGHT"   -> "Ночное потребление выше нормы. Проверьте незапланированное оборудование и устраните утечки энергии.",
    "T2_MORNING" -> "Утренний пик превышен. Распределите запуск оборудования по времени для снижения пиковой нагрузки.",
    "T3_DAY"     -> "Дневной пик превышен. Перенесите энергоёмкие операции на период T1 (ночь) для экономии.",
    "T2_EVENING" -> "Вечерний расход выше нормы. Убедитесь, что оборудование отключено после окончания смены.",
  )

  def getPeriod(hour: Int): String = hour match {
    case h if h <= 5              => "T1_NIGHT"
    case h if h <= 9              => "T2_MORNING"
    case h if h <= 18             => "T3_DAY"
    case _                        => "T2_EVENING"
  }

  def checkAnomaly(hour: Int, consumption: Double): Boolean = {
    val period = getPeriod(hour)
    consumption > thresholds(period)
  }

  def getRecommendation(hour: Int, isAnomaly: Boolean): Option[String] =
    if (isAnomaly) Some(recommendations(getPeriod(hour))) else None
}
