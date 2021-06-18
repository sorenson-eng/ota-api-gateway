package com.sorenson.api_gateway.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.Slash
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.http.scaladsl.server.{Directives, _}
import com.sorenson.api_gateway.SettingsSupport
import com.sorenson.api_gateway.http.APiGatewayDirectives.{DeviceId, Namespace}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class DownstreamClient(uri: Uri, name: String)(implicit val system: ActorSystem) {
  private lazy val _http = Http()

  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def proxy(ns: Namespace, deviceId: DeviceId, path: Path, ctx: RequestContext): Future[HttpResponse] = {
    val basePath = if(uri.path.isEmpty) uri.path else Slash(uri.path)

    val newReq = ctx.request
      .addHeader(RawHeader("x-svrs-forwarded-to", name))
      .addHeader(RawHeader("x-ats-device-uuid", deviceId.value))
      .addHeader(RawHeader("x-ats-namespace", ns.value))
      .removeHeader("timeout-access")
      .withUri(uri.withPath(basePath ++ Slash(path)).withQuery(ctx.request.uri.query()))

    log.info(s"proxying request to $uri: $newReq")

    _http.singleRequest(newReq).map { req =>
      req.removeHeader("Server")
    }
  }
}

class DownstreamClients()(implicit val system: ActorSystem) extends SettingsSupport {
  lazy val treehub: DownstreamClient = new DownstreamClient(settings.treehubUri, "treehub")

  lazy val director: DownstreamClient = new DownstreamClient(settings.directorUri, "director")

  lazy val reposerver: DownstreamClient = new DownstreamClient(settings.reposerverUri, "reposerver")

  lazy val deviceRegistry: DownstreamClient = new DownstreamClient(settings.deviceRegistryUri, "deviceRegistry")
}

class ApiGatewayRoutes()(implicit val ec: ExecutionContext, system: ActorSystem) {
  import APiGatewayDirectives._
  import Directives._

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  lazy val clients  = new DownstreamClients()

  val routes: Route = {
    (path("health") & extractSslDeviceId) { deviceId =>
      complete (s"""{"deviceId": "$deviceId"}""")
    } ~
    (pathPrefix(NamespaceSegment) & extractSslDeviceId & extractRequestContext) { (ns, deviceId, ctx) =>
      log.debug(s"Handling request for $ns/$deviceId")

      path("treehub" / RemainingPath) { path =>
        val treehubPath = Path("api/v2/") ++ path
        complete(clients.treehub.proxy(ns, deviceId, treehubPath, ctx))
      } ~
      path("director" / RemainingPath) { path =>
        val directorPath = Path(s"api/v1/device/${deviceId.value}/") ++ path
        complete(clients.director.proxy(ns, deviceId, directorPath, ctx))
      } ~
      path("repo" / RemainingPath) { path =>
        val repoPath = Path(s"api/v1/user_repo/") ++ path
        complete(clients.reposerver.proxy(ns, deviceId, repoPath, ctx))
      } ~
      (ignoreTrailingSlash & path("system_info" / RemainingPath)) { path =>
        val _remainingPath = if(path.isEmpty) Path.Empty else Path.Slash(path)
        val _path = Path(s"api/v1/devices/${deviceId.value}/system_info") ++  _remainingPath
        complete(clients.deviceRegistry.proxy(ns, deviceId, _path, ctx))
      } ~
      path("devices") {
        val _path = Path(s"api/v1/devices")
        complete(clients.deviceRegistry.proxy(ns, deviceId, _path, ctx))
      } ~
      path("core" / "installed") {
        val _path = Path(s"api/v1/mydevice/${deviceId.value}/packages")
        complete(clients.deviceRegistry.proxy(ns, deviceId, _path, ctx))
      } ~
      path("events") {
        val _path = Path("api/v3/events")
        complete(clients.deviceRegistry.proxy(ns, deviceId, _path, ctx))
      }
    }
  }
}
