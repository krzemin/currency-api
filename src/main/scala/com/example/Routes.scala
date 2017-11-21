package com.example

import java.time.{ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import com.example.fixer.Currency
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

  val mainRoute: Route = path("rates") {
    parameters('base) { base =>

      onComplete(fixerClient.getLatestRates(Currency(base))) {
        case Success(ratesResponse) =>

          val result = CurrencyRateResponse(
            base = ratesResponse.base,
            timestamp = ZonedDateTime.now(ZoneId.of("UTC")),
            rates = ratesResponse.rates
          )
          complete(result)

        case Failure(why) =>

          complete(StatusCodes.InternalServerError -> why.getMessage)
      }
    }
  }

}
