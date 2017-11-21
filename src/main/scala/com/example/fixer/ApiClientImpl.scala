package com.example.fixer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.stream.Materializer

import io.circe.generic.auto._
import io.circe.java8.time._
import com.example.utils.CirceValueClass._
import com.example.utils.CirceValueClassKeyEncoder._
import com.example.utils.CirceAkkaSupport._

import scala.concurrent.{ExecutionContext, Future}

class ApiClientImpl(implicit sys: ActorSystem, mat: Materializer, ec: ExecutionContext)
  extends ApiClient {

  private val fixerUriBase = Uri("http://api.fixer.io")

  def getLatestRates(base: Currency, target: Option[Currency] = None): Future[RatesResponse] = {

    val baseQueryParam = Map("base" -> base.symbol)
    val symbolsQueryParamOpt = target.map(curr => Map("symbols" -> curr.symbol)).getOrElse(Map.empty)

    val targetUri = fixerUriBase
      .withPath(Path("/latest"))
      .withQuery(Query(baseQueryParam ++ symbolsQueryParamOpt))

    Http().singleRequest(HttpRequest(uri = targetUri)).flatMap { httpResponse =>
      if(httpResponse.status == StatusCodes.OK) {
        httpResponse.decodeEntityAs[RatesResponse]()
      } else {
        Future.failed(new RuntimeException(s"expected status 200/OK, got ${httpResponse.status}"))
      }
    }
  }
}


