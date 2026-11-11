package com.pouch.data.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.pouch.data.remote.ExchangeRateResponse
import com.pouch.data.remote.FrankfurterApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CurrencyRepository(context: Context) {

    private val prefs = context.getSharedPreferences("pouch_currency_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val api: FrankfurterApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.frankfurter.dev/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FrankfurterApi::class.java)
    }

    suspend fun getExchangeRates(base: String = "EUR"): Pair<ExchangeRateResponse?, Boolean> {
        return try {
            val response = api.getLatestRates(base)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                prefs.edit { putString("cached_rates_$base", gson.toJson(body)) }
                Pair(body, false)
            } else {
                getOfflineCachedRates(base)
            }
        } catch (_: Exception) {
            getOfflineCachedRates(base)
        }
    }

    private fun getOfflineCachedRates(base: String): Pair<ExchangeRateResponse?, Boolean> {
        val cachedJson = prefs.getString("cached_rates_$base", null)
        return if (cachedJson != null) {
            val rates = gson.fromJson(cachedJson, ExchangeRateResponse::class.java)
            Pair(rates, true)
        } else {
            val defaultRates = ExchangeRateResponse(
                base = base,
                rates = mapOf("USD" to 1.08, "GBP" to 0.85, "JPY" to 160.0)
            )
            Pair(defaultRates, true)
        }
    }
}
