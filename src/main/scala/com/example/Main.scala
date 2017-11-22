package com.example

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.github.swagger.akka.model.Info
import com.github.swagger.akka.{SwaggerHttpService, SwaggerSite}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.StdIn

object Main extends App with SwaggerSite {

  implicit val system: ActorSystem = ActorSystem("currency-api")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val serverHost = "localhost"
  val serverPort = 9000

  val log = Logging(system, this.getClass)

  val fixerClient = new fixer.ApiClientImpl
  val ratesChangeNotifier = new publisher.RatesChangeNotifierImpl()
  val currencyWatcher = new publisher.CurrencyWatcher(fixerClient, ratesChangeNotifier)

  val apiRoutes = new CurrencyApiRoutes(fixerClient, currencyWatcher)

  object SwaggerDocService extends SwaggerHttpService {
    override val apiClasses: Set[Class[_]] = Set(classOf[CurrencyApiRoutes])
    override val host = s"$serverHost:$serverPort"
  }

  val allRoutes = swaggerSiteRoute ~ SwaggerDocService.routes ~ apiRoutes.mainRoute

  val serverBindingFuture: Future[ServerBinding] = Http().bindAndHandle(allRoutes, serverHost, serverPort)

  log.info(s"Server online at http://$serverHost:$serverPort/\nPress RETURN to stop...")

  StdIn.readLine()

  serverBindingFuture
    .flatMap(_.unbind())
    .onComplete { done =>
      currencyWatcher.stopAll()
      done.failed.map { ex => log.error(ex, "Failed unbinding") }
      system.terminate()
    }
}
