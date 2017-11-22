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
  val plnRateJsonObj = """{"PLN":3.5898}"""

  "Routes" when {

    "/rates endpoint" should {

      "return latest currency rates from fixer.io" in {

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

        val request = HttpRequest(uri = "/rates?base=USD")

        request ~> mainRoute ~> check {
          status should ===(StatusCodes.OK)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===(
            s"""{"success":true,"response":{"base":"USD","timestamp":"$fakedNow","rates":$ratesJsonObj}}"""
          )
        }
      }

      "return latest currency rate of target currency from fixer.io" in {

        stubFor {
          get(urlEqualTo("/latest?base=USD&symbols=PLN"))
            .willReturn {
              aResponse()
                .withStatus(200)
                .withBody {
                  s"""{"base":"USD","date":"2017-11-22","rates":$plnRateJsonObj}"""
                }
            }
        }

        val request = HttpRequest(uri = "/rates?base=USD&target=PLN")

        request ~> mainRoute ~> check {
          status should ===(StatusCodes.OK)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===(
            s"""{"success":true,"response":{"base":"USD","timestamp":"$fakedNow","rates":$plnRateJsonObj}}"""
          )
        }
      }

      "return currency rates of requested date from fixer.io" in {

        val weekAgoTimestamp = fakedNow.minusWeeks(1)
        val weekAgoDate = weekAgoTimestamp.toLocalDate

        stubFor {
          get(urlEqualTo(s"/$weekAgoDate?base=USD"))
            .willReturn {
              aResponse()
                .withStatus(200)
                .withBody {
                  s"""{"base":"USD","date":"$weekAgoDate","rates":$ratesJsonObj}"""
                }
            }
        }

        val request = HttpRequest(uri = s"/rates?base=USD&timestamp=$weekAgoTimestamp")

        request ~> mainRoute ~> check {
          status should ===(StatusCodes.OK)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===(
            s"""{"success":true,"response":{"base":"USD","timestamp":"$weekAgoTimestamp","rates":$ratesJsonObj}}"""
          )
        }
      }

      "return rate of requested target currency at specified date from fixer.io" in {

        val weekAgoTimestamp = fakedNow.minusWeeks(1)
        val weekAgoDate = weekAgoTimestamp.toLocalDate

        stubFor {
          get(urlEqualTo(s"/$weekAgoDate?base=USD&symbols=PLN"))
            .willReturn {
              aResponse()
                .withStatus(200)
                .withBody {
                  s"""{"base":"USD","date":"$weekAgoDate","rates":$plnRateJsonObj}"""
                }
            }
        }

        val request = HttpRequest(uri = s"/rates?base=USD&target=PLN&timestamp=$weekAgoTimestamp")

        request ~> mainRoute ~> check {
          status should ===(StatusCodes.OK)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===(
            s"""{"success":true,"response":{"base":"USD","timestamp":"$weekAgoTimestamp","rates":$plnRateJsonObj}}"""
          )
        }
      }

      "handle expected fixer.io errors" in {
        stubFor {
          get(urlEqualTo("/latest?base=USD"))
            .willReturn {
              aResponse()
                .withStatus(422)
                .withBody {
                  s"""{"error":"some error happened"}"""
                }
            }
        }

        val request = HttpRequest(uri = "/rates?base=USD")

        request ~> mainRoute ~> check {
          status should ===(StatusCodes.InternalServerError)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===(
            s"""{"success":false,"message":"fixer api returned an error: some error happened"}"""
          )
        }
      }

      "handle unexpected fixer.io errors" in {
        stubFor {
          get(urlEqualTo("/latest?base=USD"))
            .willReturn {
              aResponse()
                .withStatus(500)
                .withBody {
                  s"""server error"""
                }
            }
        }

        val request = HttpRequest(uri = "/rates?base=USD")

        request ~> mainRoute ~> check {
          status should ===(StatusCodes.InternalServerError)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===(
            s"""{"success":false,"message":"expected status 200 OK, got 500 Internal Server Error"}"""
          )
        }
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
