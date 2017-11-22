package com.example.publisher

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, Uri}
import akka.stream.Materializer
import com.example.fixer.FixerRatesResponse
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.java8.time._
import com.example.utils.CirceValueClass._
import com.example.utils.CirceValueClassKeyEncoder._

import scala.concurrent.{ExecutionContext, Future}

class RatesChangeNotifierImpl(webhookUri: Uri = Uri("http://localhost:7091/webhooks"))
                             (implicit sys: ActorSystem, mat: Materializer, ec: ExecutionContext)
  extends RatesChangeNotifier {

  def notifyCurrencyChanged(fixerRatesResponse: FixerRatesResponse): Future[FixerRatesResponse] = {

    val jsonEntity = HttpEntity(ContentTypes.`application/json`, fixerRatesResponse.asJson.noSpaces)
    val request = HttpRequest(uri = webhookUri, entity = jsonEntity)
    Http().singleRequest(request).map(_ => fixerRatesResponse)
  }
}
