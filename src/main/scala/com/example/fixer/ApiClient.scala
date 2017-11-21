package com.example.fixer

import java.time.LocalDate

import com.example.models.Currency

import scala.concurrent.Future

trait ApiClient {

  def getLatestRates(base: Currency, target: Option[Currency] = None): Future[FixerRatesResponse]

  def getRatesAt(date: LocalDate, base: Currency, target: Option[Currency] = None): Future[FixerRatesResponse]

}
