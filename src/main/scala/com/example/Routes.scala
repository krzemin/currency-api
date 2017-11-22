package com.example

import java.time.{ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.{PathMatchers, RejectionHandler, Route}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import com.example.models.{Currency, CurrencyRatesResponse, FailedResponse, SuccessResponse}
import de.heikoseeberger.akkahttpcirce._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.java8.time._
import com.example.utils.CirceValueClass._
import com.example.utils.CirceValueClassKeyEncoder._

import scala.util.{Failure, Success}
import scala.concurrent.duration._

class Routes(fixerClient: fixer.ApiClient,
             currencyWatcher: publisher.CurrencyWatcher,
             currentTimeProvider: () => ZonedDateTime = () => ZonedDateTime.now)
            (implicit sys: ActorSystem, mat: Materializer) extends FailFastCirceSupport {

  val log = Logging(sys, classOf[Routes])

  private val stringToZonedDateTime = Unmarshaller.strict[String, ZonedDateTime](ZonedDateTime.parse)

  val ratesQueryRoute: Route = path("rates") {
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
            SuccessResponse {
              CurrencyRatesResponse(
                base = ratesResponse.base,
                timestamp = timestamp.getOrElse(currentTimeProvider()).toInstant.atZone(ZoneId.of("UTC")),
                rates = ratesResponse.rates
              )
            }
          }

        case Failure(why) =>
          log.error(why, "Fixer client request failed")
          complete(StatusCodes.InternalServerError -> FailedResponse(why.getMessage))
      }
    }
  }

  val publicationRoute: Route = pathPrefix("publication") {
    pathEndOrSingleSlash {
      get {
        complete {
          SuccessResponse {
            currencyWatcher.listAllWatches().mapValues(_.checkInterval.toString())
          }
        }
      }
    } ~
    path(PathMatchers.Segment) { currencySymbol =>
      val currency = Currency(currencySymbol)

      post {
        if(currencyWatcher.startCurrencyObserver(15.seconds, currency)) {
          complete(SuccessResponse(s"Observer for $currencySymbol created with check interval of 5 minutes."))
        } else {
          complete(StatusCodes.PreconditionFailed, FailedResponse(s"Observer for $currencySymbol already exists!"))
        }
      } ~
      delete {
        if(currencyWatcher.stopCurrencyObserver(currency)) {
          complete(SuccessResponse(s"Observer for $currencySymbol deleted successfully."))
        } else {
          complete(StatusCodes.PreconditionFailed, FailedResponse(s"Observer for $currencySymbol didn't exist!"))
        }
      }
    }

  }

  implicit def rejectionHandler = RejectionHandler.default

  val mainRoute: Route = Route.seal {
    ratesQueryRoute ~ publicationRoute
  }

}
