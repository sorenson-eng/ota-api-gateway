package com.sorenson.api_gateway

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import com.sorenson.api_gateway.http.{APiGatewayDirectives, ApiGatewayRoutes}
import org.slf4j.LoggerFactory


object Boot extends App
  with Directives
  with SettingsSupport
  with VersionInfo {

  private val log = LoggerFactory.getLogger(this.getClass)
  private implicit val system = ActorSystem("ApiGwBoot")

  import system.dispatcher

  log.info(s"Starting $version on http://${settings.host}:${settings.port}")

  val routes: Route =
    (logRequestResult("api-gw" -> Logging.InfoLevel) & APiGatewayDirectives.versionHeaders(version)) {
      new ApiGatewayRoutes().routes
    }

  val httpsContext = BootTlsContext.from(settings)

  Http(system).newServerAt(settings.host, settings.port).enableHttps(httpsContext).bindFlow(routes)
}
