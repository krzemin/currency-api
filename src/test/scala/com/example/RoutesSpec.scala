package com.example

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

class RoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  val mainRoute: Route = new Routes().mainRoute

  "Routes" should {
    "return 'test' on /rates endpoint" in {
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = "/rates")

      request ~> mainRoute ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`text/plain(UTF-8)`)

        // and no entries should be in the list:
        entityAs[String] should ===("test")
      }

    }
  }
}
