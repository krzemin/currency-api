package com.example.publisher

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import akka.stream.scaladsl.Source
import com.example.fixer.ApiClient
import com.example.models.Currency

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class CurrencyWatcher(fixerApiClient: ApiClient,
                      ratesChangeNotifier: RatesChangeNotifier)
                     (implicit sys: ActorSystem, mat: Materializer, ec: ExecutionContext) {

  val log = Logging(sys, classOf[CurrencyWatcher])

  @volatile private var killSwitches: Map[Currency, KillSwitch] = Map.empty

  def startCurrencyObserver(checkEvery: FiniteDuration, base: Currency): Boolean = this.synchronized {

    if(killSwitches.contains(base)) {
      false
    } else {
      val killSwitch = KillSwitches.shared(base.symbol)
      killSwitches += base -> killSwitch

      Source
        .tick(0.seconds, checkEvery, ())
        .mapAsync(1)(_ => fixerApiClient.getLatestRates(base))
        .sliding(2)
        .filter { case Seq(resp1, resp2) => resp1.rates != resp2.rates }
        .mapAsync(1) { case Seq(_, newResp) =>
          ratesChangeNotifier.notifyCurrencyChanged(newResp)
        }
        .via(killSwitch.flow)
        .runForeach { newResp =>
          log.info(s"Detected change in currency rates for base $base at ${ZonedDateTime.now()}")
          log.info(s"New currency rates: ${newResp.rates}")
        }

      true
    }
  }

  def stopCurrencyObserver(base: Currency): Boolean = this.synchronized {
    killSwitches.get(base) match {
      case None =>
        false
      case Some(killSwitch) =>
        killSwitch.shutdown()
        true
    }
  }

  def stopAll(): Unit = this.synchronized {
    killSwitches.values.foreach(_.shutdown())
  }
}
