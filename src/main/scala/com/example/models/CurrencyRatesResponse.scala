package com.example.models

import java.time.ZonedDateTime

case class CurrencyRatesResponse(base: Currency,
                                 timestamp: ZonedDateTime,
                                 rates: Map[Currency, BigDecimal])
