package com.example.alfa69

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import com.example.alfa69.WeatherService

@Composable
fun WeatherTimeWidget() {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var currentDay by remember { mutableStateOf("") }
    
    var weatherData by remember { mutableStateOf(WeatherService.getSimulatedWeatherData()) }
    
    // Actualizar la hora cada segundo y el clima cada 5 minutos
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEEE", Locale("es", "ES"))
            
            currentTime = timeFormat.format(now.time)
            currentDate = dateFormat.format(now.time)
            currentDay = dayFormat.format(now.time).capitalize()
            
            // Actualizar clima cada 5 minutos
            if (now.get(Calendar.MINUTE) % 5 == 0 && now.get(Calendar.SECOND) == 0) {
                weatherData = WeatherService.getSimulatedWeatherData()
            }
            
            delay(1000)
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Información de tiempo
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = currentTime,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = currentDay,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Start
                )
                Text(
                    text = currentDate,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Start
                )
            }
            
            // Información del clima
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = weatherData["icon"] ?: "☀️",
                        fontSize = 32.sp
                    )
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = weatherData["temp"] ?: "22°C",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = weatherData["condition"] ?: "Soleado",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "Humedad: ${weatherData["humidity"] ?: "65%"}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
} 