package com.example.models

import java.time.ZonedDateTime

case class CurrencyRatesResponse(base: Currency,
                                 timestamp: ZonedDateTime,
                                 rates: Map[Currency, BigDecimal])

object CurrencyRatesResponse {

  case class SuccessResponse(response: CurrencyRatesResponse, success: Boolean = true)
    extends com.example.models.SuccessResponse[CurrencyRatesResponse](response, success)
}
