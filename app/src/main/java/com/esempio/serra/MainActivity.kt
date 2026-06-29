package com.esempio.serra

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stayLoggedInPrefs = getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)
        val stayLoggedIn = stayLoggedInPrefs.getBoolean("stay_logged_in", false)

        setContent {
            val context = LocalContext.current
            val sharedPreferences = context.getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)

            MaterialTheme {
                var showAnimation by remember { mutableStateOf(true) }
                var isLoggedIn by remember { mutableStateOf(stayLoggedIn) }

                LaunchedEffect(Unit) {
                    delay(3000)
                    showAnimation = false
                }

                when {
                    showAnimation -> SplashAnimation()
                    isLoggedIn -> Dashboard(onLogout = {
                        isLoggedIn = false
                        sharedPreferences.edit { putBoolean("stay_logged_in", false) }
                    })
                    else -> LoginScreen(onLoginSuccess = {
                        isLoggedIn = true
                        sharedPreferences.edit { putBoolean("stay_logged_in", true) }
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var stayLoggedIn by remember { mutableStateOf(false) }

    val mAuth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Benvenuto nella SegmaSirra",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50),
            modifier = Modifier.padding(bottom = 32.dp)
        )

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(
                focusedIndicatorColor = Color(0xFF4CAF50),
                unfocusedIndicatorColor = Color.Gray,
                focusedLabelColor = Color(0xFF4CAF50),
                unfocusedLabelColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(
                focusedIndicatorColor = Color(0xFF4CAF50),
                unfocusedIndicatorColor = Color.Gray,
                focusedLabelColor = Color(0xFF4CAF50),
                unfocusedLabelColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = stayLoggedIn, onCheckedChange = {
                stayLoggedIn = it
                sharedPreferences.edit { putBoolean("stay_logged_in", it) }
            })
            Text("Resta connesso", color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            onLoginSuccess()
                        } else {
                            errorMessage = "Account non esistente con queste credenziali."
                            showRegisterDialog = true
                        }
                    }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("Accedi", color = Color.White)
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage, color = Color.Red, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isRegistering = !isRegistering }) {
            Text(
                text = if (isRegistering) "Hai già un account? Accedi" else "Non hai un account? Registrati",
                color = Color(0xFF4CAF50)
            )
        }

        if (showRegisterDialog) {
            AlertDialog(
                onDismissRequest = { showRegisterDialog = false },
                title = { Text("Registrazione") },
                text = { Text("Vuoi registrarti con queste credenziali?") },
                confirmButton = {
                    Button(
                        onClick = {
                            mAuth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { regTask ->
                                    if (regTask.isSuccessful) {
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = "Registrazione fallita: ${regTask.exception?.message}"
                                    }
                                }
                            showRegisterDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Registrati", color = Color.White)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showRegisterDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text("Annulla", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
fun SplashAnimation() {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF4CAF50)),
        contentAlignment = Alignment.Center
    ) {
        Text("\uD83C\uDF3F SegmaSirra", fontSize = (48 * scale.value).sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

enum class Screen {
    DATA, CONTROLS, CARDS, SNAKE
}

@Serializable
data class PlantRule(
    val name: String,
    val minSoilHumidity: Double,
    val maxSoilHumidity: Double,
    val minAirHumidity: Double,
    val maxAirHumidity: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
    val minLightLevel: Double,
    val maxLightLevel: Double,
    val actionSoilDry: Action,
    val actionSoilWet: Action,
    val actionAirLow: Action,
    val actionAirHigh: Action,
    val actionTempLow: Action,
    val actionTempHigh: Action,
    val actionLightLow: Action,
    val actionLightHigh: Action,
    var isActive: Boolean = false
)

enum class Action(val displayName: String) {
    IRRIGATE_ON("Accendi Irrigazione"),
    IRRIGATE_OFF("Spegni Irrigazione"),
    HUMIDIFIER_ON("Accendi Umidificatore"),
    HUMIDIFIER_OFF("Spegni umidificatore"),
    FAN_ON("Accendi Ventole"),
    FAN_OFF("Spegni Ventole"),
    LIGHT_ON("Accendi Luci"),
    LIGHT_OFF("Spegni Luci"),
    ROOF_OPEN("Apri Tetto"),
    ROOF_CLOSE("Chiudi Tetto"),
    NONE("Nessuna Azione")
}

@Composable
fun AddPlantDialog(
    onDismiss: () -> Unit,
    onAdd: (PlantRule) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var minSoilHumidity by remember { mutableStateOf("") }
    var minAirHumidity by remember { mutableStateOf("") }
    var minTemperature by remember { mutableStateOf("") }
    var minLightLevel by remember { mutableStateOf("") }
    var maxSoilHumidity by remember { mutableStateOf("") }
    var maxAirHumidity by remember { mutableStateOf("") }
    var maxTemperature by remember { mutableStateOf("") }
    var maxLightLevel by remember { mutableStateOf("") }

    var actionSoilDry by remember { mutableStateOf(Action.IRRIGATE_ON) }
    var actionSoilWet by remember { mutableStateOf(Action.IRRIGATE_OFF) }
    var actionAirLow by remember { mutableStateOf(Action.HUMIDIFIER_ON) }
    var actionAirHigh by remember { mutableStateOf(Action.HUMIDIFIER_OFF) }
    var actionTempLow by remember { mutableStateOf(Action.FAN_ON) }
    var actionTempHigh by remember { mutableStateOf(Action.FAN_OFF) }
    var actionLightLow by remember { mutableStateOf(Action.LIGHT_ON) }
    var actionLightHigh by remember { mutableStateOf(Action.LIGHT_OFF) }

    val actionOptions = Action.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onAdd(
                    PlantRule(
                        name = name,
                        minSoilHumidity = minSoilHumidity.toDoubleOrNull() ?: 30.0,
                        maxSoilHumidity = maxSoilHumidity.toDoubleOrNull() ?: 100.0,
                        minAirHumidity = minAirHumidity.toDoubleOrNull() ?: 30.0,
                        maxAirHumidity = maxAirHumidity.toDoubleOrNull() ?: 100.0,
                        minTemperature = minTemperature.toDoubleOrNull() ?: 15.0,
                        maxTemperature = maxTemperature.toDoubleOrNull() ?: 35.0,
                        minLightLevel = minLightLevel.toDoubleOrNull() ?: 20.0,
                        maxLightLevel = maxLightLevel.toDoubleOrNull() ?: 80.0,
                        actionSoilDry = actionSoilDry,
                        actionSoilWet = actionSoilWet,
                        actionAirLow = actionAirLow,
                        actionAirHigh = actionAirHigh,
                        actionTempLow = actionTempLow,
                        actionTempHigh = actionTempHigh,
                        actionLightLow = actionLightLow,
                        actionLightHigh = actionLightHigh
                    )
                )
                onDismiss()
            }) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Annulla")
            }
        },
        title = { Text("Crea Nuova Card") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val inputModifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Pianta") },
                    modifier = inputModifier
                )

                TextField(
                    value = minSoilHumidity,
                    onValueChange = {
                        minSoilHumidity = it.filter { c -> c.isDigit() || c == '.' }
                            .takeIf { it.toDoubleOrNull() ?: 0.0 <= 100 } ?: minSoilHumidity
                    },
                    label = { Text("Umidità Terreno Min (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = inputModifier
                )

                TextField(
                    value = maxSoilHumidity,
                    onValueChange = {
                        maxSoilHumidity = it.filter { c -> c.isDigit() || c == '.' }
                            .takeIf { it.toDoubleOrNull() ?: 0.0 <= 100 } ?: maxSoilHumidity
                    },
                    label = { Text("Umidità Terreno Max (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = inputModifier
                )

                Text("Azione se terreno secco:")
                DropdownMenuSelector(
                    options = actionOptions.map { it.displayName },
                    selected = actionSoilDry.displayName
                ) { selectedDisplayName ->
                    actionSoilDry = actionOptions.first { it.displayName == selectedDisplayName }
                }

                Text("Azione se terreno troppo umido:")
                DropdownMenuSelector(
                    options = actionOptions.map { it.displayName },
                    selected = actionSoilWet.displayName
                ) { selectedDisplayName ->
                    actionSoilWet = actionOptions.first { it.displayName == selectedDisplayName }
                }

                TextField(
                    value = minAirHumidity,
                    onValueChange = {
                        minAirHumidity = it.filter { c -> c.isDigit() || c == '.' }
                            .takeIf { it.toDoubleOrNull() ?: 0.0 <= 100 } ?: minAirHumidity
                    },
                    label = { Text("Umidità Aria Min (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = inputModifier
                )

                TextField(
                    value = maxAirHumidity,
                    onValueChange = {
                        maxAirHumidity = it.filter { c -> c.isDigit() || c == '.' }
                            .takeIf { (it.toDoubleOrNull() ?: 0.0) <= 100 } ?: maxAirHumidity
                    },
                    label = { Text("Umidità Aria Max (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = inputModifier
                )

                Text("Azione se aria secca:")
                DropdownMenuSelector(
                    options = actionOptions.map { it.displayName },
                    selected = actionAirLow.displayName
                ) { selectedDisplayName ->
                    actionAirLow = actionOptions.first { action -> action.displayName == selectedDisplayName }
                }

                Text("Azione se aria troppo umida:")
                DropdownMenuSelector(
                    options = actionOptions.map { it.displayName },
                    selected = actionAirHigh.displayName
                ) { selectedDisplayName ->
                    actionAirHigh = actionOptions.first { action -> action.displayName == selectedDisplayName }
                }

                TextField(
                    value = minTemperature,
                    onValueChange = { minTemperature = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Temperatura Min (°C)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = inputModifier
                )

                TextField(
                    value = maxTemperature,
                    onValueChange = { maxTemperature = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Temperatura Max (°C)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = inputModifier
                )

                Text("Azione se temperatura bassa:")
                DropdownMenuSelector(
                    options = actionOptions.map { it.displayName },
                    selected = actionTempLow.displayName
                ) { selectedDisplayName ->
                    actionTempLow = actionOptions.first { action -> action.displayName == selectedDisplayName }
                }

                Text("Azione se temperatura alta:")
                DropdownMenuSelector(
                    options = actionOptions.map { it.displayName },
                    selected = actionTempHigh.displayName
                ) { selectedDisplayName ->
                    actionTempHigh = actionOptions.first { action -> action.displayName == selectedDisplayName }
                }

                TextField(
                    value = minLightLevel,
                    onValueChange = {
                        minLightLevel = it.filter { c -> c.isDigit() || c == '.' }
                            .takeIf { it.toDoubleOrNull() ?: 0.0 <= 100 } ?: minLightLevel
                    },
                    label = { Text("Luminosità Min (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = inputModifier
                )

                TextField(
                    value = maxLightLevel,
                    onValueChange = {
                        maxLightLevel = it.filter { c -> c.isDigit() || c == '.' }
                            .takeIf { it.toDoubleOrNull() ?: 0.0 <= 100 } ?: maxLightLevel
                    },
                    label = { Text("Luminosità Max (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = inputModifier
                )

                Text("Azione se luce bassa:")
                DropdownMenuSelector(
                    options = actionOptions.map { it.displayName },
                    selected = actionLightLow.displayName
                ) { selectedDisplayName ->
                    actionLightLow = actionOptions.first { action -> action.displayName == selectedDisplayName }
                }

                Text("Azione se luce alta:")
                DropdownMenuSelector(
                    options = actionOptions.map { it.displayName },
                    selected = actionLightHigh.displayName
                ) { selectedDisplayName ->
                    actionLightHigh = actionOptions.first { action -> action.displayName == selectedDisplayName }
                }
            }
        }
    )
}

@Composable
fun DropdownMenuSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = {
                        onSelect(it)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(onLogout: () -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)

    fun getCurrentDate(): String {
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return format.format(Date())
    }

    fun getCurrentTime(): String {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(Date())
    }

    val initialRules: List<PlantRule> = try {
        val savedJson = sharedPreferences.getString("plantRules", null)
        if (savedJson != null) Json.decodeFromString(savedJson) else emptyList()
    } catch (e: Exception) {
        Log.e("Serra", "Errore nel parsing delle regole: ${e.message}")
        emptyList()
    }

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentScreen by remember { mutableStateOf(Screen.DATA) }

    var lightOn by remember { mutableStateOf(sharedPreferences.getBoolean("lightOn", false)) }
    var fanOn by remember { mutableStateOf(sharedPreferences.getBoolean("fanOn", false)) }
    var roofOpen by remember { mutableStateOf(sharedPreferences.getBoolean("roofOpen", false)) }
    var irrigationOn by remember { mutableStateOf(sharedPreferences.getBoolean("irrigationOn", false)) }
    var humidifierOn by remember { mutableStateOf(sharedPreferences.getBoolean("humidifierOn", false)) }

    var isControllingByMe by remember { mutableStateOf(false) }
    var activeControllerEmail by remember { mutableStateOf<String?>(null) }
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Ignoto"

    var plantRules by remember { mutableStateOf<List<PlantRule>>(initialRules) }
    var showAddPlantDialog by remember { mutableStateOf(false) }

    var lastSensorUpdate by remember { mutableStateOf(0L) }
    var isArduinoAvailable by remember { mutableStateOf(false) }

    var temperature by remember { mutableStateOf("...") }
    var humidity by remember { mutableStateOf("...") }
    var lightLevel by remember { mutableStateOf("...") }
    var soilHumidity by remember { mutableStateOf("...") }
    var remWater by remember { mutableStateOf("...") }
    var isConnected by remember { mutableStateOf(false) }
    var isInternetConnected by remember { mutableStateOf(false) }
    var weatherInfo by remember { mutableStateOf("...") }
    var currentDate by remember { mutableStateOf(getCurrentDate()) }
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    val firebaseUrl = "https://serra-d44cc-default-rtdb.europe-west1.firebasedatabase.app/serra"
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun updateFirebaseCommands() {
        val json = """
        {
          "lightOn": $lightOn,
          "fanOn": $fanOn,
          "roofOpen": $roofOpen,
          "irrigationOn": $irrigationOn,
          "humidifierOn": $humidifierOn
        }
    """.trimIndent()

        val request = Request.Builder()
            .url("$firebaseUrl/commands.json")
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Firebase", "Errore invio: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("Firebase", "Comandi inviati: ${response.body?.string()}")
            }
        })
    }

    fun checkInternetConnection(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun acquireControl() {
        val client = OkHttpClient()
        val json = """
        {
          "active_user": "$currentUserEmail",
          "last_heartbeat": ${System.currentTimeMillis()}
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("$firebaseUrl/control_session.json")
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Firebase", "Errore acquisizione controllo: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                isControllingByMe = true
            }
        })
    }

    LaunchedEffect(Unit) {
        while (true) {
            isInternetConnected = checkInternetConnection()
            currentDate = getCurrentDate()
            currentTime = getCurrentTime()
            delay(5000)
        }
    }

    LaunchedEffect(Unit) {
        val client = OkHttpClient()
        while (true) {
            val request = Request.Builder()
                .url("$firebaseUrl/control_session.json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let {
                        if (it != "null" && it.isNotEmpty()) {
                            try {
                                val json = JSONObject(it)
                                val activeUser = json.optString("active_user")
                                val lastHB = json.optLong("last_heartbeat")
                                val now = System.currentTimeMillis()

                                if (activeUser == currentUserEmail || (now - lastHB) > 120000) {
                                    activeControllerEmail = activeUser
                                    isControllingByMe = (activeUser == currentUserEmail)
                                } else {
                                    activeControllerEmail = activeUser
                                    isControllingByMe = false
                                }
                            } catch (e: Exception) {
                                Log.e("Firebase", "Errore parsing control_session: ${e.message}")
                            }
                        }
                    }
                }
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Firebase", "Errore lettura control_session: ${e.message}")
                }
            })

            if (isControllingByMe) {
                val updateJson = """{"active_user":"$currentUserEmail","last_heartbeat":${System.currentTimeMillis()}}"""
                val updateReq = Request.Builder()
                    .url("$firebaseUrl/control_session.json")
                    .put(updateJson.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(updateReq).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) {}
                })
            }
            delay(10000)
        }
    }

    LaunchedEffect(Unit) {
        val client = OkHttpClient()

        fun fetchSensors() {
            val request = Request.Builder()
                .url("https://serra-d44cc-default-rtdb.europe-west1.firebasedatabase.app/serra/sensors.json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Firebase", "Errore sensori: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let {
                        try {
                            val json = JSONObject(it)
                            lastSensorUpdate = System.currentTimeMillis()
                            temperature = json.optDouble("temperature", Double.NaN).takeIf { !it.isNaN() }?.toString() ?: "..."
                            humidity = json.optDouble("humidity", Double.NaN).takeIf { !it.isNaN() }?.toString() ?: "..."
                            lightLevel = json.optString("light", "...")
                            soilHumidity = json.optDouble("soil", Double.NaN).takeIf { !it.isNaN() }?.toString() ?: "..."
                            remWater = json.optDouble("remWater", Double.NaN).takeIf { !it.isNaN() }?.toString() ?: "..."

                            val temp = temperature.toDoubleOrNull()
                            val airHum = humidity.toDoubleOrNull()
                            val soilHum = soilHumidity.toDoubleOrNull()

                            if (temp == null || airHum == null || soilHum == null) {
                                Log.e("Sensors", "Valori nulli: temp=$temperature, air=$humidity, soil=$soilHumidity")
                                return
                            }

                            plantRules.find { it.isActive }?.let { rule ->
                                var changed = false

                                val newIrrigation = when {
                                    soilHum < rule.minSoilHumidity -> rule.actionSoilDry == Action.IRRIGATE_ON
                                    soilHum > rule.maxSoilHumidity -> rule.actionSoilWet == Action.IRRIGATE_ON
                                    else -> false
                                }
                                if (irrigationOn != newIrrigation) {
                                    irrigationOn = newIrrigation
                                    changed = true
                                }
                                val newHumidifier = when {
                                    airHum < rule.minAirHumidity -> rule.actionAirLow == Action.HUMIDIFIER_ON
                                    airHum > rule.maxAirHumidity -> rule.actionAirHigh == Action.HUMIDIFIER_ON
                                    else -> false
                                }
                                if (humidifierOn != newHumidifier) {
                                    humidifierOn = newHumidifier
                                    changed = true
                                }

                                val newFan = when {
                                    temp < rule.minTemperature -> rule.actionTempLow == Action.FAN_ON
                                    temp > rule.maxTemperature -> rule.actionTempHigh == Action.FAN_ON
                                    else -> false
                                }
                                if (fanOn != newFan) {
                                    fanOn = newFan
                                    changed = true
                                }

                                val newRoofOpen = when {
                                    temp < rule.minTemperature -> rule.actionTempLow == Action.ROOF_OPEN
                                    temp > rule.maxTemperature -> rule.actionTempHigh == Action.ROOF_OPEN
                                    else -> false
                                }
                                if (roofOpen != newRoofOpen) {
                                    roofOpen = newRoofOpen
                                    changed = true
                                }

                                if (changed) {
                                    sharedPreferences.edit {
                                        putBoolean("irrigationOn", irrigationOn)
                                        putBoolean("humidifierOn", humidifierOn)
                                        putBoolean("fanOn", fanOn)
                                        putBoolean("roofOpen", roofOpen)
                                    }
                                    updateFirebaseCommands()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Firebase", "Parsing error: ${e.message}")
                        }
                    }
                }
            })

            val statusRequest = Request.Builder()
                .url("https://serra-d44cc-default-rtdb.europe-west1.firebasedatabase.app/serra/status/isConnected.json")
                .build()

            client.newCall(statusRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    isConnected = false
                }

                override fun onResponse(call: Call, response: Response) {
                    isConnected = response.body?.string() == "true"
                }
            })

            val weatherRequest = Request.Builder()
                .url("https://api.openweathermap.org/data/2.5/weather?q=Milano&appid=97dc1e64fb64a50f2b0d9d9b3070d4b9")
                .build()

            client.newCall(weatherRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Weather", "Errore meteo: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let {
                        try {
                            val json = JSONObject(it)
                            val weatherArray = json.optJSONArray("weather")
                            if (weatherArray != null && weatherArray.length() > 0) {
                                val weatherObj = weatherArray.getJSONObject(0)
                                val description = weatherObj.optString("description", "Meteo non disponibile")
                                weatherInfo = description.replaceFirstChar { it.uppercaseChar() }
                            } else {
                                weatherInfo = "Meteo non disponibile"
                            }
                        } catch (e: Exception) {
                            Log.e("Weather", "Parsing error: ${e.message}")
                        }
                    }
                }
            })
        }

        while (true) {
            fetchSensors()
            delay(3000)
        }
    }
    val activeRule = plantRules.find { it.isActive }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            isArduinoAvailable = (now - lastSensorUpdate) <= 3 * 60 * 1000
            delay(10_000)
        }
    }

    activeRule?.let { rule ->
        val soil = soilHumidity.toDoubleOrNull()
        val air = humidity.toDoubleOrNull()
        val temp = temperature.toDoubleOrNull()

        var changed = false

        soil?.let {
            if (it < rule.minSoilHumidity) {
                val desired = rule.actionSoilDry == Action.IRRIGATE_ON
                if (irrigationOn != desired) {
                    irrigationOn = desired
                    changed = true
                }
            } else if (it > rule.maxSoilHumidity) {
                val desired = rule.actionSoilWet == Action.IRRIGATE_ON
                if (irrigationOn != desired) {
                    irrigationOn = desired
                    changed = true
                }
            } else {
                if (irrigationOn) {
                    irrigationOn = false
                    changed = true
                }
            }
        }

        air?.let {
            if (it < rule.minAirHumidity) {
                val desired = rule.actionAirLow == Action.HUMIDIFIER_ON
                if (humidifierOn != desired) {
                    humidifierOn = desired
                    changed = true
                }
            } else if (it > rule.maxAirHumidity) {
                val desired = rule.actionAirHigh == Action.HUMIDIFIER_ON
                if (humidifierOn != desired) {
                    humidifierOn = desired
                    changed = true
                }
            } else {
                if (humidifierOn) {
                    humidifierOn = false
                    changed = true
                }
            }
        }

        temp?.let {
            if (it < rule.minTemperature) {
                val desiredFan = rule.actionTempLow == Action.FAN_ON
                if (fanOn != desiredFan) {
                    fanOn = desiredFan
                    changed = true
                }
            } else if (it > rule.maxTemperature) {
                val desiredFan = rule.actionTempHigh == Action.FAN_ON
                if (fanOn != desiredFan) {
                    fanOn = desiredFan
                    changed = true
                }
            } else {
                if (fanOn) {
                    fanOn = false
                    changed = true
                }
            }
        }

        temp?.let {
            if (it < rule.minTemperature) {
                val desiredLight = rule.actionTempLow == Action.LIGHT_ON
                if (lightOn != desiredLight) {
                    lightOn = desiredLight
                    changed = true
                }
            } else if (it > rule.maxTemperature) {
                val desiredLight = rule.actionTempHigh == Action.LIGHT_ON
                if (fanOn != desiredLight) {
                    lightOn = desiredLight
                    changed = true
                }
            } else {
                if (lightOn && rule.actionTempLow != Action.LIGHT_ON && rule.actionTempHigh != Action.LIGHT_ON) {
                    lightOn = false
                    changed = true
                }
            }
        }

        val lightValue = when (lightLevel) {
            "low" -> 20.0
            "moderate" -> 50.0
            "high" -> 80.0
            else -> 50.0
        }

        if (lightValue < rule.minLightLevel) {
            val desired = rule.actionLightLow == Action.LIGHT_ON
            if (lightOn != desired) {
                lightOn = desired
                changed = true
            }
        } else if (lightValue > rule.maxLightLevel) {
            val desired = rule.actionLightHigh == Action.LIGHT_ON
            if (lightOn != desired) {
                lightOn = desired
                changed = true
            }
        }

        if (changed) {
            sharedPreferences.edit {
                putBoolean("irrigationOn", irrigationOn)
                putBoolean("humidifierOn", humidifierOn)
                putBoolean("fanOn", fanOn)
                putBoolean("lightOn", lightOn)
            }
            updateFirebaseCommands()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Menu", modifier = Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                NavigationDrawerItem(
                    label = { Text("\uD83D\uDCCA Dati", fontSize = 18.sp) },
                    selected = currentScreen == Screen.DATA,
                    onClick = {
                        currentScreen = Screen.DATA
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("\uD83C\uDF9B Controlli Manuali", fontSize = 18.sp) },
                    selected = currentScreen == Screen.CONTROLS,
                    onClick = {
                        currentScreen = Screen.CONTROLS
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("\uD83D\uDCC2 Cards", fontSize = 18.sp) },
                    selected = currentScreen == Screen.CARDS,
                    onClick = {
                        currentScreen = Screen.CARDS
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("\uD83D\uDC0D Segma Snake", fontSize = 18.sp) },
                    selected = currentScreen == Screen.SNAKE,
                    onClick = {
                        currentScreen = Screen.SNAKE
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("\uD83D\uDED1 Esci", fontSize = 18.sp, color = Color.Red) },
                    selected = false,
                    onClick = {
                        onLogout()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("🌿 SegmaSirra Dashboard") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xFF4CAF50))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("🌐 Internet: ${if (isInternetConnected) "Connesso" else "Non disponibile"}", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "🔌 Arduino: ${if (isArduinoAvailable) "Disponibile" else "Non disponibile"}",
                        fontSize = 12.sp,
                        color = if (isArduinoAvailable) Color(0xFF2E7D32) else Color.Red
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentDate,
                            fontSize = 16.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = currentTime,
                            fontSize = 16.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🌧 Meteo: $weatherInfo",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
                when (currentScreen) {
                    Screen.DATA -> {
                        Text("📈 Dati Ambientali", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                        InfoCard("🌡 Temperatura", "$temperature°C")
                        InfoCard("💧 Umidità Aria", "$humidity%")
                        InfoCard("☀ Luce", lightLevel)
                        InfoCard("🌱 Umidità Terreno", "$soilHumidity%")
                        InfoCard("🚰 Acqua Rimanente", "$remWater %")
                    }
                    Screen.CONTROLS -> {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (!isControllingByMe && activeControllerEmail != null) {
                                Spacer(modifier = Modifier.height(50.dp))
                                Text(
                                    "SegmaSirra occupata 😛",
                                    color = Color.Red,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                                Text("In controllo: $activeControllerEmail", color = Color.Gray, modifier = Modifier.padding(8.dp))
                                Button(onClick = { acquireControl() }, modifier = Modifier.padding(top = 16.dp)) {
                                    Text("Forza Controllo")
                                }
                            } else {
                                if (plantRules.any { it.isActive }) {
                                    Text(
                                        "⚠ Comandi manuali disabilitati: una card è attiva",
                                        color = Color.Red,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }

                                Text("🎮 Controlli Manuali", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Button(
                                        onClick = {
                                            irrigationOn = !irrigationOn
                                            sharedPreferences.edit { putBoolean("irrigationOn", irrigationOn) }
                                            updateFirebaseCommands()
                                        },
                                        enabled = !plantRules.any { it.isActive } && isControllingByMe
                                    ) {
                                        Text(if (irrigationOn) "💧 Irrigazione Attivata" else "💧 Irrigazione Disattivata")
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        humidifierOn = !humidifierOn
                                        sharedPreferences.edit { putBoolean("humidifierOn", humidifierOn) }
                                        updateFirebaseCommands()
                                    },
                                    enabled = !plantRules.any { it.isActive } && isControllingByMe,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp)
                                ) {
                                    Text(if (humidifierOn) "💨 Umidificatore Acceso" else "💨 Umidificatore Spento")
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = {
                                            lightOn = !lightOn
                                            sharedPreferences.edit { putBoolean("lightOn", lightOn) }
                                            updateFirebaseCommands()
                                        },
                                        enabled = !plantRules.any { it.isActive } && isControllingByMe
                                    ) {
                                        Text(if (lightOn) "💡 Luci Accese" else "💡 Luci Spente")
                                    }
                                    Button(
                                        onClick = {
                                            fanOn = !fanOn
                                            sharedPreferences.edit { putBoolean("fanOn", fanOn) }
                                            updateFirebaseCommands()
                                        },
                                        enabled = !plantRules.any { it.isActive } && isControllingByMe
                                    ) {
                                        Text(if (fanOn) "🌀 Ventole Accese" else "🌀 Ventole Spente")
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        roofOpen = !roofOpen
                                        sharedPreferences.edit { putBoolean("roofOpen", roofOpen) }
                                        updateFirebaseCommands()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp),
                                    enabled = !plantRules.any { it.isActive } && isControllingByMe
                                ) {
                                    Text(if (roofOpen) "🛑 Chiudi Tetto" else "☁ Apri Tetto")
                                }
                            }
                        }
                    }
                    Screen.CARDS -> {
                        Text("📂 Automazioni per Piante", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

                        plantRules.forEach { rule ->
                            var showDetailsDialog by remember { mutableStateOf(false) }

                            if (showDetailsDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDetailsDialog = false },
                                    title = { Text("Dettagli - ${rule.name}") },
                                    text = {
                                        Column {
                                            Text("Umidità Terreno: ${rule.minSoilHumidity} - ${rule.maxSoilHumidity}%")
                                            Text("Umidità Aria: ${rule.minAirHumidity} - ${rule.maxAirHumidity}%")
                                            Text("Temperatura: ${rule.minTemperature} - ${rule.maxTemperature}°C")
                                            Text("Azioni:", fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(" - Terreno secco: ${rule.actionSoilDry.displayName}")
                                            Text(" - Terreno umido: ${rule.actionSoilWet.displayName}")
                                            Text(" - Aria bassa: ${rule.actionAirLow.displayName}")
                                            Text(" - Aria alta: ${rule.actionAirHigh.displayName}")
                                            Text(" - Temp. bassa: ${rule.actionTempLow.displayName}")
                                            Text(" - Temp. alta: ${rule.actionTempHigh.displayName}")
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = { showDetailsDialog = false }) {
                                            Text("Chiudi")
                                        }
                                    }
                                )
                            }

                            Card(
                                modifier = Modifier
                                    .then(
                                        if (
                                            rule.isActive &&
                                            (
                                                    (soilHumidity.toDoubleOrNull() ?: 0.0) < rule.minSoilHumidity ||
                                                            (soilHumidity.toDoubleOrNull() ?: 0.0) > rule.maxSoilHumidity ||
                                                            (humidity.toDoubleOrNull() ?: 0.0) < rule.minAirHumidity ||
                                                            (humidity.toDoubleOrNull() ?: 0.0) > rule.maxAirHumidity ||
                                                            (temperature.toDoubleOrNull() ?: 0.0) < rule.minTemperature ||
                                                            (temperature.toDoubleOrNull() ?: 0.0) > rule.maxTemperature
                                                    )
                                        ) Modifier.background(Color(0xFFFFCDD2)) else Modifier
                                    )
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable { showDetailsDialog = true },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rule.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Text("Attiva: ${if (rule.isActive) "Sì" else "No"}", fontSize = 14.sp)
                                    }

                                    Switch(
                                        checked = rule.isActive,
                                        onCheckedChange = { checked ->
                                            val updated = if (checked) {
                                                plantRules.map { it.copy(isActive = false) }
                                                    .map { if (it.name == rule.name) rule.copy(isActive = true) else it }
                                            } else {
                                                plantRules.map { if (it.name == rule.name) rule.copy(isActive = false) else it }
                                            }

                                            plantRules = updated
                                            sharedPreferences.edit {
                                                putString("plantRules", Json.encodeToString(updated))
                                            }
                                        }
                                    )

                                    IconButton(onClick = {
                                        plantRules = plantRules.filterNot { it.name == rule.name }
                                        sharedPreferences.edit {
                                            putString("plantRules", Json.encodeToString(plantRules))
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = Color.Red)
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            FloatingActionButton(
                                onClick = { showAddPlantDialog = true },
                                containerColor = Color(0xFF4CAF50)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Aggiungi Pianta", tint = Color.White)
                            }
                        }

                        if (showAddPlantDialog) {
                            AddPlantDialog(
                                onDismiss = { showAddPlantDialog = false },
                                onAdd = { rule ->
                                    plantRules = plantRules + rule
                                    sharedPreferences.edit {
                                        putString("plantRules", Json.encodeToString(plantRules))
                                    }
                                    showAddPlantDialog = false
                                }
                            )
                        }
                    }
                    Screen.SNAKE -> {
                        SnakeGameScreen()
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xFF4CAF50))
                )
            }
        }
    }
}
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SnakeGameScreen() {
    var snakeSegments by remember { mutableStateOf(listOf(Pair(10, 10))) }
    var food by remember { mutableStateOf(Pair(5, 5)) }
    var direction by remember { mutableStateOf(Pair(0, -1)) }
    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    val gridSize = 20
    LaunchedEffect(gameOver) {
        if (!gameOver) {
            while (true) {
                delay(150)
                val newHead = Pair(
                    (snakeSegments.first().first + direction.first + gridSize) % gridSize,
                    (snakeSegments.first().second + direction.second + gridSize) % gridSize
                )

                if (snakeSegments.contains(newHead)) {
                    gameOver = true
                    break
                }

                val newSegments = mutableListOf(newHead)
                newSegments.addAll(snakeSegments)

                if (newHead == food) {
                    food = Pair((0 until gridSize).random(), (0 until gridSize).random())
                    score++
                } else {
                    newSegments.removeAt(newSegments.size - 1)
                }

                snakeSegments = newSegments
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🌿 Green Snake",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50),
            modifier = Modifier.padding(16.dp)
        )

        Text(
            "Punteggio: $score 💧",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )

        if (gameOver) {
            Text(
                "Game Over!",
                fontSize = 24.sp,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            Button(
                onClick = {
                    snakeSegments = listOf(Pair(10, 10))
                    food = Pair(5, 5)
                    direction = Pair(0, -1)
                    score = 0
                    gameOver = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Rigioca")
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(16.dp)
                    .background(Color(0xFFE8F5E9))
            ) {
                val tileSize = maxWidth / gridSize

                Text(
                    "💧",
                    fontSize = 20.sp,
                    modifier = Modifier.offset(
                        x = tileSize * food.first,
                        y = tileSize * food.second
                    )
                )

                snakeSegments.forEachIndexed { i, s ->
                    Text(
                        if (i == 0) "🌿" else "🍃",
                        fontSize = 20.sp,
                        modifier = Modifier.offset(
                            x = tileSize * s.first,
                            y = tileSize * s.second
                        )
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Button(
                    onClick = { if (direction.second == 0) direction = Pair(0, -1) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("↑", fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    Button(
                        onClick = { if (direction.first == 0) direction = Pair(-1, 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("←", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { if (direction.first == 0) direction = Pair(1, 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("→", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { if (direction.second == 0) direction = Pair(0, 1) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("↓", fontSize = 24.sp)
                }
            }
        }
    }
}
@Composable
fun InfoCard(title: String, value: String) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 16.sp, color = Color.Gray)
        }
    }
}
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Dashboard(onLogout = {})
}
@Preview
@Composable
fun SplashPreview() {
    SplashAnimation()
}