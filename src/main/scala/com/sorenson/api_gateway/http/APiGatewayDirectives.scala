package com.sorenson.api_gateway.http

import akka.http.scaladsl.model.AttributeKeys
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server._

import java.security.cert.X509Certificate
import java.time.Instant
import scala.util.{Failure, Success, Try}

object APiGatewayDirectives {

  import Directives._

  case class DeviceId(value: String) extends AnyVal

  case class Namespace(value: String) extends AnyVal

  lazy val NamespaceSegment: PathMatcher1[Namespace] = PathMatchers.Segment.flatMap { segment => Option(Namespace(segment)) }

  def versionHeaders(version: String): Directive0 = Directives.respondWithHeader(RawHeader("x-svrs-version", version))

  val extractSslDeviceId: Directive1[DeviceId] = (extractRequestContext & extractLog).tflatMap { case (ctx, log) =>
    val cn = Try {
      ctx.request.attribute(AttributeKeys.sslSession).get.getSession.getPeerPrincipal.getName
    }
    val expiresAt = Try {
      ctx.request.attribute(AttributeKeys.sslSession).get.session.getPeerCertificates.head.asInstanceOf[X509Certificate].getNotAfter
    }

    if (expiresAt.isSuccess && expiresAt.get.toInstant.isBefore(Instant.now())) {
      log.warning("Client certificate would have expired at {}. Continuing with expired certificate.", expiresAt.get.toInstant)
    }

    cn match {
      case Success(v) if v.startsWith("CN=") => provide(DeviceId(v.split("CN=").last))
      case Success(v) => provide(DeviceId(v))
      case Failure(err) =>
        log.warning(s"Could not extract namespace from certificate: {}", err)
        provide(DeviceId("cn-unknown"))
    }
  }
}
