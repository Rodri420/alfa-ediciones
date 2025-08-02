# Widget de Clima y Hora

## Descripción
Se ha agregado un widget en la parte superior de la aplicación que muestra:
- **Hora actual** (actualizada cada segundo)
- **Fecha actual** 
- **Día de la semana**
- **Información del clima** (temperatura, condición, humedad)

## Características

### Información de Tiempo
- Hora en formato HH:mm:ss
- Día de la semana en español
- Fecha en formato dd/MM/yyyy
- Se actualiza automáticamente cada segundo

### Información del Clima
- Temperatura en grados Celsius
- Condición climática (Soleado, Parcialmente nublado, etc.)
- Humedad en porcentaje
- Icono representativo del clima
- Los datos se simulan basándose en la hora del día y la estación del año

### Diseño
- Card con fondo azul oscuro (#2C3E50)
- Bordes redondeados
- Elevación para efecto de sombra
- Texto en blanco con diferentes opacidades para jerarquía visual

## Archivos Modificados

1. **MainActivity.kt**: Se agregó el widget en la parte superior de la aplicación
2. **WeatherTimeWidget.kt**: Componente Compose que renderiza el widget
3. **WeatherService.kt**: Servicio que proporciona datos simulados del clima
4. **build.gradle**: Se agregaron dependencias para futuras implementaciones de API real
5. **AndroidManifest.xml**: Se agregaron permisos para ubicación e internet

## Implementación Futura

Para obtener datos reales del clima, necesitarías:

1. **Obtener una API key** de OpenWeatherMap (https://openweathermap.org/api)
2. **Reemplazar la API key** en `WeatherService.kt`
3. **Implementar la obtención de ubicación** usando Google Play Services
4. **Hacer llamadas reales a la API** en lugar de usar datos simulados

## Uso

El widget se muestra automáticamente en la parte superior de todas las pantallas de la aplicación. No requiere configuración adicional.

## Personalización

Puedes personalizar:
- Colores del widget modificando `Color(0xFF2C3E50)`
- Tamaños de fuente ajustando los valores `sp`
- Espaciado modificando los valores `dp`
- Frecuencia de actualización del clima cambiando la condición en `LaunchedEffect` 