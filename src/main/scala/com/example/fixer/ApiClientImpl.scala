package com.example.fixer

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.stream.Materializer
import com.example.models.Currency
import io.circe.generic.auto._
import io.circe.java8.time._
import com.example.utils.CirceValueClass._
import com.example.utils.CirceValueClassKeyEncoder._
import com.example.utils.CirceAkkaSupport._

import scala.concurrent.{ExecutionContext, Future}

class ApiClientImpl(implicit sys: ActorSystem, mat: Materializer, ec: ExecutionContext)
  extends ApiClient {

  private val fixerUriBase = Uri("http://api.fixer.io")

  def getLatestRates(base: Currency, target: Option[Currency] = None): Future[FixerRatesResponse] = {

    val targetUri = fixerUriBase
      .withPath(Path("/latest"))
      .withQuery(constructQuery(base, target))

    executeFixerRequest(targetUri)
  }

  def getRatesAt(date: LocalDate, base: Currency, target: Option[Currency] = None): Future[FixerRatesResponse] = {

    val targetUri = fixerUriBase
      .withPath(Path("/" + date.toString))
      .withQuery(constructQuery(base, target))

    executeFixerRequest(targetUri)
  }

  private def executeFixerRequest(uri: Uri): Future[FixerRatesResponse] = {
    Http().singleRequest(HttpRequest(uri = uri)).flatMap { httpResponse =>
      if(httpResponse.status == StatusCodes.OK) {
        httpResponse.decodeEntityAs[FixerRatesResponse]()
      } else if(httpResponse.status == StatusCodes.UnprocessableEntity) {
        httpResponse.decodeEntityAs[FixerErrorResponse]().flatMap { errorResponse =>
          Future.failed(new RuntimeException(s"fixer api returned an error: ${errorResponse.error}"))
        }
      } else {
        Future.failed(new RuntimeException(s"expected status 200/OK, got ${httpResponse.status}"))
      }
    }
  }

  private def constructQuery(base: Currency, target: Option[Currency]): Query = {
    val baseQueryParam = Map("base" -> base.symbol)
    val symbolsQueryParamOpt = target.map(curr => Map("symbols" -> curr.symbol)).getOrElse(Map.empty)
    Query(baseQueryParam ++ symbolsQueryParamOpt)
  }

}


