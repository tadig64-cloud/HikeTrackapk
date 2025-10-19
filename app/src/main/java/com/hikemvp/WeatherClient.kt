package com.hikemvp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hikemvp.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Client météo unifié :
 * - Essaie OpenWeatherMap si une clé est dispo (strings.xml: weather_api_key)
 * - Sinon (ou en cas d'erreur), fallback Open-Meteo (pas de clé, mondial)
 */
object WeatherClient {

    data class CurrentWeather(
        val tempC: Double?,
        val windKmh: Double?,
        val description: String?
    )

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun fetch(context: Context, lat: Double, lon: Double, callback: (CurrentWeather?) -> Unit) {
        val apiKey = try {
            context.getString(R.string.weather_api_key)
        } catch (_: Throwable) {
            "" // pas de clé dans strings → on forcer fallback
        }

        if (apiKey.isNullOrBlank()) {
            fetchOpenMeteo(lat, lon, callback)
            return
        }

        // Tentative OWM
        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&lang=fr&appid=$apiKey"
        io.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"

                val code = conn.responseCode
                if (code in 200..299) {
                    val br = BufferedReader(InputStreamReader(conn.inputStream))
                    val body = br.readText()
                    br.close()

                    val json = JSONObject(body)
                    val mainObj = json.optJSONObject("main")
                    val windObj = json.optJSONObject("wind")
                    val weatherArr = json.optJSONArray("weather")

                    val tempC = mainObj?.optDouble("temp")
                    val windMs = windObj?.optDouble("speed")
                    val windKmh = windMs?.let { it * 3.6 }
                    val desc = if (weatherArr != null && weatherArr.length() > 0)
                        weatherArr.getJSONObject(0).optString("description")
                    else null

                    post { callback(CurrentWeather(tempC, windKmh, desc)) }
                } else {
                    Log.w("WeatherClient", "OWM HTTP $code → fallback Open-Meteo")
                    fetchOpenMeteo(lat, lon, callback)
                }
            } catch (e: Exception) {
                Log.e("WeatherClient", "OWM error → fallback Open-Meteo", e)
                fetchOpenMeteo(lat, lon, callback)
            } finally {
                conn?.disconnect()
            }
        }
    }

    // ---- Fallback Open-Meteo (no key, worldwide) ----
    private fun fetchOpenMeteo(lat: Double, lon: Double, callback: (CurrentWeather?) -> Unit) {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,wind_speed_10m" +
                "&timezone=auto&language=${Locale.getDefault().language}"

        io.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code in 200..299) {
                    val br = BufferedReader(InputStreamReader(conn.inputStream))
                    val body = br.readText()
                    br.close()

                    val root = JSONObject(body)
                    val cur = root.optJSONObject("current")
                        ?: root.optJSONObject("current_weather") // compat anciennes versions

                    val tempC = cur?.optDouble("temperature_2m")
                        ?: cur?.optDouble("temperature")
                    val wind = cur?.optDouble("wind_speed_10m")
                        ?: cur?.optDouble("windspeed")
                    val windKmh = wind?.let { it } // Open-Meteo renvoie déjà km/h

                    post { callback(CurrentWeather(tempC, windKmh, null)) }
                } else {
                    Log.w("WeatherClient", "Open-Meteo HTTP $code")
                    post { callback(null) }
                }
            } catch (e: Exception) {
                Log.e("WeatherClient", "Open-Meteo error", e)
                post { callback(null) }
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun toLabel(cw: CurrentWeather?): String =
        cw?.tempC?.let { "Météo : ${it.toInt()}°C" } ?: "Météo : —"

    private fun post(block: () -> Unit) = main.post(block)
}
