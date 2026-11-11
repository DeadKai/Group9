package com.pouch.data.remote

import com.google.gson.annotations.SerializedName

data class ExchangeRateResponse(
    @SerializedName("amount") val amount: Double = 1.0,
    @SerializedName("base") val base: String = "EUR",
    @SerializedName("date") val date: String = "",
    @SerializedName("rates") val rates: Map<String, Double> = emptyMap()
)
