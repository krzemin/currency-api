package com.example.fixer

import java.time.LocalDate

import com.example.models.Currency

case class FixerRatesResponse(base: Currency,
                              date: LocalDate,
                              rates: Map[Currency, BigDecimal])

