package services

import javax.inject.{Inject, Singleton}

import models.EnergyReading
import org.eclipse.paho.client.mqttv3._
import play.api.inject.ApplicationLifecycle
import play.api.libs.json._
import play.api.{Configuration, Logger}
import repositories.EnergyRepository

import java.time.LocalDate
import scala.concurrent.Future

@Singleton
class MqttService @Inject() (
  config:       Configuration,
  lifecycle:    ApplicationLifecycle,
  repository:   EnergyRepository,
  anomalyService: AnomalyService,
) extends MqttCallback {

  private val log = Logger(getClass)

  private val brokerUrl = config.get[String]("mqtt.broker")
  private val topic     = config.get[String]("mqtt.topic")

  private val client = new MqttClient(brokerUrl, MqttClient.generateClientId())
  client.setCallback(this)

  connect()

  lifecycle.addStopHook { () =>
    Future.successful {
      if (client.isConnected) {
        client.disconnect()
        log.info("Disconnected from MQTT broker")
      }
    }
  }

  private def connect(): Unit = {
    try {
      val opts = new MqttConnectOptions()
      opts.setCleanSession(true)
      opts.setAutomaticReconnect(true)
      opts.setConnectionTimeout(10)
      client.connect(opts)
      client.subscribe(topic)
      log.info(s"Connected to MQTT broker $brokerUrl, subscribed to $topic")
    } catch {
      case e: MqttException =>
        log.warn(s"MQTT connect failed (will retry via auto-reconnect): ${e.getMessage}")
    }
  }

  override def connectionLost(cause: Throwable): Unit =
    log.warn(s"MQTT connection lost: ${cause.getMessage}")

  override def deliveryComplete(token: IMqttDeliveryToken): Unit = ()

  override def messageArrived(topic: String, message: MqttMessage): Unit = {
    val payload = new String(message.getPayload, "UTF-8")
    try {
      val json = Json.parse(payload)
      val date           = LocalDate.parse((json \ "date").as[String])
      val hour           = (json \ "hour").as[Int]
      val consumptionMwh = (json \ "consumption_mwh").as[BigDecimal]
      val period         = anomalyService.getPeriod(hour)
      val isAnomaly      = anomalyService.checkAnomaly(hour, consumptionMwh.toDouble)
      val rec            = anomalyService.getRecommendation(hour, isAnomaly)

      val reading = EnergyReading(None, date, hour, consumptionMwh, period, isAnomaly, rec)
      repository.upsert(reading)
    } catch {
      case e: Exception =>
        log.error(s"Failed to process MQTT message: ${e.getMessage}. Payload: $payload")
    }
  }
}
