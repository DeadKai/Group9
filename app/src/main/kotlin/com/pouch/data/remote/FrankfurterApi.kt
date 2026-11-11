package com.pouch.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface FrankfurterApi {
    @GET("latest")
    suspend fun getLatestRates(
        @Query("base") base: String = "EUR"
    ): Response<ExchangeRateResponse>
}
