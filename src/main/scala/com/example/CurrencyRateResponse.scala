package com.example

import java.time.ZonedDateTime

import com.example.fixer.Currency

case class CurrencyRateResponse(base: Currency,
                                timestamp: ZonedDateTime,
                                rates: Map[Currency, BigDecimal])
