package com.example

import java.time.{ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import com.example.models.{Currency, CurrencyRatesResponse}
import de.heikoseeberger.akkahttpcirce._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.java8.time._
import com.example.utils.CirceValueClass._
import com.example.utils.CirceValueClassKeyEncoder._

import scala.util.{Failure, Success}

class Routes(fixerClient: fixer.ApiClient)
            (implicit sys: ActorSystem, mat: Materializer) extends FailFastCirceSupport {

  val log = Logging(sys, classOf[Routes])

  private val stringToZonedDateTime = Unmarshaller.strict[String, ZonedDateTime](ZonedDateTime.parse)

  val mainRoute: Route = path("rates") {
    parameters('base, 'target.?, 'timestamp.as(stringToZonedDateTime).?) { (base, target, timestamp) =>

      val baseCurrency = Currency(base)
      val targetCurrency = target.map(Currency)
      val dateOpt = timestamp.map(_.toInstant.atZone(ZoneId.of("UTC")).toLocalDate)

      val fixerResponseF = dateOpt match {
        case None =>
          fixerClient.getLatestRates(baseCurrency, targetCurrency)
        case Some(date) =>
          fixerClient.getRatesAt(date, baseCurrency, targetCurrency)
      }

      onComplete(fixerResponseF) {
        case Success(ratesResponse) =>
          complete {
            CurrencyRatesResponse(
              base = ratesResponse.base,
              timestamp = timestamp.getOrElse(ZonedDateTime.now).toInstant.atZone(ZoneId.of("UTC")),
              rates = ratesResponse.rates
            )
          }

        case Failure(why) =>
          log.error(why, "Fixer client request failed")
          complete(StatusCodes.InternalServerError -> why.getMessage)
      }
    }
  }

}
