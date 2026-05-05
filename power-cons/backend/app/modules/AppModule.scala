package modules

import com.google.inject.AbstractModule
import services.MqttService

class AppModule extends AbstractModule {
  override def configure(): Unit = {
    // Start MQTT subscriber at application boot, not on first request
    bind(classOf[MqttService]).asEagerSingleton()
  }
}
