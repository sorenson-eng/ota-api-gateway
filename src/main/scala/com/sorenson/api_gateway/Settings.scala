package com.sorenson.api_gateway

import akka.http.scaladsl.model.Uri
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Paths

class Settings(config: Config) {
  lazy val _config = config.getConfig("api-gateway")

  lazy val host = _config.getString("server.host")
  lazy val port = _config.getInt("server.port")

  lazy val treehubUri = Uri(_config.getString("downstreams.treehub"))

  lazy val directorUri = Uri(_config.getString("downstreams.director"))

  lazy val reposerverUri = Uri(_config.getString("downstreams.reposerver"))

  lazy val deviceRegistryUri = Uri(_config.getString("downstreams.deviceRegistry"))

  lazy val keystorePath = Paths.get(_config.getString("keystore.path"))

  lazy val keystorePassword = _config.getString("keystore.password")
}

trait SettingsSupport {
  val settings: Settings = new Settings(ConfigFactory.load())
}
