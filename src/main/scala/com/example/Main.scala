package com.example

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import com.example.publisher.{CurrencyWatcher, RatesChangeNotifierImpl}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("currency-api")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val log = Logging(system, this.getClass)

  val ratesChangeWebhookUri = Uri("http://localhost:7091/webhooks")

  val fixerClient = new fixer.ApiClientImpl
  val ratesChangeNotifier = new RatesChangeNotifierImpl(ratesChangeWebhookUri)
  val currencyWatcher = new CurrencyWatcher(fixerClient, ratesChangeNotifier)

  val routes = new Routes(fixerClient, currencyWatcher)

  val serverBindingFuture: Future[ServerBinding] = Http().bindAndHandle(routes.mainRoute, "localhost", 9000)

  log.info(s"Server online at http://localhost:9000/\nPress RETURN to stop...")

  StdIn.readLine()

  serverBindingFuture
    .flatMap(_.unbind())
    .onComplete { done =>
      currencyWatcher.stopAll()
      done.failed.map { ex => log.error(ex, "Failed unbinding") }
      system.terminate()
    }
}
