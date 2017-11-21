package com.example.fixer

import scala.concurrent.Future

trait ApiClient {

  def getLatestRates(base: Currency, target: Option[Currency] = None): Future[RatesResponse]

}
