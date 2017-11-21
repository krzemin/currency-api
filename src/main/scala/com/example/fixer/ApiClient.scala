package com.example.fixer

import scala.concurrent.Future

trait ApiClient {

  def getLatestRates(base: Currency): Future[RatesResponse]

}
