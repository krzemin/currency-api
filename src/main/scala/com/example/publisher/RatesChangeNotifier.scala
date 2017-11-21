package com.example.publisher

import com.example.fixer.FixerRatesResponse

import scala.concurrent.Future

trait RatesChangeNotifier {
  def notifyCurrencyChanged(fixerRatesResponse: FixerRatesResponse): Future[FixerRatesResponse]
}
