package com.example.alfa69

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alfa69.ui.theme.Alfa69Theme
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import com.example.alfa69.AppDatabase
import com.example.alfa69.Cliente
import com.example.alfa69.ClienteDao
import com.example.alfa69.WeatherTimeWidget

class MainActivity : ComponentActivity() {
    // Cache global para los datos del Excel
    private val dniCache = mutableMapOf<String, String>()
    private var isCacheLoaded = false
    private lateinit var database: AppDatabase
    private lateinit var clienteDao: ClienteDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar base de datos SQLite
        database = AppDatabase.getDatabase(this)
        clienteDao = database.clienteDao()
        
        enableEdgeToEdge()
        setContent {
            Alfa69Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF5F5F5)
                ) {
                    MainContent(
                        dniCache = dniCache,
                        isCacheLoaded = isCacheLoaded,
                        onCacheLoaded = { isCacheLoaded = true },
                        clienteDao = clienteDao
                    )
                }
            }
        }
    }
}

@Composable
fun MainContent(
    dniCache: MutableMap<String, String>,
    isCacheLoaded: Boolean,
    onCacheLoaded: () -> Unit,
    clienteDao: ClienteDao
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Widget de clima y hora en la parte superior
        WeatherTimeWidget()
        
        // Contenido principal
        Box(
            modifier = Modifier.weight(1f)
        ) {
            var showMainMenu by remember { mutableStateOf(false) }
            var showSearchScreen by remember { mutableStateOf(false) }
            var showCuilScreen by remember { mutableStateOf(false) }
            var showPdfScreen by remember { mutableStateOf(false) }
            var showClientDataScreen by remember { mutableStateOf(false) }

            when {
                showPdfScreen -> PdfScreen(onBackClick = { showPdfScreen = false })
                showCuilScreen -> CuilScreen(onBackClick = { showCuilScreen = false })
                showSearchScreen -> SearchScreen(
                    onBackClick = { showSearchScreen = false },
                    dniCache = dniCache,
                    isCacheLoaded = isCacheLoaded,
                    onCacheLoaded = onCacheLoaded,
                    clienteDao = clienteDao
                )
                showClientDataScreen -> ClientDataScreen(onBackClick = { showClientDataScreen = false })
                !showMainMenu -> MainScreen(onIngresarClick = { showMainMenu = true })
                else -> MenuScreen(
                    onSearchClick = { showSearchScreen = true },
                    onCuilClick = { showCuilScreen = true },
                    onPdfClick = { showPdfScreen = true },
                    onClientDataClick = { showClientDataScreen = true }
                )
            }
        }
    }
}

// Función para cargar datos desde Excel a SQLite
suspend fun cargarDatosDesdeExcel(context: Context, clienteDao: ClienteDao) {
    withContext(Dispatchers.IO) {
        try {
            println("DEBUG: Iniciando carga de datos desde Excel a SQLite...")
            
            // Verificar si ya hay datos en SQLite
            val count = clienteDao.contarClientes()
            println("DEBUG: Registros actuales en SQLite: $count")
            if (count > 0) {
                println("DEBUG: Ya hay $count registros en SQLite, saltando carga desde Excel")
                return@withContext
            }
            
            val excelFile = File(context.getExternalFilesDir(null), "base_datos.xlsx")
            println("DEBUG: Ruta del archivo Excel: ${excelFile.absolutePath}")
            println("DEBUG: ¿Existe el archivo Excel? ${excelFile.exists()}")

            if (!excelFile.exists()) {
                println("DEBUG: Archivo Excel no existe, copiando desde assets...")
                val assetFiles = context.assets.list("")
                println("DEBUG: Archivos en assets: ${assetFiles?.joinToString(", ")}")
                
                if (assetFiles == null || !assetFiles.contains("base_datos.xlsx")) {
                    println("DEBUG: ERROR - No se encontró base_datos.xlsx en assets")
                    println("DEBUG: Archivos disponibles en assets: ${assetFiles?.joinToString(", ")}")
                    return@withContext
                }
                
                println("DEBUG: Copiando archivo desde assets...")
                context.assets.open("base_datos.xlsx").use { input ->
                    FileOutputStream(excelFile).use { output -> 
                        input.copyTo(output) 
                    }
                }
                println("DEBUG: Archivo copiado exitosamente")
                println("DEBUG: Tamaño del archivo: ${excelFile.length()} bytes")
            } else {
                println("DEBUG: Archivo Excel ya existe")
                println("DEBUG: Tamaño del archivo: ${excelFile.length()} bytes")
            }

            println("DEBUG: Abriendo archivo Excel...")
            FileInputStream(excelFile).use { fis ->
                val workbook = WorkbookFactory.create(fis)
                println("DEBUG: Workbook creado exitosamente")
                
                val clientes = mutableListOf<Cliente>()
                
                // Procesar TODAS las hojas
                val totalSheets = workbook.numberOfSheets
                println("DEBUG: Total de hojas en el Excel: $totalSheets")
                
                for (sheetIndex in 0 until totalSheets) {
                    val sheet = workbook.getSheetAt(sheetIndex)
                    val sheetName = sheet.sheetName
                    println("DEBUG: Procesando hoja: $sheetName")
                    
                    val totalRows = sheet.lastRowNum + 1
                    println("DEBUG: Total de filas en hoja '$sheetName': $totalRows")
                    
                    var processedRows = 0
                    for (rowIndex in 0 until totalRows) {
                        val row = sheet.getRow(rowIndex)
                        if (row != null) {
                            val dniCell = row.getCell(2)
                            if (dniCell != null) {
                                val dniValue = when (dniCell.cellType) {
                                    CellType.STRING -> dniCell.stringCellValue
                                    CellType.NUMERIC -> dniCell.numericCellValue.toLong().toString()
                                    else -> ""
                                }.trim()
                                
                                if (dniValue.isNotEmpty()) {
                                    val statusCell = row.getCell(3)
                                    if (statusCell != null) {
                                        val statusValue = when (statusCell.cellType) {
                                            CellType.STRING -> statusCell.stringCellValue
                                            CellType.NUMERIC -> statusCell.numericCellValue.toString()
                                            else -> ""
                                        }.trim().lowercase()
                                        
                                        // Solo agregar si tiene estado problemático
                                        if (statusValue.contains("incobrable") || 
                                            statusValue.contains("consultar") || 
                                            statusValue.contains("baja")) {
                                            
                                            val nombre = row.getCell(1)?.stringCellValue ?: ""
                                            
                                            val cliente = Cliente(
                                                dni = dniValue,
                                                nombre = nombre,
                                                estado = statusValue.uppercase(),
                                                hoja = sheetName
                                            )
                                            clientes.add(cliente)
                                            processedRows++
                                            if (processedRows % 100 == 0) {
                                                println("DEBUG: Procesadas $processedRows filas en hoja $sheetName")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    println("DEBUG: Procesadas $processedRows filas problemáticas en hoja $sheetName")
                }
                
                // Insertar todos los clientes en SQLite
                if (clientes.isNotEmpty()) {
                    println("DEBUG: Insertando ${clientes.size} clientes en SQLite...")
                    clienteDao.insertarClientes(clientes)
                    println("DEBUG: Clientes insertados exitosamente en SQLite")
                    
                    // Verificar que se insertaron correctamente
                    val finalCount = clienteDao.contarClientes()
                    println("DEBUG: Total de registros después de inserción: $finalCount")
                } else {
                    println("DEBUG: No se encontraron clientes para insertar")
                }
            }
        } catch (e: Exception) {
            println("DEBUG: ERROR durante la carga: ${e.message}")
            e.printStackTrace()
        }
    }
}

// Función para buscar en SQLite
suspend fun buscarClienteEnSQLite(clienteDao: ClienteDao, dni: String): String {
    return withContext(Dispatchers.IO) {
        try {
            // Búsqueda exacta
            var cliente = clienteDao.buscarPorDni(dni)
            
            // Si no encuentra, buscar sin ceros a la izquierda
            if (cliente == null) {
                val dniWithoutZeros = dni.trimStart('0')
                cliente = clienteDao.buscarPorDni(dniWithoutZeros)
            }
            
            // Si aún no encuentra, buscar con ceros a la izquierda
            if (cliente == null && dni.length < 8) {
                val dniWithZeros = dni.padStart(8, '0')
                cliente = clienteDao.buscarPorDni(dniWithZeros)
            }
            
            // Si aún no encuentra, buscar coincidencia parcial
            if (cliente == null) {
                val clientes = clienteDao.buscarPorDniParcial(dni)
                if (clientes.isNotEmpty()) {
                    cliente = clientes.first()
                }
            }
            
            if (cliente != null) {
                "Estado: ${cliente.estado} (${cliente.hoja})"
            } else {
                "El DNI $dni es APTO."
            }
        } catch (e: Exception) {
            println("DEBUG: Error en búsqueda SQLite: ${e.message}")
            "Error en la búsqueda: ${e.message}"
        }
    }
}

// Función global para cargar el cache (mantener para compatibilidad)
suspend fun loadDniCache(
    context: Context, 
    cache: MutableMap<String, String>, 
    onLoaded: () -> Unit
) {
    // Esta función ahora carga datos a SQLite en lugar de cache en memoria
    withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val clienteDao = database.clienteDao()
            
            // Verificar si ya hay datos en SQLite
            val count = clienteDao.contarClientes()
            println("DEBUG: Registros actuales en SQLite: $count")
            
            if (count > 0) {
                println("DEBUG: SQLite ya tiene $count registros, marcando como cargado")
                println("DEBUG: Cache SQLite cargado")
                onLoaded()
                return@withContext
            }
            
            // Cargar datos desde Excel a SQLite
            cargarDatosDesdeExcel(context, clienteDao)
            
            println("DEBUG: Cache SQLite cargado")
            onLoaded()
        } catch (e: Exception) {
            println("DEBUG: ERROR durante la carga: ${e.message}")
            e.printStackTrace()
        }
    }
}

@Composable
fun MainScreen(onIngresarClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Alfa Ediciones",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50), // Azul oscuro elegante
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onIngresarClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .size(width = 250.dp, height = 60.dp)
                ) {
                    Text(
                        text = "Ingresar",
                        fontSize = 18.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    onBackClick: () -> Unit, 
    dniCache: MutableMap<String, String>,
    isCacheLoaded: Boolean,
    onCacheLoaded: () -> Unit,
    clienteDao: ClienteDao
) {
    var dni by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var debugInfo by remember { mutableStateOf("") }
    var searchEnabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Cargar cache si no está cargado
    LaunchedEffect(Unit) {
        if (!isCacheLoaded) {
            println("DEBUG: Iniciando carga de datos...")
            isLoading = true
            debugInfo = "Iniciando carga..."
            
            scope.launch {
                try {
                    // Verificar primero si ya hay datos en SQLite
                    val count = clienteDao.contarClientes()
                    println("DEBUG: Verificando registros en SQLite: $count")
                    
                    if (count > 0) {
                        println("DEBUG: SQLite ya tiene $count registros, marcando como cargado")
                        debugInfo = "SQLite ya cargado: $count registros"
                        onCacheLoaded() // Esto actualiza isCacheLoaded a true
                        isLoading = false
                        searchEnabled = true
                        return@launch
                    }
                    
                    println("DEBUG: Llamando a loadDniCache...")
                    debugInfo = "Cargando desde Excel..."
                    
                    loadDniCache(context, dniCache, onCacheLoaded)
                    
                    println("DEBUG: loadDniCache completado")
                    debugInfo = "Carga completada"
                    
                    // Verificar cuántos registros hay en SQLite
                    val finalCount = clienteDao.contarClientes()
                    println("DEBUG: Total de registros en SQLite: $finalCount")
                    debugInfo = "Registros en SQLite: $finalCount"
                    searchEnabled = true
                    
                } catch (e: Exception) {
                    println("DEBUG: ERROR en carga: ${e.message}")
                    debugInfo = "Error: ${e.message}"
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        } else {
            println("DEBUG: Cache ya está cargado")
            debugInfo = "Cache ya cargado"
            searchEnabled = true
        }
    }

    fun searchDni(dniToFind: String): String {
        return if (searchEnabled) {
            // Si ya está cargado, buscar inmediatamente
            scope.launch {
                try {
                    println("DEBUG: Buscando DNI: $dniToFind")
                    val result = buscarClienteEnSQLite(clienteDao, dniToFind.trim())
                    println("DEBUG: Resultado de búsqueda: $result")
                    searchResult = result
                } catch (e: Exception) {
                    println("DEBUG: Error en búsqueda: ${e.message}")
                    searchResult = "Error en búsqueda: ${e.message}"
                }
            }
            "Buscando..."
        } else {
            "Cargando base de datos..."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Búsqueda de Incobrables",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (!isCacheLoaded) {
                    Text(
                        text = "Cargando base de datos...",
                        fontSize = 16.sp,
                        color = Color(0xFF2C3E50),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = debugInfo,
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Botón de forzar recarga
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    debugInfo = "Forzando recarga..."
                                    val database = AppDatabase.getDatabase(context)
                                    val clienteDao = database.clienteDao()
                                    cargarDatosDesdeExcel(context, clienteDao)
                                    onCacheLoaded()
                                    debugInfo = "Recarga completada"
                                } catch (e: Exception) {
                                    debugInfo = "Error en recarga: ${e.message}"
                                }
                            }
                        },
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Text("Forzar recarga", fontSize = 14.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = dni,
                    onValueChange = { dni = it },
                    label = { Text("Ingrese DNI a buscar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = searchEnabled || debugInfo.contains("SQLite ya cargado")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (searchEnabled && dni.isNotEmpty()) {
                            searchDni(dni)
                        }
                    },
                    enabled = dni.isNotEmpty() && searchEnabled,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Buscar DNI",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                searchResult?.let { result ->
                    Text(
                        text = result,
                        color = if (result.contains("APTO")) Color(0xFF006400) else Color(0xFF8B0000),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFBDBDBD)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Volver",
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun MenuScreen(onSearchClick: () -> Unit, onCuilClick: () -> Unit, onPdfClick: () -> Unit, onClientDataClick: () -> Unit) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Menú Principal",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onSearchClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .size(width = 250.dp, height = 60.dp)
                ) {
                    Text(
                        text = "Búsqueda Incobrables",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = {
                        val url = "https://www.bcra.gob.ar/BCRAyVos/Situacion_Crediticia.asp"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .size(width = 250.dp, height = 60.dp)
                ) {
                    Text(
                        text = "Situación 5",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = onClientDataClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .size(width = 250.dp, height = 60.dp)
                ) {
                    Text(
                        text = "Cargar datos clientes",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(
                    onClick = onCuilClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .size(width = 250.dp, height = 60.dp)
                ) {
                    Text(
                        text = "Sacar CUIL",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onPdfClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .size(width = 250.dp, height = 60.dp)
                ) {
                    Text(
                        text = "Crear PDF",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botón para consultar negativa de ANSES
                Button(
                    onClick = {
                        val url = "https://servicioswww.anses.gob.ar/censite/index.aspx"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .size(width = 250.dp, height = 60.dp)
                ) {
                    Text(
                        text = "Consultar negativa de ANSES",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CuilScreen(onBackClick: () -> Unit) {
    var dni by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Masculino") }
    var resultCuil by remember { mutableStateOf<String?>(null) }

    fun calculateCuil(dni: String, gender: String): String {
        if (dni.length !in 7..8) {
            return "El DNI debe tener 7 u 8 dígitos."
        }
        val paddedDni = dni.padStart(8, '0')
        val prefix = if (gender == "Masculino") "20" else "27"
        val base = prefix + paddedDni
        val weights = listOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

        val sum = base.mapIndexed { index, char ->
            char.toString().toInt() * weights[index]
        }.sum()

        val remainder = sum % 11
        val verifier = when {
            remainder == 0 -> 0
            remainder == 1 -> if (gender == "Masculino") 9 else 4
            else -> 11 - remainder
        }
        
        val finalPrefix = if (remainder == 1) "23" else prefix

        return "$finalPrefix-$paddedDni-$verifier"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cálculo de CUIL",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = dni,
                    onValueChange = { if (it.length <= 8) dni = it.filter { c -> c.isDigit() } },
                    label = { Text("Ingrese DNI (sin puntos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = gender == "Masculino",
                        onClick = { gender = "Masculino" }
                    )
                    Text("Masculino")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = gender == "Femenino",
                        onClick = { gender = "Femenino" }
                    )
                    Text("Femenino")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { resultCuil = calculateCuil(dni, gender) },
                    enabled = dni.length >= 7,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Calcular", fontSize = 16.sp, color = Color.White)
                }

                resultCuil?.let {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "CUIL Calculado:",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Volver", fontSize = 16.sp, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun PdfScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var pdfStatus by remember { mutableStateOf<String?>(null) }

    fun createPdf() {
        val file = File(context.getExternalFilesDir(null), "ejemplo.pdf")
        try {
            val writer = PdfWriter(file)
            val pdf = PdfDocument(writer)
            val document = Document(pdf)
            document.add(Paragraph("¡Hola, Mundo!"))
            document.add(Paragraph("Este es un PDF de ejemplo creado desde la app."))
            document.close()
            pdfStatus = "PDF 'ejemplo.pdf' creado con éxito en la carpeta de la app."
        } catch (e: Exception) {
            e.printStackTrace()
            pdfStatus = "Error al crear PDF: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Creación de PDF",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { createPdf() },
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Generar PDF de Ejemplo", fontSize = 16.sp, color = Color.White)
                }

                pdfStatus?.let { status ->
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = status,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = if (status.startsWith("Error")) Color.Red else Color(0xFF006400)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Volver", fontSize = 16.sp, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun ClientDataScreen(onBackClick: () -> Unit) {
    var numeroEquipo by remember { mutableStateOf("") }
    var nombreApellido by remember { mutableStateOf("") }
    var dni by remember { mutableStateOf("") }
    var cargo by remember { mutableStateOf("") }
    var provincia by remember { mutableStateOf("") }
    var celular by remember { mutableStateOf("") }
    var metodoPago by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Datos del Cliente",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                // 1. Número de equipo
                OutlinedTextField(
                    value = numeroEquipo,
                    onValueChange = { numeroEquipo = it },
                    label = { Text("Número de equipo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 2. Nombre y Apellido
                OutlinedTextField(
                    value = nombreApellido,
                    onValueChange = { nombreApellido = it },
                    label = { Text("Nombre y Apellido") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 3. DNI
                OutlinedTextField(
                    value = dni,
                    onValueChange = { dni = it.filter { c -> c.isDigit() } },
                    label = { Text("DNI") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 4. Cargo
                OutlinedTextField(
                    value = cargo,
                    onValueChange = { cargo = it },
                    label = { Text("Cargo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 5. Provincia
                OutlinedTextField(
                    value = provincia,
                    onValueChange = { provincia = it },
                    label = { Text("Provincia") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 6. Celular
                OutlinedTextField(
                    value = celular,
                    onValueChange = { celular = it.filter { c -> c.isDigit() } },
                    label = { Text("Celular") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // 7. Método de pago
                OutlinedTextField(
                    value = metodoPago,
                    onValueChange = { metodoPago = it },
                    label = { Text("Método de pago") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { /* TODO: Acción de enviar */ },
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Enviar", fontSize = 16.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Volver", fontSize = 16.sp, color = Color.Black)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    Alfa69Theme {
        MainScreen(onIngresarClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun SearchScreenPreview() {
    Alfa69Theme {
        SearchScreen(
            onBackClick = {}, 
            dniCache = mutableMapOf(), 
            isCacheLoaded = false, 
            onCacheLoaded = {},
            clienteDao = AppDatabase.getDatabase(LocalContext.current).clienteDao()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MenuScreenPreview() {
    Alfa69Theme {
        MenuScreen(onSearchClick = {}, onCuilClick = {}, onPdfClick = {}, onClientDataClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun CuilScreenPreview() {
    Alfa69Theme {
        CuilScreen(onBackClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PdfScreenPreview() {
    Alfa69Theme {
        PdfScreen(onBackClick = {})
    }
}