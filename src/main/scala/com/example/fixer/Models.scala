package com.example.fixer

import java.time.LocalDate

case class Currency(symbol: String) extends AnyVal

case class RatesResponse(base: Currency,
                         date: LocalDate,
                         rates: Map[Currency, BigDecimal])

