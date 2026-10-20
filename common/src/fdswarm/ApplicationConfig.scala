package fdswarm

import com.typesafe.config.{Config, ConfigFactory}

/** The same resolved configuration is used by injection and JSON codecs. */
object ApplicationConfig:
  lazy val value: Config =
    ConfigFactory.parseFile(new java.io.File("config/sarasec.conf"))
      .withFallback(ConfigFactory.load())
