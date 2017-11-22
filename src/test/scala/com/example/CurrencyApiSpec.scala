package com.example

import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}

import scala.util.Random

class CurrencyApiSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest with BeforeAndAfterEach {

  val wiremockHost = "localhost"
  val wiremockPort: Int = randomPort()

  val wireMockServer = new WireMockServer(wireMockConfig().port(wiremockPort))

  val fixerMockedUri = Uri(s"http://$wiremockHost:$wiremockPort")
  val notificationWehbhookMockedUri = Uri(s"http://$wiremockHost:$wiremockPort/webhook")

  val fixerClient = new fixer.ApiClientImpl(fixerMockedUri)
  val ratesChangeNotifier = new publisher.RatesChangeNotifierImpl(notificationWehbhookMockedUri)
  val currencyWatcher = new publisher.CurrencyWatcher(fixerClient, ratesChangeNotifier)

  val fakedNow = ZonedDateTime.now().toInstant.atZone(ZoneId.of("UTC"))

  val mainRoute = new Routes(fixerClient, currencyWatcher, () => fakedNow).mainRoute


  override def beforeEach {
    wireMockServer.start()
    WireMock.configureFor(wiremockHost, wiremockPort)
  }

  override def afterEach {
    wireMockServer.stop()
  }

  val ratesJsonObj = """{"CHF":0.99161,"GBP":0.75782,"PLN":3.5898,"EUR":0.84782}"""

  "Routes" should {
    "return latest currency rates from fixer.io on /rates endpoint" in {

      stubFor {
        get(urlEqualTo("/latest?base=USD"))
          .willReturn {
            aResponse()
              .withStatus(200)
              .withBody {
                s"""{"base":"USD","date":"2017-11-22","rates":$ratesJsonObj}"""
              }
          }
      }

      val request = HttpRequest(uri = Uri("/rates").withQuery(Query("base" -> "USD")))

      request ~> mainRoute ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===(
          s"""{"success":true,"response":{"base":"USD","timestamp":"$fakedNow","rates":$ratesJsonObj}}"""
        )
      }
    }
  }


  lazy val rand = new Random()

  def randIntBetween(a: Int, b: Int): Int = {
    require(a <= b)
    a + rand.nextInt(b - a + 1)
  }

  def randomPort(): Int = randIntBetween(10000, 65000)

}
