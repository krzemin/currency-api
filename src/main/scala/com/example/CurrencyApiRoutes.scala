package com.example

import java.time.{ZoneId, ZonedDateTime}
import javax.ws.rs.Path

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.{PathMatchers, RejectionHandler, Route}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.java8.time._
import com.example.utils.CirceValueClass._
import com.example.utils.CirceValueClassKeyEncoder._
import com.example.models._
import io.swagger.annotations._

import scala.util.{Failure, Success}
import scala.concurrent.duration._

@Api(value = "/", produces = "application/json")
@Path("/")
class CurrencyApiRoutes(fixerClient: fixer.ApiClient,
                        currencyWatcher: publisher.CurrencyWatcher,
                        currentTimeProvider: () => ZonedDateTime = () => ZonedDateTime.now)
                       (implicit sys: ActorSystem, mat: Materializer) extends FailFastCirceSupport {

  val log = Logging(sys, classOf[CurrencyApiRoutes])

  private val stringToZonedDateTime = Unmarshaller.strict[String, ZonedDateTime](ZonedDateTime.parse)

  @Path("/rates")
  @ApiOperation(
    value = "retrieve currency rates",
    notes = "if 'timestamp' parameter is set, it returns currency rates at this timestamp, otherwise it returns latest rates",
    httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "base", value = "base currency symbol", required = true, defaultValue = "USD", dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "target", value = "target currency symbol", required = false, defaultValue = "PLN", dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "timestamp", value = "currency rates timestamp", required = false, defaultValue = "2017-11-22T16:12:28.175Z", dataType = "string", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Currency rates", response = classOf[CurrencyRatesResponse.SuccessResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[FailedResponse])
  ))
  def ratesQueryRoute: Route = path("rates") {
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
            CurrencyRatesResponse.SuccessResponse {
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

  @Path("/publication")
  @ApiOperation(
    value = "list current currency rate change observers",
    notes = "if 'timestamp' parameter is set, it returns currency rates at this timestamp, otherwise it returns latest rates",
    httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Currency rates", response = classOf[ObjectResponse])
  ))
  def listPublicationsRoute: Route =
    get {
      complete {
        ObjectResponse {
          currencyWatcher.listAllObservers().map { case (currency, observation) =>
            currency.symbol -> observation.checkInterval.toString()
          }
        }
      }
    }

  @Path("/publication/{currencySymbol}")
  @ApiOperation(
    value = "creates new currency rate observer for specified base currency",
    notes = "you can have at most single observer per given currency base",
    httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "currencySymbol", value = "base currency symbol", required = true, defaultValue = "USD", dataType = "string", paramType = "path"),
    new ApiImplicitParam(value = "rate check interval in seconds", required = true, dataType = "integer", defaultValue = "15", paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "currency observer created", response = classOf[StringResponse]),
    new ApiResponse(code = 412, message = "can't create currency observer", response = classOf[FailedResponse])
  ))
  def createPublicationRoute(@ApiParam(hidden = true) currency: Currency): Route =
    post {
      entity(as[Int]) { checkIntervalSecs =>
        if(currencyWatcher.startCurrencyObserver(checkIntervalSecs.seconds, currency)) {
          complete(StringResponse(s"Observer for ${currency.symbol} created with check interval of $checkIntervalSecs seconds."))
        } else {
          complete(StatusCodes.PreconditionFailed, FailedResponse(s"Observer for ${currency.symbol} already exists!"))
        }
      }
    }

  @Path("/publication/{currencySymbol}")
  @ApiOperation(
    value = "deletes currency rate observer for specified base currency",
    notes = "it deletes observer only if it exists",
    httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "currencySymbol", value = "base currency symbol", required = true, defaultValue = "USD", dataType = "string", paramType = "path"),
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "currency observer created", response = classOf[StringResponse]),
    new ApiResponse(code = 412, message = "can't create currency observer", response = classOf[FailedResponse])
  ))
  def deletePublicationRoute(@ApiParam(hidden = true) currency: Currency): Route =
    delete {
      if(currencyWatcher.stopCurrencyObserver(currency)) {
        complete(StringResponse(s"Observer for ${currency.symbol} deleted successfully."))
      } else {
        complete(StatusCodes.PreconditionFailed, FailedResponse(s"Observer for ${currency.symbol} doesn't exist!"))
      }
    }

  def publicationRoute: Route = pathPrefix("publication") {
    pathEndOrSingleSlash {
      listPublicationsRoute
    } ~
    path(PathMatchers.Segment) { currencySymbol =>
      val currency = Currency(currencySymbol)
      createPublicationRoute(currency) ~ deletePublicationRoute(currency)
    }
  }

  implicit def rejectionHandler = RejectionHandler.default

  val mainRoute: Route = Route.seal {
    ratesQueryRoute ~ publicationRoute
  }

}
