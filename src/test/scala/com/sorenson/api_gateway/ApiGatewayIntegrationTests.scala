package com.sorenson.api_gateway

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.sorenson.api_gateway.http.ApiGatewayRoutes
import io.circe.{Codec, Json}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import org.scalatest.time.{Seconds, Span}

case class HttpBinResponse(headers: Map[String, String], method: String, url: String, json: Json, args: Map[String, String])

object HttpBinResponse {
  implicit val codec: Codec[HttpBinResponse] = io.circe.generic.semiauto.deriveCodec[HttpBinResponse]
}

class ApiGatewayIntegrationTests extends AnyFunSuite with ScalatestRouteTest with SettingsSupport with Matchers {

  import HttpBinResponse._

  lazy val routes = Route.seal(new ApiGatewayRoutes().routes)

  implicit val defaultTimeout = RouteTestTimeout(Span(5, Seconds))

  test("proxies to treehub") {
    Post("/eng/treehub/my/object") ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[HttpBinResponse]

      response.method shouldBe "POST"
      response.headers.get("X-Ats-Device-Uuid") should contain("cn-unknown")
      response.headers.get("X-Ats-Namespace") should contain("eng")
      response.headers.get("X-Svrs-Forwarded-To") should contain("treehub")
      response.url should endWith("/treehub/api/v2/my/object")
    }
  }

  test("forwards query parameters") {
    Get("/eng/treehub/my/object?myparam=myvalue") ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[HttpBinResponse]
      response.method shouldBe "GET"
      response.args shouldBe Map("myparam" -> "myvalue")

      val uri = Uri(response.url)
      uri.path.toString() should endWith("/treehub/api/v2/my/object")
      uri.query().get("myparam") should contain("myvalue")
    }
  }

  test("forwards json bodies") {
    Post("/eng/treehub/my/object", Map("myobject" -> "myvalue").asJson) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[HttpBinResponse]

      response.method shouldBe "POST"
      response.json shouldBe Map("myobject" -> "myvalue").asJson
      response.url should endWith("/treehub/api/v2/my/object")
    }
  }

  test("proxies director") {
    Get("/eng/director/repo/root.json") ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[HttpBinResponse]

      response.method shouldBe "GET"
      response.headers.get("X-Svrs-Forwarded-To") should contain("director")
      response.url should endWith("/director/api/v1/device/cn-unknown/repo/root.json")
    }
  }

  test("proxies to reposerver") {
    Get("/eng/repo/repo/root.json") ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[HttpBinResponse]

      response.method shouldBe "GET"
      response.headers.get("X-Svrs-Forwarded-To") should contain("reposerver")
      response.url should endWith("/reposerver/api/v1/user_repo/repo/root.json")
    }
  }

  test("proxies system_info/* to dev registry") {
    Get("/eng/system_info/notinstalled") ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[HttpBinResponse]

      response.method shouldBe "GET"
      response.headers.get("X-Svrs-Forwarded-To") should contain("deviceRegistry")
      response.url should endWith("/device-registry/api/v1/devices/cn-unknown/system_info/notinstalled")
    }
  }

  test("proxies system_info[] to dev registry") {
    Get("/eng/system_info") ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[HttpBinResponse]

      response.method shouldBe "GET"
      response.headers.get("X-Svrs-Forwarded-To") should contain("deviceRegistry")
      response.url should endWith("/device-registry/api/v1/devices/cn-unknown/system_info")
    }
  }

  test("proxies /devices to device registry") {
    Get("/eng/devices") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[HttpBinResponse]

      response.method shouldBe "GET"
      response.headers.get("X-Svrs-Forwarded-To") should contain("deviceRegistry")
      response.url should endWith("/device-registry/api/v1/devices")
    }
  }

  test("proxies /core/installed to device registry") {
    Get("/eng/core/installed") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[HttpBinResponse]

      response.method shouldBe "GET"
      response.headers.get("X-Svrs-Forwarded-To") should contain("deviceRegistry")
      response.url should endWith("/device-registry/api/v1/mydevice/cn-unknown/packages")
    }
  }

  test("proxies /events to device registry") {
    Post("/eng/events") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[HttpBinResponse]

      response.method shouldBe "POST"
      response.headers.get("X-Svrs-Forwarded-To") should contain("deviceRegistry")
      response.url should endWith("/device-registry/api/v3/events")
    }
  }
}
