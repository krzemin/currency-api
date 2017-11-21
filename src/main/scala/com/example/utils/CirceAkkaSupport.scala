package com.example.utils

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import io.circe.Decoder
import io.circe.parser.parse

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object CirceAkkaSupport {

  implicit class CirceEntityConsumer(val httpResponse: HttpResponse) extends AnyVal {

    def decodeEntityAs[T](timeout: FiniteDuration = 5.seconds)
                         (implicit decoder: Decoder[T], mat: Materializer, ec: ExecutionContext): Future[T] = {

      httpResponse.entity.toStrict(timeout).flatMap { strictEntity =>
        parse(strictEntity.data.decodeString("UTF-8")).fold(
          parsingFailure => Future.failed(new RuntimeException(parsingFailure.message)),
          json => json.as[T].fold(
            decodingFailure => Future.failed(new RuntimeException(decodingFailure.message)),
            result => Future.successful(result)
          )
        )
      }
    }

  }

}
