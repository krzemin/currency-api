package com.example.publisher

import java.time.ZonedDateTime

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl.Source
import com.example.fixer.ApiClient
import com.example.models.Currency

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object CurrencyWatcher {
  case class CurrencyObservation(killSwitch: SharedKillSwitch, checkInterval: FiniteDuration)
}

class CurrencyWatcher(fixerApiClient: ApiClient,
                      ratesChangeNotifier: RatesChangeNotifier)
                     (implicit sys: ActorSystem, mat: Materializer, ec: ExecutionContext) {

  import CurrencyWatcher._

  val log = Logging(sys, classOf[CurrencyWatcher])

  @volatile private var observersPerCurrency: Map[Currency, CurrencyObservation] = Map.empty

  def startCurrencyObserver(checkInterval: FiniteDuration, base: Currency): Boolean = this.synchronized {

    if(observersPerCurrency.contains(base)) {
      false
    } else {
      val killSwitch = KillSwitches.shared(base.symbol)
      observersPerCurrency += base -> CurrencyObservation(killSwitch, checkInterval)

      Source
        .tick(0.seconds, checkInterval, ())
        .mapAsync(1) { _ =>
          log.info(s"Retrieving latest currency rates for base $base...")
          fixerApiClient.getLatestRates(base)
        }
        .sliding(2)
        .filter { case Seq(resp1, resp2) => resp1.rates != resp2.rates }
        .mapAsync(1) { case Seq(_, newResp) =>
          ratesChangeNotifier.notifyCurrencyChanged(newResp)
        }
        .via(killSwitch.flow)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
        .runForeach { newResp =>
          log.info(s"Detected change in currency rates for base $base at ${ZonedDateTime.now()}")
          log.info(s"New currency rates: ${newResp.rates}")
        }
        .onComplete {
          case Success(Done) =>
            log.info(s"Currency observer stream for $base completed successfully.")
          case Failure(why) =>
            log.error(why, s"Currency observer stream for $base failed!")
        }

      true
    }
  }

  def stopCurrencyObserver(base: Currency): Boolean = this.synchronized {
    observersPerCurrency.get(base) match {
      case None =>
        false
      case Some(currencyWatch) =>
        currencyWatch.killSwitch.shutdown()
        observersPerCurrency -= base
        true
    }
  }

  def stopAll(): Unit = this.synchronized {
    observersPerCurrency.values.foreach(_.killSwitch.shutdown())
    observersPerCurrency = Map.empty
  }

  def listAllObservers(): Map[Currency, CurrencyObservation] =
    observersPerCurrency
}
