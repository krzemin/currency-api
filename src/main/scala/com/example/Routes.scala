package com.example

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer

class Routes(implicit sys: ActorSystem, mat: Materializer) {

  val log = Logging(sys, classOf[Routes])

  val mainRoute: Route = path("rates") {
    complete("test")
  }

}
