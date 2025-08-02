package com.example.alfa69

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Call
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import java.util.*

// Modelos de datos para el clima
data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>,
    val name: String
)

data class Main(
    val temp: Double,
    val humidity: Int,
    @SerializedName("feels_like")
    val feelsLike: Double
)

data class Weather(
    val description: String,
    val icon: String
)

// Interfaz para la API del clima
interface WeatherApi {
    @GET("weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "es"
    ): Call<WeatherResponse>
}

// Clase para manejar el servicio del clima
class WeatherService {
    companion object {
        private const val API_KEY = "tu_api_key_aqui" // Reemplaza con tu API key de OpenWeatherMap
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"
        
        fun getWeatherApi(): WeatherApi {
            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
            return retrofit.create(WeatherApi::class.java)
        }
        
        // Función para simular datos del clima basados en la hora del día
        fun getSimulatedWeatherData(): Map<String, String> {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val month = Calendar.getInstance().get(Calendar.MONTH)
            
            return when {
                hour in 6..18 -> {
                    // Día
                    when (month) {
                        in 11..1 -> mapOf("temp" to "15°C", "condition" to "Soleado", "humidity" to "45%", "icon" to "☀️")
                        in 2..4 -> mapOf("temp" to "20°C", "condition" to "Parcialmente nublado", "humidity" to "55%", "icon" to "⛅")
                        in 5..7 -> mapOf("temp" to "25°C", "condition" to "Soleado", "humidity" to "40%", "icon" to "☀️")
                        else -> mapOf("temp" to "22°C", "condition" to "Soleado", "humidity" to "50%", "icon" to "☀️")
                    }
                }
                else -> {
                    // Noche
                    when (month) {
                        in 11..1 -> mapOf("temp" to "8°C", "condition" to "Despejado", "humidity" to "60%", "icon" to "🌙")
                        in 2..4 -> mapOf("temp" to "12°C", "condition" to "Despejado", "humidity" to "65%", "icon" to "🌙")
                        in 5..7 -> mapOf("temp" to "18°C", "condition" to "Despejado", "humidity" to "55%", "icon" to "🌙")
                        else -> mapOf("temp" to "15°C", "condition" to "Despejado", "humidity" to "60%", "icon" to "🌙")
                    }
                }
            }
        }
    }
} 