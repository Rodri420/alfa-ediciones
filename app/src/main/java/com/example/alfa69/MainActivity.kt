package com.example.alfa69

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alfa69.ui.theme.Alfa69Theme
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.room.Room
import com.example.alfa69.AppDatabase
import com.example.alfa69.Cliente
import com.example.alfa69.ClienteDao
import com.example.alfa69.WeatherTimeWidget
import android.content.ContentValues
import android.provider.MediaStore
import java.io.OutputStream
import androidx.core.content.FileProvider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// Constantes de administrador
private const val DEFAULT_ADMIN_PASSWORD = "samuelyolde1234"
private const val CODE_CHANGE_PASSWORD = "43965428"
private const val CODE_BLOCK_APP = "12373379"
private const val CODE_UNBLOCK_APP = "nogalito2001"

// Preferencias
private const val PREFS_NAME = "alfa_prefs"
private const val KEY_ADMIN_PASSWORD = "admin_password"
private const val KEY_APP_LOCKED = "app_locked"

private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
private fun getAdminPassword(context: Context): String = getPrefs(context).getString(KEY_ADMIN_PASSWORD, DEFAULT_ADMIN_PASSWORD) ?: DEFAULT_ADMIN_PASSWORD
private fun setAdminPassword(context: Context, newPassword: String) {
    getPrefs(context).edit().putString(KEY_ADMIN_PASSWORD, newPassword).apply()
}
private fun isAppLocked(context: Context): Boolean = getPrefs(context).getBoolean(KEY_APP_LOCKED, false)
private fun setAppLocked(context: Context, locked: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_APP_LOCKED, locked).apply()
}

// Bloqueo global en Firestore
private fun setGlobalLock(locked: Boolean) {
    val db = FirebaseFirestore.getInstance()
    db.collection("config").document("app_state")
        .set(mapOf(
            "locked" to locked,
            "updatedAt" to FieldValue.serverTimestamp()
        ), SetOptions.merge())
}

private fun observeGlobalLock(onChange: (Boolean) -> Unit): ListenerRegistration {
    val db = FirebaseFirestore.getInstance()
    return db.collection("config").document("app_state")
        .addSnapshotListener { snapshot, _ ->
            val locked = snapshot?.getBoolean("locked") ?: false
            onChange(locked)
        }
}

class MainActivity : ComponentActivity() {
    // Cache global para los datos del Excel
    private val dniCache = mutableMapOf<String, String>()
    private var isCacheLoaded = false
    private lateinit var database: AppDatabase
    private lateinit var clienteDao: ClienteDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        
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
    val context = LocalContext.current
    var appLocked by remember { mutableStateOf(isAppLocked(context)) }

    // Escuchar bloqueo global
    DisposableEffect(Unit) {
        val reg = observeGlobalLock { locked ->
            setAppLocked(context, locked)
            appLocked = locked
        }
        onDispose { reg.remove() }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        WeatherTimeWidget()
        Box(
            modifier = Modifier.weight(1f)
        ) {
            var showMainMenu by remember { mutableStateOf(false) }
            var showSearchScreen by remember { mutableStateOf(false) }
            var showCuilScreen by remember { mutableStateOf(false) }
            var showPdfScreen by remember { mutableStateOf(false) }
            var showClientDataScreen by remember { mutableStateOf(false) }
            var showAdminLogin by remember { mutableStateOf(false) }
            var showAdminPanel by remember { mutableStateOf(false) }
            var startWithChange by remember { mutableStateOf(false) }
            var showAdminClients by remember { mutableStateOf(false) }
            var showConsultSale by remember { mutableStateOf(false) }

            when {
                appLocked -> LockedScreen(
                    onUnlock = { code ->
                        if (code == CODE_UNBLOCK_APP) {
                            // Desbloquear globalmente
                            setGlobalLock(false)
                        }
                    }
                )
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
                showAdminLogin -> AdminLoginScreen(
                    onBackClick = { showAdminLogin = false },
                    onSuccess = { showAdminLogin = false; startWithChange = false; showAdminPanel = true },
                    onLockChange = { locked -> setAppLocked(context, locked); appLocked = locked },
                    onOpenChange = { showAdminLogin = false; startWithChange = true; showAdminPanel = true }
                )
                showAdminPanel -> AdminPanelScreen(
                    onBackClick = { showAdminPanel = false },
                    startWithChangePassword = startWithChange,
                    onOpenClients = { showAdminPanel = false; showAdminClients = true }
                )
                showAdminClients -> AdminClientsScreen(onBackClick = { showAdminClients = false })
                showConsultSale -> ConsultSaleScreen(onBackClick = { showConsultSale = false })
                !showMainMenu -> MainScreen(onIngresarClick = { showMainMenu = true })
                else -> MenuScreen(
                    onSearchClick = { showSearchScreen = true },
                    onCuilClick = { showCuilScreen = true },
                    onPdfClick = { showPdfScreen = true },
                    onClientDataClick = { showClientDataScreen = true },
                    onAdminClick = { showAdminLogin = true },
                    onConsultSaleClick = { showConsultSale = true }
                )
            }
        }
    }
}

@Composable
fun LockedScreen(onUnlock: (String) -> Unit) {
    var codigo by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Aplicación bloqueada", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B0000))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it },
            label = { Text("Código de desbloqueo") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onUnlock(codigo) },
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Desbloquear", color = Color.White)
        }
    }
}

@Composable
fun AdminLoginScreen(onBackClick: () -> Unit, onSuccess: () -> Unit, onLockChange: (Boolean) -> Unit, onOpenChange: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ingreso Administrador", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña / Código") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (info.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(info, color = Color(0xFF006400)) }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        when (password) {
                            CODE_BLOCK_APP -> { setGlobalLock(true); info = "Aplicación bloqueada para todos" }
                            CODE_UNBLOCK_APP -> { setGlobalLock(false); info = "Aplicación desbloqueada" }
                            CODE_CHANGE_PASSWORD -> { onOpenChange() }
                            getAdminPassword(context) -> onSuccess()
                            else -> info = "Dato inválido"
                        }
                        password = ""
                    },
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Ingresar", color = Color.White) }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Volver") }
            }
        }
    }
}

@Composable
fun AdminPanelScreen(onBackClick: () -> Unit, startWithChangePassword: Boolean, onOpenClients: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showChange by remember { mutableStateOf(startWithChangePassword) }
    var codeChange by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newPass2 by remember { mutableStateOf("") }
    var changeMsg by remember { mutableStateOf("") }

    var resetting by remember { mutableStateOf(false) }
    var resetMsg by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Panel de Administrador", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showChange = !showChange },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (showChange) "Ocultar cambio de contraseña" else "Cambiar contraseña", color = Color.White) }
                if (showChange) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = codeChange, onValueChange = { codeChange = it }, label = { Text("Código de autorización") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("Nueva contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPass2, onValueChange = { newPass2 = it }, label = { Text("Repetir contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        when {
                            codeChange != CODE_CHANGE_PASSWORD -> changeMsg = "Código incorrecto"
                            newPass.length < 6 -> changeMsg = "La contraseña debe tener al menos 6 caracteres"
                            newPass != newPass2 -> changeMsg = "Las contraseñas no coinciden"
                            else -> {
                                setAdminPassword(context, newPass)
                                changeMsg = "Contraseña actualizada"
                                newPass = ""; newPass2 = ""; codeChange = ""
                            }
                        }
                    }, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Guardar", color = Color.White) }
                    if (changeMsg.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(changeMsg, color = if (changeMsg.contains("actualizada")) Color(0xFF006400) else Color(0xFFF44336)) }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onOpenClients,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Clientes", color = Color.White) }

                Spacer(modifier = Modifier.height(16.dp))
                var creating by remember { mutableStateOf(false) }
                var createMsg by remember { mutableStateOf("") }
                Button(
                    onClick = {
                        if (creating) return@Button
                        creating = true
                        createMsg = "Creando CSV (todos)..."
                        val ctx = context
                        val db = FirebaseFirestore.getInstance()

                        // Acumular documentos de las 3 colecciones
                        val allDocs = mutableListOf<Map<String, Any?>>()
                        var pendientes = 4
                        val done: () -> Unit = {
                            pendientes -= 1
                            if (pendientes == 0) {
                                // Cuando termina la lectura, crear CSV
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val baseDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                                        val outFile = File(baseDir, "clientes.csv")
                                        FileOutputStream(outFile).use { fos ->
                                            fos.writer().use { writer ->
                                                // Sugerir separador y encabezados con nuevas columnas
                                                writer.appendLine("sep=,")
                                                writer.appendLine("DNI,NombreApellido,Provincia,Celular,ImporteTotal,ValorCuota,MetodoPago,DatosPago1,DatosPago2,DatosPago3,VendedorNombre")
                                                allDocs.forEach { d ->
                                                    val dni = (d["dni"] as? String) ?: ""
                                                    val nombre = (d["nombreApellido"] as? String) ?: ""
                                                    val prov = (d["provincia"] as? String) ?: ""
                                                    val cel = (d["celular"] as? String) ?: ""
                                                    val imp = (d["importeTotal"] as? String) ?: ""
                                                    val cuota = (d["valorCuota"] as? String) ?: ""
                                                    val vendedor = (d["vendedorNombre"] as? String) ?: ""
                                                    val metodo = (d["apartado"] as? String)
                                                        ?: when {
                                                            d.containsKey("cbu") -> "CBU"
                                                            d.containsKey("numeroTarjeta") -> "Tarjeta"
                                                            d.containsKey("fechaVenta") -> "MercadoPago"
                                                            d.containsKey("editorial") -> "CL"
                                                            else -> ""
                                                        }
                                                    // Datos de pago por método
                                                    var dp1 = ""; var dp2 = ""; var dp3 = ""
                                                    when (metodo) {
                                                        "CBU" -> { dp1 = (d["cbu"] as? String) ?: "" }
                                                        "Tarjeta" -> {
                                                            dp1 = (d["numeroTarjeta"] as? String) ?: ""
                                                            dp2 = (d["fechaVencimiento"] as? String) ?: ""
                                                            dp3 = (d["codigoTarjeta"] as? String) ?: ""
                                                        }
                                                        "MercadoPago" -> { dp1 = (d["fechaVenta"] as? String) ?: "" }
                                                        "CL" -> { /* sin datos extra */ }
                                                    }
                                                    // Forzar valores sensibles como texto en Excel usando fórmula ="..."
                                                    if (metodo == "CBU" && dp1.isNotEmpty()) {
                                                        dp1 = "=\"${dp1}\""
                                                    }
                                                    if (metodo == "Tarjeta" && dp1.isNotEmpty()) {
                                                        dp1 = "=\"${dp1}\""
                                                    }
                                                    val celText = if (cel.isNotEmpty()) "=\"${cel}\"" else cel
                                                    // No quotear si comienza con '=' para preservar fórmula
                                                    fun q(s: String) = if (s.startsWith("=")) s else "\"" + s.replace("\"", "\"\"") + "\""
                                                    val fields = listOf(dni, nombre, prov, celText, imp, cuota, metodo, dp1, dp2, dp3, vendedor).map { q(it) }
                                                    val line = fields.joinToString(",")
                                                    writer.appendLine(line)
                                                }
                                            }
                                        }

                                        // Copiar a Descargas
                                        var saved = false
                                        try {
                                            val resolver = ctx.contentResolver
                                            val values = ContentValues().apply {
                                                put(MediaStore.Downloads.DISPLAY_NAME, "clientes.csv")
                                                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                                                put(MediaStore.Downloads.IS_PENDING, 1)
                                            }
                                            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                            if (uri != null) {
                                                resolver.openOutputStream(uri)?.use { out ->
                                                    FileInputStream(outFile).use { input -> input.copyTo(out) }
                                                }
                                                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                                                resolver.update(uri, values, null, null)
                                                saved = true
                                            }
                                        } catch (_: Exception) {}

                                        withContext(Dispatchers.Main) {
                                            creating = false
                                            createMsg = if (saved) "CSV creado en Descargas: clientes.csv" else "CSV creado: ${outFile.absolutePath} (no se pudo copiar a Descargas)"
                                            shareFile(ctx, outFile, "clientes.csv", "text/csv")
                                        }
                                    } catch (e: Throwable) {
                                        withContext(Dispatchers.Main) {
                                            creating = false
                                            createMsg = "Error creando CSV: ${e::class.simpleName} ${e.message}"
                                        }
                                    }
                                }
                            }
                        }

                        db.collection("Debito").get()
                            .addOnSuccessListener { s ->
                                s.documents.forEach { d -> allDocs.add(d.data ?: emptyMap()) }
                                done()
                            }
                            .addOnFailureListener { done() }
                        db.collection("tarjeta").get()
                            .addOnSuccessListener { s ->
                                s.documents.forEach { d -> allDocs.add(d.data ?: emptyMap()) }
                                done()
                            }
                            .addOnFailureListener { done() }
                        db.collection("mercadopago").get()
                            .addOnSuccessListener { s ->
                                s.documents.forEach { d -> allDocs.add(d.data ?: emptyMap()) }
                                done()
                            }
                            .addOnFailureListener { done() }
                        db.collection("editorial").get()
                            .addOnSuccessListener { s ->
                                s.documents.forEach { d -> allDocs.add(d.data ?: emptyMap()) }
                                done()
                            }
                            .addOnFailureListener { done() }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (creating) "Creando CSV..." else "Crear CSV (todos)", color = Color.White) }
                if (createMsg.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(createMsg) }

                Spacer(modifier = Modifier.height(24.dp))

                // Actualizar incobrables desde Firebase (Storage)
                Button(
                    onClick = {
                        if (updating) return@Button
                        updating = true
                        updateMsg = "Reseteando datos de clientes (Debito/Tarjeta/MercadoPago)..."
                        val db = FirebaseFirestore.getInstance()
                        val borrarColeccion: (String, () -> Unit) -> Unit = { nombre, onDone ->
                            db.collection(nombre).get()
                                .addOnSuccessListener { snap ->
                                    val batch = db.batch()
                                    snap.documents.forEach { d -> batch.delete(d.reference) }
                                    batch.commit()
                                        .addOnSuccessListener { onDone() }
                                        .addOnFailureListener { onDone() }
                                }
                                .addOnFailureListener { onDone() }
                        }
                        var pendientes = 3
                        val doneParte = {
                            pendientes -= 1
                            if (pendientes == 0) {
                                updating = false
                                updateMsg = "Datos de clientes reseteados (solo Firestore)"
                            }
                        }
                        borrarColeccion("Debito", doneParte)
                        borrarColeccion("tarjeta", doneParte)
                        borrarColeccion("mercadopago", doneParte)
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (updating) "Reseteando..." else "Resetear datos de clientes", color = Color.White) }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (updating) return@Button
                        updating = true
                        updateMsg = "Descargando y actualizando incobrables locales..."
                        try {
                            val storage = FirebaseStorage.getInstance()
                            val ref = storage.reference.child("incobrables/base_datos.xlsx")
                            val localFile = File(context.getExternalFilesDir(null), "base_datos.xlsx")
                            ref.getFile(localFile)
                                .addOnSuccessListener {
                                    val appDb = AppDatabase.getDatabase(context)
                                    val dao = appDb.clienteDao()
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            dao.eliminarTodos()
                                            cargarDatosDesdeExcel(context, dao)
                                            val finalCount = dao.contarClientes()
                                            withContext(Dispatchers.Main) {
                                                updating = false
                                                updateMsg = "Incobrables actualizados. Registros: $finalCount"
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                updating = false
                                                updateMsg = "Error al actualizar incobrables: ${e.message}"
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    updating = false
                                    updateMsg = "Error al descargar: ${e.message}"
                                }
                        } catch (e: Exception) {
                            updating = false
                            updateMsg = "Error: ${e.message}"
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (updating) "Actualizando..." else "Actualizar incobrables", color = Color.White) }
                if (updateMsg.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(updateMsg) }

                Button(
                    onClick = {
                        if (!resetting) {
                            resetting = true
                            resetMsg = "Reseteando datos..."
                            val db = FirebaseFirestore.getInstance()
                            // Borrar colecciones Debito y tarjeta
                            val borrarColeccion: (String, () -> Unit) -> Unit = { nombre, onDone ->
                                db.collection(nombre).get()
                                    .addOnSuccessListener { snap ->
                                        val batch = db.batch()
                                        snap.documents.forEach { d -> batch.delete(d.reference) }
                                        batch.commit().addOnSuccessListener { onDone() }.addOnFailureListener { onDone() }
                                    }
                                    .addOnFailureListener { onDone() }
                            }
                            var pendientes = 2
                            val doneParte = {
                                pendientes -= 1
                                if (pendientes == 0) {
                                    // Borrar tabla local SQLite
                                    val appDb = AppDatabase.getDatabase(context)
                                    val dao = appDb.clienteDao()
                                    scope.launch(Dispatchers.IO) {
                                        try { dao.eliminarTodos() } catch (_: Exception) {}
                                        withContext(Dispatchers.Main) {
                                            resetting = false
                                            resetMsg = "Datos reseteados"
                                        }
                                    }
                                }
                            }
                            borrarColeccion("Debito", doneParte)
                            borrarColeccion("tarjeta", doneParte)
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (resetting) "Reseteando..." else "Resetear datos", color = Color.White) }
                if (resetMsg.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(resetMsg, color = if (resetMsg.contains("reseteados")) Color(0xFF006400) else Color(0xFF8B0000)) }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBackClick, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD)), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Volver") }
            }
        }
    }
}

// Función para guardar cliente en Firebase
fun guardarClienteEnFirebase(
    numeroEquipo: String,
    nombreApellido: String,
    dni: String,
    cargo: String,
    provincia: String,
    celular: String,
    metodoPagoSeleccionado: String,
    cbu: String = "",
    numeroTarjeta: String = "",
    fechaVencimiento: String = "",
    codigoTarjeta: String = "",
    vendedorNombre: String = "",
    vendedorCodigo: String = "",
    importeTotal: String = "",
    valorCuota: String = "",
    fechaVenta: String = ""
) {
    val db = FirebaseFirestore.getInstance()
    val comunes = mapOf(
        "numeroEquipo" to numeroEquipo,
        "nombreApellido" to nombreApellido,
        "dni" to dni,
        "cargo" to cargo,
        "provincia" to provincia,
        "celular" to celular,
        "vendedorNombre" to vendedorNombre,
        "vendedorCodigo" to vendedorCodigo,
        "importeTotal" to importeTotal,
        "valorCuota" to valorCuota,
        "fechaCreacion" to System.currentTimeMillis()
    )
    when (metodoPagoSeleccionado) {
        "CBU" -> {
            val datos = comunes + mapOf("cbu" to cbu)
            db.collection("Debito").add(datos)
        }
        "Tarjeta" -> {
            val datos = comunes + mapOf(
                "numeroTarjeta" to numeroTarjeta,
                "fechaVencimiento" to fechaVencimiento,
                "codigoTarjeta" to codigoTarjeta
            )
            db.collection("tarjeta").add(datos)
        }
        "MercadoPago" -> {
            val datos = comunes + mapOf(
                "fechaVenta" to fechaVenta
            )
            db.collection("mercadopago").add(datos)
        }
        "CL" -> {
            val datos = comunes + mapOf(
                "editorial" to true
            )
            db.collection("editorial").add(datos)
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
fun MenuScreen(onSearchClick: () -> Unit, onCuilClick: () -> Unit, onPdfClick: () -> Unit, onClientDataClick: () -> Unit, onAdminClick: () -> Unit, onConsultSaleClick: () -> Unit) {
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
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
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
                
                // 1) Búsqueda Incobrables
                Button(
                    onClick = onSearchClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier
                        .size(width = 250.dp, height = 60.dp)
                ) { Text("Búsqueda Incobrables", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium) }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 2) Sacar CUIL
                Button(
                    onClick = onCuilClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.size(width = 250.dp, height = 60.dp)
                ) { Text("Sacar CUIL", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium) }

                Spacer(modifier = Modifier.height(20.dp))

                // 3) Situación 5
                Button(
                    onClick = {
                        val url = "https://www.bcra.gob.ar/BCRAyVos/Situacion_Crediticia.asp"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.size(width = 250.dp, height = 60.dp)
                ) { Text("Situación 5", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium) }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 4) Consultar negativa de ANSES
                Button(
                    onClick = {
                        val url = "https://servicioswww.anses.gob.ar/censite/index.aspx"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.size(width = 250.dp, height = 60.dp)
                ) { Text("Consultar negativa de ANSES", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium) }
                
                Spacer(modifier = Modifier.height(20.dp))

                // 5) Consultar venta (nuevo)
                Button(
                    onClick = onConsultSaleClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                    modifier = Modifier.size(width = 250.dp, height = 60.dp)
                ) { Text("Estado de venta", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium) }

                Spacer(modifier = Modifier.height(20.dp))
                
                // 6) Cargar datos clientes (verde)
                Button(
                    onClick = onClientDataClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.size(width = 250.dp, height = 60.dp)
                ) { Text("Cargar datos clientes", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium) }
                
                Spacer(modifier = Modifier.height(20.dp))

                // Administrador
                Button(
                    onClick = onAdminClick,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                    modifier = Modifier.size(width = 250.dp, height = 60.dp)
                ) { Text("Administrador", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold) }
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
    var metodoPagoSeleccionado by remember { mutableStateOf("") }
    var cbu by remember { mutableStateOf("") }
    var numeroTarjeta by remember { mutableStateOf("") }
    var fechaVencimiento by remember { mutableStateOf("") }
    var codigoTarjeta by remember { mutableStateOf("") }
    var vendedorNombre by remember { mutableStateOf("") }
    var vendedorCodigo by remember { mutableStateOf("") }
    var importeTotal by remember { mutableStateOf("") }
    var valorCuota by remember { mutableStateOf("") }
    var fechaVenta by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    var showErrorMessage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
                    onValueChange = { dni = it },
                    label = { Text("DNI") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
                    onValueChange = { celular = it },
                    label = { Text("Celular") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Datos del vendedor
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = vendedorNombre,
                    onValueChange = { vendedorNombre = it },
                    label = { Text("Nombre del vendedor") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = vendedorCodigo,
                    onValueChange = { vendedorCodigo = it.filter { c -> c.isLetterOrDigit() } },
                    label = { Text("Código del vendedor") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                // Importes
                OutlinedTextField(
                    value = importeTotal,
                    onValueChange = { importeTotal = it },
                    label = { Text("Importe total de la venta") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = valorCuota,
                    onValueChange = { valorCuota = it },
                    label = { Text("Valor de la cuota") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // 7. Método de pago
                Text("Método de pago", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = metodoPagoSeleccionado == "CBU",
                        onClick = { metodoPagoSeleccionado = "CBU" }
                    )
                    Text("CBU")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = metodoPagoSeleccionado == "Tarjeta",
                        onClick = { metodoPagoSeleccionado = "Tarjeta" }
                    )
                    Text("Tarjeta")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = metodoPagoSeleccionado == "MercadoPago",
                        onClick = { metodoPagoSeleccionado = "MercadoPago" }
                    )
                    Text("Mercado Pago (MP)")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = metodoPagoSeleccionado == "CL",
                        onClick = { metodoPagoSeleccionado = "CL" }
                    )
                    Text("CL")
                }

                if (metodoPagoSeleccionado == "CBU") {
                    OutlinedTextField(
                        value = cbu,
                        onValueChange = { cbu = it },
                        label = { Text("Número de CBU") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (metodoPagoSeleccionado == "Tarjeta") {
                    OutlinedTextField(
                        value = numeroTarjeta,
                        onValueChange = { numeroTarjeta = it },
                        label = { Text("Número de tarjeta") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fechaVencimiento,
                        onValueChange = { fechaVencimiento = it },
                        label = { Text("Fecha de vencimiento (MM/AA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = codigoTarjeta,
                        onValueChange = { codigoTarjeta = it },
                        label = { Text("Código de seguridad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (metodoPagoSeleccionado == "MercadoPago") {
                    OutlinedTextField(
                        value = fechaVenta,
                        onValueChange = { fechaVenta = it },
                        label = { Text("Fecha de la venta (DD/MM/AAAA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (metodoPagoSeleccionado == "CL") {
                    // No datos adicionales obligatorios
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                // Función para limpiar formulario
                fun limpiarFormulario() {
                    numeroEquipo = ""
                    nombreApellido = ""
                    dni = ""
                    cargo = ""
                    provincia = ""
                    celular = ""
                    vendedorNombre = ""
                    vendedorCodigo = ""
                    importeTotal = ""
                    valorCuota = ""
                    metodoPagoSeleccionado = ""
                    cbu = ""
                    numeroTarjeta = ""
                    fechaVencimiento = ""
                    codigoTarjeta = ""
                    fechaVenta = ""
                }

                // Función para validar campos
                fun validarCampos(): Boolean {
                    if (numeroEquipo.isEmpty()) { errorMessage = "El número de equipo es obligatorio"; showErrorMessage = true; return false }
                    if (nombreApellido.isEmpty()) { errorMessage = "El nombre y apellido es obligatorio"; showErrorMessage = true; return false }
                    if (dni.isEmpty()) { errorMessage = "El DNI es obligatorio"; showErrorMessage = true; return false }
                    if (dni.length < 7) { errorMessage = "El DNI debe tener al menos 7 dígitos"; showErrorMessage = true; return false }
                    if (vendedorNombre.isEmpty()) { errorMessage = "El nombre del vendedor es obligatorio"; showErrorMessage = true; return false }
                    if (vendedorCodigo.isEmpty()) { errorMessage = "El código del vendedor es obligatorio"; showErrorMessage = true; return false }
                    if (importeTotal.isEmpty()) { errorMessage = "El importe total es obligatorio"; showErrorMessage = true; return false }
                    if (valorCuota.isEmpty()) { errorMessage = "El valor de la cuota es obligatorio"; showErrorMessage = true; return false }
                    when (metodoPagoSeleccionado) {
                        "CBU" -> if (cbu.isEmpty()) { errorMessage = "El CBU es obligatorio"; showErrorMessage = true; return false }
                        "Tarjeta" -> if (numeroTarjeta.isEmpty() || fechaVencimiento.isEmpty() || codigoTarjeta.isEmpty()) { errorMessage = "Complete los datos de la tarjeta"; showErrorMessage = true; return false }
                        "MercadoPago" -> if (fechaVenta.isEmpty()) { errorMessage = "Ingrese la fecha de la venta"; showErrorMessage = true; return false }
                        "CL" -> { /* sin campos adicionales obligatorios */ }
                        else -> { errorMessage = "Seleccione un método de pago"; showErrorMessage = true; return false }
                    }
                    return true
                }

                Button(
                    onClick = { 
                        if (validarCampos()) {
                            isLoading = true
                            showErrorMessage = false
                            guardarClienteEnFirebase(
                                numeroEquipo = numeroEquipo,
                                nombreApellido = nombreApellido,
                                dni = dni,
                                cargo = cargo,
                                provincia = provincia,
                                celular = celular,
                                metodoPagoSeleccionado = metodoPagoSeleccionado,
                                cbu = cbu,
                                numeroTarjeta = numeroTarjeta,
                                fechaVencimiento = fechaVencimiento,
                                codigoTarjeta = codigoTarjeta,
                                vendedorNombre = vendedorNombre,
                                vendedorCodigo = vendedorCodigo,
                                importeTotal = importeTotal,
                                valorCuota = valorCuota,
                                fechaVenta = fechaVenta
                            )
                            showSuccessMessage = true
                            limpiarFormulario()
                            isLoading = false
                        }
                    },
                    enabled = !isLoading && numeroEquipo.isNotEmpty() && nombreApellido.isNotEmpty() && dni.isNotEmpty(),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLoading) Color(0xFF95A5A6) else Color(0xFF2C3E50)
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (isLoading) {
                        Text("Enviando...", fontSize = 16.sp, color = Color.White)
                    } else {
                        Text("Enviar", fontSize = 16.sp, color = Color.White)
                    }
                }

                // Mostrar mensaje de éxito
                if (showSuccessMessage) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = "¡Cliente guardado exitosamente!",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    // Ocultar mensaje después de 3 segundos
                    LaunchedEffect(Unit) {
                        delay(3000)
                        showSuccessMessage = false
                    }
                }

                // Mostrar mensaje de error
                if (showErrorMessage) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    // Ocultar mensaje después de 5 segundos
                    LaunchedEffect(Unit) {
                        delay(5000)
                        showErrorMessage = false
                    }
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

// Tipo auxiliar para administración de clientes
data class AdminClientItem(val collection: String, val id: String, val data: Map<String, Any?>)

@Composable
fun AdminClientsScreen(onBackClick: () -> Unit) {
    var items by remember { mutableStateOf(listOf<AdminClientItem>()) }
    var loading by remember { mutableStateOf(true) }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {
        val tmp = mutableListOf<AdminClientItem>()
        var pendientes = 4
        fun done() { pendientes -= 1; if (pendientes == 0) { items = tmp.toList(); loading = false } }
        db.collection("Debito").get()
            .addOnSuccessListener { snap -> snap.documents.forEach { d -> tmp.add(AdminClientItem("Debito", d.id, d.data ?: emptyMap())) }; done() }
            .addOnFailureListener { done() }
        db.collection("tarjeta").get()
            .addOnSuccessListener { snap -> snap.documents.forEach { d -> tmp.add(AdminClientItem("tarjeta", d.id, d.data ?: emptyMap())) }; done() }
            .addOnFailureListener { done() }
        db.collection("mercadopago").get()
            .addOnSuccessListener { snap -> snap.documents.forEach { d -> tmp.add(AdminClientItem("mercadopago", d.id, d.data ?: emptyMap())) }; done() }
            .addOnFailureListener { done() }
        db.collection("editorial").get()
            .addOnSuccessListener { snap -> snap.documents.forEach { d -> tmp.add(AdminClientItem("editorial", d.id, d.data ?: emptyMap())) }; done() }
            .addOnFailureListener { done() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Clientes (Admin)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            Text("Cargando...")
        } else if (items.isEmpty()) {
            Text("Sin registros")
        } else {
            LazyColumn {
                items(items, key = { it.id }) { item ->
                val it = item.data
                var apto by remember(item.id) { mutableStateOf((it["apto"] as? Boolean) ?: false) }
                var apartados by remember(item.id) {
                    val list = (it["apartados"] as? List<*>)?.mapNotNull { v -> v?.toString() }?.toSet()
                    val fallback = (it["apartado"] as? String)?.let { s -> setOf(s) } ?: when (item.collection) {
                        "Debito" -> setOf("CBU")
                        "tarjeta" -> setOf("Tarjeta")
                        else -> setOf("MercadoPago")
                    }
                    mutableStateOf(list ?: fallback)
                }
                var devolucion by remember(item.id) { mutableStateOf((it["devolucion"] as? String).orEmpty()) }
                var saving by remember(item.id) { mutableStateOf(false) }
                var savedOk by remember(item.id) { mutableStateOf(false) }
                val numeroEquipo = (it["numeroEquipo"] as? String).orEmpty()
                val nombreApellido = (it["nombreApellido"] as? String).orEmpty()
                val dni = (it["dni"] as? String).orEmpty()

                // Persistencia basada en múltiples apartados y devolución

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (nombreApellido.isNotEmpty()) Text(nombreApellido, fontWeight = FontWeight.SemiBold)
                        if (dni.isNotEmpty()) Text("DNI: $dni")
                        if (numeroEquipo.isNotEmpty()) Text("N° Equipo: $numeroEquipo")
                        val cargo = (it["cargo"] as? String).orEmpty()
                        val provincia = (it["provincia"] as? String).orEmpty()
                        val celular = (it["celular"] as? String).orEmpty()
                        val vendedorNombreFirebase = (it["vendedorNombre"] as? String).orEmpty()
                        val vendedorCodigo = (it["vendedorCodigo"] as? String).orEmpty()
                        val importeTotal = (it["importeTotal"] as? String).orEmpty()
                        val valorCuota = (it["valorCuota"] as? String).orEmpty()
                        val cbu = (it["cbu"] as? String).orEmpty()
                        val numeroTarjeta = (it["numeroTarjeta"] as? String).orEmpty()
                        val fechaVencimiento = (it["fechaVencimiento"] as? String).orEmpty()
                        val codigoTarjeta = (it["codigoTarjeta"] as? String).orEmpty()
                        val fechaVenta = (it["fechaVenta"] as? String).orEmpty()
                        if (cargo.isNotEmpty()) Text("Cargo: $cargo")
                        if (provincia.isNotEmpty()) Text("Provincia: $provincia")
                        if (celular.isNotEmpty()) Text("Celular: $celular")
                        if (vendedorNombreFirebase.isNotEmpty()) Text("Vendedor: $vendedorNombreFirebase")
                        if (vendedorCodigo.isNotEmpty()) Text("Código Vendedor: $vendedorCodigo")
                        if (importeTotal.isNotEmpty()) Text("Importe Total: $importeTotal")
                        if (valorCuota.isNotEmpty()) Text("Valor Cuota: $valorCuota")
                        if (cbu.isNotEmpty()) Text("CBU: $cbu")
                        if (numeroTarjeta.isNotEmpty()) Text("Tarjeta: $numeroTarjeta")
                        if (fechaVencimiento.isNotEmpty()) Text("Venc.: $fechaVencimiento")
                        if (codigoTarjeta.isNotEmpty()) Text("CVV: $codigoTarjeta")
                        if (fechaVenta.isNotEmpty()) Text("Fecha Venta: $fechaVenta")
                        Spacer(modifier = Modifier.height(8.dp))
                        // Apto
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("APTO:")
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(selected = apto, onClick = { apto = true; savedOk = false }); Text("Sí")
                            Spacer(modifier = Modifier.width(12.dp))
                            RadioButton(selected = !apto, onClick = { apto = false; savedOk = false }); Text("No")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Apartados (múltiple)
                        Text("Apartados:")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val toggle: (String) -> Unit = { opt ->
                                apartados = if (apartados.contains(opt)) apartados - opt else apartados + opt
                                savedOk = false
                            }
                            Checkbox(checked = apartados.contains("CBU"), onCheckedChange = { toggle("CBU") }); Text("CBU")
                            Spacer(modifier = Modifier.width(12.dp))
                            Checkbox(checked = apartados.contains("Tarjeta"), onCheckedChange = { toggle("Tarjeta") }); Text("Tarjeta")
                            Spacer(modifier = Modifier.width(12.dp))
                            Checkbox(checked = apartados.contains("MercadoPago"), onCheckedChange = { toggle("MercadoPago") }); Text("Mercado Pago")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = apartados.contains("Consultar por editorial"),
                                onCheckedChange = {
                                    apartados = if (apartados.contains("Consultar por editorial")) {
                                        apartados - "Consultar por editorial"
                                    } else {
                                        apartados + "Consultar por editorial"
                                    }
                                    savedOk = false
                                }
                            )
                            Text("Consultar por editorial")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Devolución del administrador
                        OutlinedTextField(
                            value = devolucion,
                            onValueChange = { devolucion = it; savedOk = false },
                            label = { Text("Devolución del administrador") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (!saving) {
                                    saving = true
                                    savedOk = false
                                    val update = mapOf(
                                        "apto" to apto,
                                        "apartados" to apartados.toList(),
                                        "devolucion" to devolucion
                                    )
                                    FirebaseFirestore.getInstance()
                                        .collection(item.collection)
                                        .document(item.id)
                                        .set(update, SetOptions.merge())
                                        .addOnSuccessListener { savedOk = true; saving = false }
                                        .addOnFailureListener { saving = false }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = when {
                                saving -> Color(0xFF95A5A6)
                                savedOk -> Color(0xFF4CAF50)
                                else -> Color(0xFF2C3E50)
                            })
                        ) { Text(when {
                            saving -> "Guardando..."
                            savedOk -> "Guardado"
                            else -> "Guardar"
                        }, color = Color.White) }
                    }
                }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackClick, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD)), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Volver") }
    }
}

@Composable
fun ConsultSaleScreen(onBackClick: () -> Unit) {
    var dni by remember { mutableStateOf("") }
    var queryDni by remember { mutableStateOf("") }
    var resultsDebito by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var resultsTarjeta by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var resultsMP by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var msg by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()

    // Listeners en vivo
    DisposableEffect(queryDni) {
        var reg1: ListenerRegistration? = null
        var reg2: ListenerRegistration? = null
        var reg3: ListenerRegistration? = null
        msg = ""
        if (queryDni.isNotEmpty()) {
            reg1 = db.collection("Debito").whereEqualTo("dni", queryDni)
                .addSnapshotListener { snap, _ ->
                    val list = snap?.documents?.map { d ->
                        val base = d.data ?: emptyMap()
                        // Mantener compatibilidad, pero no forzar UI a depender solo de "apartado"
                        if (base.containsKey("apartado") || base.containsKey("apartados")) base else base + mapOf("apartado" to "CBU")
                    } ?: emptyList()
                    resultsDebito = list
                }
            reg2 = db.collection("tarjeta").whereEqualTo("dni", queryDni)
                .addSnapshotListener { snap, _ ->
                    val list = snap?.documents?.map { d ->
                        val base = d.data ?: emptyMap()
                        if (base.containsKey("apartado") || base.containsKey("apartados")) base else base + mapOf("apartado" to "Tarjeta")
                    } ?: emptyList()
                    resultsTarjeta = list
                }
            reg3 = db.collection("mercadopago").whereEqualTo("dni", queryDni)
                .addSnapshotListener { snap, _ ->
                    val list = snap?.documents?.map { d ->
                        val base = d.data ?: emptyMap()
                        if (base.containsKey("apartado") || base.containsKey("apartados")) base else base + mapOf("apartado" to "MercadoPago")
                    } ?: emptyList()
                    resultsMP = list
                }
        } else {
            resultsDebito = emptyList(); resultsTarjeta = emptyList(); resultsMP = emptyList()
        }
        onDispose {
            reg1?.remove(); reg2?.remove(); reg3?.remove()
        }
    }

    val combinedResults = remember(resultsDebito, resultsTarjeta, resultsMP) {
        (resultsDebito + resultsTarjeta + resultsMP)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Estado de venta", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = dni,
            onValueChange = { dni = it.filter { c -> c.isDigit() } },
            label = { Text("DNI") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { if (dni.isNotEmpty()) queryDni = dni },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Buscar", color = Color.White) }
        Spacer(modifier = Modifier.height(12.dp))

        if (queryDni.isNotEmpty() && combinedResults.isEmpty()) {
            Text("Buscando en vivo...")
        } else if (combinedResults.isEmpty() && msg.isNotEmpty()) {
            Text(msg)
        } else {
            combinedResults.forEach { it ->
                val numeroEquipo = (it["numeroEquipo"] as? String).orEmpty()
                val nombreApellido = (it["nombreApellido"] as? String).orEmpty()
                val apartados = (it["apartados"] as? List<*>)?.joinToString(", ") { v -> v?.toString() ?: "" }.orEmpty()
                val apartado = (it["apartado"] as? String).orEmpty()
                val devolucion = (it["devolucion"] as? String).orEmpty()
                val aptoFlag = (it["apto"] as? Boolean) ?: true
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (numeroEquipo.isNotEmpty()) Text("N° Equipo: $numeroEquipo")
                        if (nombreApellido.isNotEmpty()) Text("Cliente: $nombreApellido")
                        val apartadosText = if (apartados.isNotEmpty()) apartados else apartado
                        if (apartadosText.isNotEmpty()) Text("Apartados: $apartadosText")
                        if (!aptoFlag) { Spacer(modifier = Modifier.height(6.dp)); Text("No apto venta", color = Color(0xFF8B0000)) }
                        if (devolucion.isNotEmpty()) { Spacer(modifier = Modifier.height(6.dp)); Text("Devolución: $devolucion") }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackClick, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBDBDBD)), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Volver") }
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
        MenuScreen(onSearchClick = {}, onCuilClick = {}, onPdfClick = {}, onClientDataClick = {}, onAdminClick = {}, onConsultSaleClick = {})
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

private fun shareFile(context: Context, file: File, displayName: String, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            putExtra(Intent.EXTRA_TEXT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir archivo"))
    } catch (e: Exception) {
        // no-op
    }
}