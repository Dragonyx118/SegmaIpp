package com.esempio.serra

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// ==================== ENUMS ====================

enum class AppTheme { LIGHT, DARK, GREEN_NATURE, AURORA, OCEAN }
const val APP_VERSION = 21
enum class Screen {
    DATA, CONTROLS, CARDS, CHARTS, SNAKE, BATTLEPASS, SETTINGS
}

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

// ==================== DATA CLASSES ====================

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

@Serializable
data class BattlePassReward(
    val level: Int,
    val title: String,
    val description: String,
    val icon: String,
    val rewardType: String = "badge",
    var unlocked: Boolean = false
)

@Serializable
data class BattlePassMission(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val targetValue: Int,
    var currentProgress: Int = 0,
    val xpReward: Int,
    var completed: Boolean = false
)

@Serializable
data class UserStats(
    var irrigationCount: Int = 0,
    var plantsCreated: Int = 0,
    var controlChanges: Int = 0,
    var snakeGamesPlayed: Int = 0,
    var totalXPEarned: Int = 0,
    var themesUnlocked: MutableList<String> = mutableListOf("GREEN_NATURE")
)

@Serializable
data class AppSettings(
    var theme: AppTheme = AppTheme.GREEN_NATURE,
    var volume: Float = 0.5f,
    var notificationsEnabled: Boolean = true,
    var language: String = "IT",
    var autoUpdate: Boolean = true,
    var hapticFeedback: Boolean = true
)

@Serializable
data class SensorRecord(
    val timestamp: Long,
    val temperature: Double?,
    val humidity: Double?,
    val soilHumidity: Double?,
    val waterLevel: Double?,
    val lightRaw: String?
)

// ==================== SENSOR HISTORY MANAGER ====================

object SensorHistoryManager {
    private const val PREFS_KEY = "sensor_history"
    private const val MAX_RECORDS = 720

    fun appendRecord(context: Context, temp: Double?, humidity: Double?, soil: Double?, water: Double?, light: String?) {
        val prefs = context.getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)
        val existing = loadHistory(context).toMutableList()
        existing.add(SensorRecord(System.currentTimeMillis(), temp, humidity, soil, water, light))
        val trimmed = if (existing.size > MAX_RECORDS) existing.takeLast(MAX_RECORDS) else existing
        prefs.edit().putString(PREFS_KEY, Json.encodeToString(trimmed)).apply()
    }

    fun loadHistory(context: Context): List<SensorRecord> {
        val prefs = context.getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, null) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyList() }
    }

    fun formatTimestamp(ts: Long, showDate: Boolean = false): String {
        val fmt = if (showDate) "dd/MM HH:mm" else "HH:mm"
        return SimpleDateFormat(fmt, Locale.getDefault()).format(Date(ts))
    }

    fun parseLightValue(raw: String?): Double? {
        if (raw == null) return null
        raw.toDoubleOrNull()?.let { return it }
        return when (raw.lowercase()) {
            "bassa", "low" -> 15.0
            "media", "medium" -> 50.0
            "alta", "high" -> 85.0
            "buio", "dark", "notte", "night" -> 2.0
            else -> null
        }
    }
}

// ==================== NOTIFICATION HELPER ====================

object NotificationHelper {
    const val CHANNEL_ID = "serra_alerts"
    const val CHANNEL_NAME = "Avvisi Serra"
    const val NOTIF_TEMP_HIGH = 1001
    const val NOTIF_TEMP_LOW = 1002
    const val NOTIF_HUMIDITY_HIGH = 1003
    const val NOTIF_HUMIDITY_LOW = 1004
    const val NOTIF_SOIL_HIGH = 1005
    const val NOTIF_SOIL_LOW = 1006
    const val NOTIF_WATER_LOW = 1007
    const val NOTIF_WATER_CRITICAL = 1008
    const val NOTIF_ARDUINO_OFFLINE = 1009

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avvisi su temperatura, umidità e livello acqua della serra"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendNotification(context: Context, notifId: Int, title: String, message: String, priority: Int = NotificationCompat.PRIORITY_HIGH) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!hasPermission) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    fun cancelNotification(context: Context, notifId: Int) {
        NotificationManagerCompat.from(context).cancel(notifId)
    }
}

// ==================== ALERT THRESHOLDS ====================

object AlertThresholds {
    const val TEMP_MAX = 35.0
    const val TEMP_CRITICAL_MAX = 40.0
    const val TEMP_MIN = 5.0
    const val HUMIDITY_MAX = 90.0
    const val HUMIDITY_MIN = 20.0
    const val SOIL_MAX = 85.0
    const val SOIL_MIN = 15.0
    const val WATER_LOW = 25.0
    const val WATER_CRITICAL = 10.0
}

// ==================== FOREGROUND SERVICE ====================

class SensorForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()
    private val firebaseUrl = "https://serra-d44cc-default-rtdb.europe-west1.firebasedatabase.app/serra"

    companion object {
        const val PERSISTENT_NOTIF_ID = 9999
        const val PERSISTENT_CHANNEL_ID = "serra_service"
        const val PERSISTENT_CHANNEL_NAME = "Serra in esecuzione"

        fun start(context: Context) {
            val intent = Intent(context, SensorForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SensorForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                PERSISTENT_CHANNEL_ID,
                PERSISTENT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null) // Fix: setSilent non esiste, si usa setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        NotificationHelper.createChannel(this)
    }



    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildPersistentNotification("Monitoraggio serra in corso...")
        startForeground(PERSISTENT_NOTIF_ID, notification)
        startPolling()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildPersistentNotification(text: String): Notification =
        NotificationCompat.Builder(this, PERSISTENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("🌿 SegmaSirra")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updatePersistentNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(PERSISTENT_NOTIF_ID, buildPersistentNotification(text))
    }

    private fun startPolling() {
        serviceScope.launch {
            val prefs = getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)
            var lastSensorTs = 0L
            val lastNotifTime = mutableMapOf<Int, Long>()
            val NOTIF_COOLDOWN_MS = 60 * 1000L // 60 secondi
            while (isActive) {
                try {
                    val notificationsEnabled = prefs.getBoolean("notificationsEnabled", true)
                    val response = client.newCall(
                        Request.Builder().url("$firebaseUrl/sensors.json").build()
                    ).execute()
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty() && body != "null") {
                        val json = JSONObject(body)
                        lastSensorTs = System.currentTimeMillis()
                        val temp = json.optDouble("temperature", Double.NaN)
                        val airHum = json.optDouble("humidity", Double.NaN)
                        val soilHum = json.optDouble("soil", Double.NaN)
                        val water = json.optDouble("remWater", Double.NaN)
                        val light: String? = json.optString("light").takeIf { it.isNotEmpty() }

                        SensorHistoryManager.appendRecord(
                            context = this@SensorForegroundService,
                            temp = if (temp.isNaN()) null else temp,
                            humidity = if (airHum.isNaN()) null else airHum,
                            soil = if (soilHum.isNaN()) null else soilHum,
                            water = if (water.isNaN()) null else water,
                            light = light
                        )

                        val statusParts = mutableListOf<String>()
                        if (!temp.isNaN()) statusParts.add("🌡️ %.1f°C".format(temp))
                        if (!airHum.isNaN()) statusParts.add("💧 %.0f%%".format(airHum))
                        if (!water.isNaN()) statusParts.add("🚰 %.0f%%".format(water))
                        updatePersistentNotification(
                            statusParts.joinToString("  ").ifEmpty { "In attesa dati..." }
                        )

                        if (notificationsEnabled) checkAndNotify(temp, airHum, soilHum, water, lastNotifTime, NOTIF_COOLDOWN_MS)
                    }

                    val now = System.currentTimeMillis()
                    if (lastSensorTs > 0 && (now - lastSensorTs) >= 3 * 60_000) {
                        if (prefs.getBoolean("notificationsEnabled", true)) {
                            NotificationHelper.sendNotification(
                                this@SensorForegroundService,
                                NotificationHelper.NOTIF_ARDUINO_OFFLINE,
                                "🔌 Arduino Disconnesso",
                                "Nessun dato dalla serra negli ultimi 3 minuti."
                            )
                        }
                    } else if (lastSensorTs > 0) {
                        NotificationHelper.cancelNotification(
                            this@SensorForegroundService,
                            NotificationHelper.NOTIF_ARDUINO_OFFLINE
                        )
                    }
                } catch (e: Exception) {
                    Log.e("SensorService", "Polling error: ${e.message}")
                }
                delay(10_000)
            }
        }
    }

    private fun checkAndNotify(temp: Double, airHum: Double, soilHum: Double, water: Double, lastNotifTime: MutableMap<Int, Long>, cooldownMs: Long) {
        val ctx = this

        val now = System.currentTimeMillis()
        fun canNotify(id: Int): Boolean {
            val last = lastNotifTime[id] ?: 0L
            return if (now - last >= cooldownMs) { lastNotifTime[id] = now; true } else false
        }

        if (!temp.isNaN()) {
            when {
                temp >= AlertThresholds.TEMP_CRITICAL_MAX -> if (canNotify(NotificationHelper.NOTIF_TEMP_HIGH))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_TEMP_HIGH,
                        "🔥 Temperatura Critica!",
                        "Attuale: %.1f°C — Limite: ${AlertThresholds.TEMP_CRITICAL_MAX}°C!".format(temp),
                        NotificationCompat.PRIORITY_MAX)
                temp >= AlertThresholds.TEMP_MAX -> if (canNotify(NotificationHelper.NOTIF_TEMP_HIGH))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_TEMP_HIGH,
                        "🌡️ Temperatura Alta",
                        "Attuale: %.1f°C — Supera i ${AlertThresholds.TEMP_MAX}°C.".format(temp))
                else -> NotificationHelper.cancelNotification(ctx, NotificationHelper.NOTIF_TEMP_HIGH)
            }
            if (temp <= AlertThresholds.TEMP_MIN) {
                if (canNotify(NotificationHelper.NOTIF_TEMP_LOW))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_TEMP_LOW,
                        "❄️ Temperatura Bassa", "Attuale: %.1f°C — Rischio gelata!".format(temp))
            } else NotificationHelper.cancelNotification(ctx, NotificationHelper.NOTIF_TEMP_LOW)
        }
        if (!airHum.isNaN()) {
            if (airHum >= AlertThresholds.HUMIDITY_MAX) {
                if (canNotify(NotificationHelper.NOTIF_HUMIDITY_HIGH))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_HUMIDITY_HIGH,
                        "💧 Umidità Eccessiva", "Umidità: %.0f%% — Rischio muffe!".format(airHum))
            } else NotificationHelper.cancelNotification(ctx, NotificationHelper.NOTIF_HUMIDITY_HIGH)
            if (airHum <= AlertThresholds.HUMIDITY_MIN) {
                if (canNotify(NotificationHelper.NOTIF_HUMIDITY_LOW))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_HUMIDITY_LOW,
                        "🏜️ Umidità Bassa", "Umidità: %.0f%% — Piante a rischio.".format(airHum))
            } else NotificationHelper.cancelNotification(ctx, NotificationHelper.NOTIF_HUMIDITY_LOW)
        }
        if (!soilHum.isNaN()) {
            if (soilHum >= AlertThresholds.SOIL_MAX) {
                if (canNotify(NotificationHelper.NOTIF_SOIL_HIGH))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_SOIL_HIGH,
                        "💦 Terreno Saturo", "Suolo: %.0f%% — Rischio marciume radici.".format(soilHum))
            } else NotificationHelper.cancelNotification(ctx, NotificationHelper.NOTIF_SOIL_HIGH)
            if (soilHum <= AlertThresholds.SOIL_MIN) {
                if (canNotify(NotificationHelper.NOTIF_SOIL_LOW))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_SOIL_LOW,
                        "🌵 Terreno Secco", "Suolo: %.0f%% — Irrigazione necessaria!".format(soilHum))
            } else NotificationHelper.cancelNotification(ctx, NotificationHelper.NOTIF_SOIL_LOW)
        }
        if (!water.isNaN()) {
            when {
                water <= AlertThresholds.WATER_CRITICAL -> if (canNotify(NotificationHelper.NOTIF_WATER_CRITICAL))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_WATER_CRITICAL,
                        "🚨 Acqua Quasi Esaurita!",
                        "Acqua: %.0f%% — Ricarica urgente!".format(water),
                        NotificationCompat.PRIORITY_MAX)
                water <= AlertThresholds.WATER_LOW -> if (canNotify(NotificationHelper.NOTIF_WATER_LOW))
                    NotificationHelper.sendNotification(ctx, NotificationHelper.NOTIF_WATER_LOW,
                        "🚰 Livello Acqua Basso",
                        "Acqua: %.0f%% — Considera di ricaricare.".format(water))
                else -> {
                    NotificationHelper.cancelNotification(ctx, NotificationHelper.NOTIF_WATER_LOW)
                    NotificationHelper.cancelNotification(ctx, NotificationHelper.NOTIF_WATER_CRITICAL)
                }
            }
        }
    }
}

// ==================== THEME SYSTEM ====================

data class ThemeColors(
    val primary: Color, val secondary: Color, val background: Color,
    val surface: Color, val onPrimary: Color, val onBackground: Color,
    val cardBackground: Color, val accent: Color
)

@Composable
fun getThemeColors(theme: AppTheme): ThemeColors = when (theme) {
    AppTheme.LIGHT -> ThemeColors(Color(0xFF4CAF50), Color(0xFF81C784), Color(0xFFFAFAFA), Color.White,
        Color.White, Color(0xFF212121), Color(0xFFF5F5F5), Color(0xFF66BB6A))
    AppTheme.DARK -> ThemeColors(Color(0xFF4CAF50), Color(0xFF66BB6A), Color(0xFF121212), Color(0xFF1E1E1E),
        Color.White, Color(0xFFE0E0E0), Color(0xFF2C2C2C), Color(0xFF81C784))
    AppTheme.GREEN_NATURE -> ThemeColors(Color(0xFF2E7D32), Color(0xFF66BB6A), Color(0xFFE8F5E9), Color(0xFFF1F8E9),
        Color.White, Color(0xFF1B5E20), Color(0xFFC8E6C9), Color(0xFF4CAF50))
    AppTheme.AURORA -> ThemeColors(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFFFCE4EC), Color(0xFFF3E5F5),
        Color.White, Color(0xFF880E4F), Color(0xFFF8BBD0), Color(0xFFBA68C8))
    AppTheme.OCEAN -> ThemeColors(Color(0xFF0277BD), Color(0xFF0288D1), Color(0xFFE1F5FE), Color(0xFFB3E5FC),
        Color.White, Color(0xFF01579B), Color(0xFF81D4FA), Color(0xFF29B6F6))
}

// ==================== CHARTS SCREEN ====================

private enum class ChartRange(val label: String, val minutes: Int) {
    H1("1h", 60), H6("6h", 360), H24("24h", 1440)
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

data class ThresholdLine(val value: Double, val color: Color, val label: String)

@Composable
fun ChartsScreen(themeColors: ThemeColors) {
    val context = LocalContext.current
    var history by remember { mutableStateOf<List<SensorRecord>>(emptyList()) }
    var selectedRange by remember { mutableStateOf(ChartRange.H6) }

    LaunchedEffect(Unit) {
        while (true) {
            history = SensorHistoryManager.loadHistory(context)
            delay(15_000)
        }
    }

    val cutoff = System.currentTimeMillis() - selectedRange.minutes * 60_000L
    val filtered = remember(history, selectedRange) { history.filter { it.timestamp >= cutoff } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("📈", fontSize = 28.sp)
            Spacer(Modifier.width(8.dp))
            Text("Grafici Serra", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(themeColors.cardBackground),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ChartRange.entries.forEach { range ->
                val isSelected = selectedRange == range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) themeColors.primary else Color.Transparent)
                        .clickable { selectedRange = range }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        range.label, fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) themeColors.onPrimary else themeColors.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(48.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📊", fontSize = 56.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Nessun dato disponibile", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "I grafici appariranno appena l'app riceve dati dalla serra.",
                        fontSize = 13.sp,
                        color = themeColors.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Column
        }

        ChartCard("🌡️ Temperatura", "Andamento in °C", themeColors, Color(0xFFFF6B6B)) {
            val points = filtered.mapNotNull { r -> r.temperature?.let { r.timestamp to it } }
            SeriesLineChart(
                points, Color(0xFFFF6B6B), Color(0xFFFF6B6B).copy(alpha = 0.15f),
                listOf(
                    ThresholdLine(AlertThresholds.TEMP_MAX, Color(0xFFFFB300), "Max"),
                    ThresholdLine(AlertThresholds.TEMP_MIN, Color(0xFF42A5F5), "Min")
                ),
                themeColors
            )
        }
        Spacer(Modifier.height(12.dp))

        ChartCard("💧 Umidità", "Aria (blu) e Terreno (verde) in %", themeColors, Color(0xFF4ECDC4)) {
            val airPts = filtered.mapNotNull { r -> r.humidity?.let { r.timestamp to it } }
            val soilPts = filtered.mapNotNull { r -> r.soilHumidity?.let { r.timestamp to it } }
            DualLineChart(
                airPts, soilPts,
                Color(0xFF29B6F6), Color(0xFF66BB6A),
                listOf(
                    ThresholdLine(AlertThresholds.HUMIDITY_MAX, Color(0xFFE53935), "Aria max"),
                    ThresholdLine(AlertThresholds.SOIL_MIN, Color(0xFFFF8F00), "Suolo min")
                ),
                themeColors = themeColors,
                showLegend = true,
                legendLabels = listOf("Aria %" to Color(0xFF29B6F6), "Suolo %" to Color(0xFF66BB6A))
            )
        }
        Spacer(Modifier.height(12.dp))

        ChartCard("☀️ Luminosità", "Ciclo giorno/notte", themeColors, Color(0xFFFFE66D), chartHeight = 200.dp) {
            val lightPts = filtered.mapNotNull { r ->
                SensorHistoryManager.parseLightValue(r.lightRaw)?.let { r.timestamp to it }
            }
            DayNightChart(lightPts, themeColors)
        }
        Spacer(Modifier.height(12.dp))

        ChartCard("🚰 Livello Acqua", "Serbatoio in %", themeColors, Color(0xFF38B6FF)) {
            val waterPts = filtered.mapNotNull { r -> r.waterLevel?.let { r.timestamp to it } }
            WaterAreaChart(waterPts, themeColors)
        }
        Spacer(Modifier.height(12.dp))

        ChartCard("🌡️💧 Correlazione", "Temperatura (rosso) e Umidità aria (blu) — scala normalizzata", themeColors, Color(0xFFBA68C8)) {
            val tempPts = filtered.mapNotNull { r -> r.temperature?.let { r.timestamp to it } }
            val humPts = filtered.mapNotNull { r -> r.humidity?.let { r.timestamp to it } }
            DualLineChart(
                tempPts, humPts,
                Color(0xFFFF6B6B), Color(0xFF29B6F6),
                emptyList(),
                normalizeIndependently = true,
                themeColors = themeColors,
                showLegend = true,
                legendLabels = listOf("Temp °C" to Color(0xFFFF6B6B), "Umid. %" to Color(0xFF29B6F6))
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "📝 Dati degli ultimi ${selectedRange.label}. Aggiornamento ogni 15 sec.",
            fontSize = 11.sp,
            color = themeColors.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChartCard(
    title: String, subtitle: String, themeColors: ThemeColors, accentColor: Color,
    chartHeight: Dp = 180.dp, content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 20.dp).clip(RoundedCornerShape(2.dp)).background(accentColor))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
                    Text(subtitle, fontSize = 11.sp, color = themeColors.onBackground.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) { content() }
        }
    }
}

@Composable
private fun NoDataLabel(themeColors: ThemeColors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Nessun dato nel periodo selezionato", fontSize = 13.sp, color = themeColors.onBackground.copy(alpha = 0.4f))
    }
}

@Composable
fun SeriesLineChart(
    dataPoints: List<Pair<Long, Double>>,
    lineColor: Color, fillColor: Color,
    thresholds: List<ThresholdLine> = emptyList(),
    themeColors: ThemeColors
) {
    if (dataPoints.isEmpty()) { NoDataLabel(themeColors); return }
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(dataPoints) { animProgress.snapTo(0f); animProgress.animateTo(1f, tween(800, easing = EaseOutCubic)) }
    val gridColor = themeColors.onBackground.copy(alpha = 0.08f)
    val labelColor = themeColors.onBackground.copy(alpha = 0.45f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val padL = 44.dp.toPx(); val padR = 8.dp.toPx(); val padT = 8.dp.toPx(); val padB = 28.dp.toPx()
        val cW = size.width - padL - padR; val cH = size.height - padT - padB
        val minV = dataPoints.minOf { it.second }; val maxV = dataPoints.maxOf { it.second }
        val range = (maxV - minV).coerceAtLeast(0.1)
        fun xOf(i: Int) = padL + (i.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * cW
        fun yOf(v: Double) = padT + cH - ((v - minV) / range * cH).toFloat()
        repeat(5) { i ->
            val y = padT + cH * (i.toFloat() / 4)
            drawLine(gridColor, Offset(padL, y), Offset(padL + cW, y), strokeWidth = 1f)
            val lv = maxV - (range * i / 4)
            drawIntoCanvas { c: Canvas ->
                c.nativeCanvas.drawText("%.1f".format(lv), padL - 4f, y + 7f,
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb(); textSize = 22f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    })
            }
        }
        thresholds.forEach { t ->
            if (t.value in minV..maxV) {
                val ty = yOf(t.value)
                drawLine(t.color.copy(alpha = 0.7f), Offset(padL, ty), Offset(padL + cW, ty),
                    strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            }
        }
        val ac = (dataPoints.size * animProgress.value).toInt().coerceAtLeast(2)
        val fillPath = Path().apply {
            moveTo(xOf(0), yOf(dataPoints[0].second))
            for (i in 1 until ac) {
                val cpx = (xOf(i - 1) + xOf(i)) / 2
                cubicTo(cpx, yOf(dataPoints[i - 1].second), cpx, yOf(dataPoints[i].second), xOf(i), yOf(dataPoints[i].second))
            }
            lineTo(xOf(ac - 1), padT + cH); lineTo(xOf(0), padT + cH); close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(fillColor, fillColor.copy(alpha = 0f)), startY = padT, endY = padT + cH))
        val linePath = Path().apply {
            moveTo(xOf(0), yOf(dataPoints[0].second))
            for (i in 1 until ac) {
                val cpx = (xOf(i - 1) + xOf(i)) / 2
                cubicTo(cpx, yOf(dataPoints[i - 1].second), cpx, yOf(dataPoints[i].second), xOf(i), yOf(dataPoints[i].second))
            }
        }
        drawPath(linePath, lineColor, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        repeat(5) { i ->
            val idx = (i.toFloat() / 4 * (dataPoints.size - 1)).toInt().coerceIn(0, dataPoints.size - 1)
            drawIntoCanvas { c: Canvas ->
                c.nativeCanvas.drawText(SensorHistoryManager.formatTimestamp(dataPoints[idx].first),
                    xOf(idx), padT + cH + 20f,
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb(); textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER
                    })
            }
        }
    }
}

@Composable
fun DualLineChart(
    primaryPoints: List<Pair<Long, Double>>,
    secondaryPoints: List<Pair<Long, Double>>,
    primaryColor: Color, secondaryColor: Color,
    thresholds: List<ThresholdLine> = emptyList(),
    normalizeIndependently: Boolean = false,
    themeColors: ThemeColors,
    showLegend: Boolean = false,
    legendLabels: List<Pair<String, Color>> = emptyList()
) {
    if (primaryPoints.isEmpty() && secondaryPoints.isEmpty()) { NoDataLabel(themeColors); return }
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(primaryPoints, secondaryPoints) {
        animProgress.snapTo(0f); animProgress.animateTo(1f, tween(900, easing = EaseOutCubic))
    }
    val allTimestamps = (primaryPoints.map { it.first } + secondaryPoints.map { it.first }).sorted()
    val tsMin = allTimestamps.firstOrNull() ?: 0L
    val tsMax = allTimestamps.lastOrNull() ?: 1L
    val tsRange = (tsMax - tsMin).coerceAtLeast(1L)
    val minP = if (normalizeIndependently) primaryPoints.minOfOrNull { it.second } ?: 0.0 else 0.0
    val maxP = if (normalizeIndependently) primaryPoints.maxOfOrNull { it.second } ?: 100.0 else 100.0
    val minS = if (normalizeIndependently) secondaryPoints.minOfOrNull { it.second } ?: 0.0 else 0.0
    val maxS = if (normalizeIndependently) secondaryPoints.maxOfOrNull { it.second } ?: 100.0 else 100.0
    val gridColor = themeColors.onBackground.copy(alpha = 0.08f)
    val labelColor = themeColors.onBackground.copy(alpha = 0.45f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val padL = 44.dp.toPx(); val padR = 8.dp.toPx(); val padT = 8.dp.toPx(); val padB = 28.dp.toPx()
        val cW = size.width - padL - padR; val cH = size.height - padT - padB
        fun xOfTs(ts: Long) = padL + ((ts - tsMin).toFloat() / tsRange) * cW
        fun yOfV(v: Double, mn: Double, mx: Double) =
            padT + cH - ((v - mn) / (mx - mn).coerceAtLeast(0.1) * cH).toFloat()
        repeat(5) { i ->
            drawLine(gridColor, Offset(padL, padT + cH * i / 4f), Offset(padL + cW, padT + cH * i / 4f), strokeWidth = 1f)
        }
        thresholds.forEach { t ->
            val ty = yOfV(t.value, minP, maxP)
            drawLine(t.color.copy(alpha = 0.7f), Offset(padL, ty), Offset(padL + cW, ty),
                strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }
        fun drawSeries(pts: List<Pair<Long, Double>>, color: Color, mn: Double, mx: Double) {
            if (pts.size < 2) return
            val ac = (pts.size * animProgress.value).toInt().coerceAtLeast(2)
            val p = Path().apply {
                moveTo(xOfTs(pts[0].first), yOfV(pts[0].second, mn, mx))
                for (i in 1 until ac) {
                    val cpx = (xOfTs(pts[i - 1].first) + xOfTs(pts[i].first)) / 2
                    cubicTo(cpx, yOfV(pts[i - 1].second, mn, mx), cpx, yOfV(pts[i].second, mn, mx),
                        xOfTs(pts[i].first), yOfV(pts[i].second, mn, mx))
                }
            }
            drawPath(p, color, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawSeries(primaryPoints, primaryColor, minP, maxP)
        drawSeries(secondaryPoints, secondaryColor, minS, maxS)
        repeat(5) { i ->
            val ts = tsMin + (tsRange * i / 4)
            drawIntoCanvas { c: Canvas ->
                c.nativeCanvas.drawText(SensorHistoryManager.formatTimestamp(ts),
                    padL + cW * i / 4f, padT + cH + 20f,
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb(); textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER
                    })
            }
        }
    }
    if (showLegend && legendLabels.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            legendLabels.forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Box(modifier = Modifier.size(12.dp, 3.dp).background(color, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 11.sp, color = color)
                }
            }
        }
    }
}

@Composable
fun DayNightChart(dataPoints: List<Pair<Long, Double>>, themeColors: ThemeColors) {
    if (dataPoints.isEmpty()) { NoDataLabel(themeColors); return }
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(dataPoints) { animProgress.snapTo(0f); animProgress.animateTo(1f, tween(900, easing = EaseOutCubic)) }
    val gridColor = themeColors.onBackground.copy(alpha = 0.07f)
    val labelColor = themeColors.onBackground.copy(alpha = 0.45f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val padL = 44.dp.toPx(); val padR = 8.dp.toPx(); val padT = 8.dp.toPx(); val padB = 28.dp.toPx()
        val cW = size.width - padL - padR; val cH = size.height - padT - padB
        fun xOf(i: Int) = padL + (i.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * cW
        fun yOf(v: Double) = padT + cH - (v.coerceIn(0.0, 100.0) / 100.0 * cH).toFloat()
        dataPoints.forEachIndexed { i, (ts, _) ->
            if (i < dataPoints.size - 1) {
                val hour = Calendar.getInstance().also { it.timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
                val bgColor = if (hour < 6 || hour >= 20)
                    Color(0xFF1A237E).copy(alpha = 0.12f)
                else
                    Color(0xFFFFF9C4).copy(alpha = 0.35f)
                drawRect(bgColor, topLeft = Offset(xOf(i), padT), size = Size(xOf(i + 1) - xOf(i), cH))
            }
        }
        listOf(0, 25, 50, 75, 100).forEach { pct ->
            val y = yOf(pct.toDouble())
            drawLine(gridColor, Offset(padL, y), Offset(padL + cW, y), strokeWidth = 1f)
            drawIntoCanvas { c: Canvas ->
                c.nativeCanvas.drawText("$pct%", padL - 4f, y + 7f,
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb(); textSize = 22f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    })
            }
        }
        val ac = (dataPoints.size * animProgress.value).toInt().coerceAtLeast(2)
        val fillPath = Path().apply {
            moveTo(xOf(0), yOf(dataPoints[0].second))
            for (i in 1 until ac) {
                val cpx = (xOf(i - 1) + xOf(i)) / 2
                cubicTo(cpx, yOf(dataPoints[i - 1].second), cpx, yOf(dataPoints[i].second), xOf(i), yOf(dataPoints[i].second))
            }
            lineTo(xOf(ac - 1), padT + cH); lineTo(xOf(0), padT + cH); close()
        }
        drawPath(fillPath, Brush.verticalGradient(
            listOf(Color(0xFFFFD600).copy(alpha = 0.5f), Color(0xFFFFD600).copy(alpha = 0.02f)), padT, padT + cH))
        val linePath = Path().apply {
            moveTo(xOf(0), yOf(dataPoints[0].second))
            for (i in 1 until ac) {
                val cpx = (xOf(i - 1) + xOf(i)) / 2
                cubicTo(cpx, yOf(dataPoints[i - 1].second), cpx, yOf(dataPoints[i].second), xOf(i), yOf(dataPoints[i].second))
            }
        }
        drawPath(linePath, Color(0xFFFFD600), style = Stroke(2.5f, cap = StrokeCap.Round))
        repeat(5) { i ->
            val idx = (i.toFloat() / 4 * (dataPoints.size - 1)).toInt().coerceIn(0, dataPoints.size - 1)
            drawIntoCanvas { c: Canvas ->
                c.nativeCanvas.drawText(SensorHistoryManager.formatTimestamp(dataPoints[idx].first),
                    xOf(idx), padT + cH + 20f,
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb(); textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER
                    })
            }
        }
    }
}

@Composable
fun WaterAreaChart(dataPoints: List<Pair<Long, Double>>, themeColors: ThemeColors) {
    if (dataPoints.isEmpty()) { NoDataLabel(themeColors); return }
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(dataPoints) { animProgress.snapTo(0f); animProgress.animateTo(1f, tween(800, easing = EaseOutCubic)) }
    val gridColor = themeColors.onBackground.copy(alpha = 0.08f)
    val labelColor = themeColors.onBackground.copy(alpha = 0.45f)
    val thresholds = listOf(
        ThresholdLine(AlertThresholds.WATER_LOW, Color(0xFFFFB300), "Basso"),
        ThresholdLine(AlertThresholds.WATER_CRITICAL, Color(0xFFE53935), "Critico")
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val padL = 44.dp.toPx(); val padR = 8.dp.toPx(); val padT = 8.dp.toPx(); val padB = 28.dp.toPx()
        val cW = size.width - padL - padR; val cH = size.height - padT - padB
        fun xOf(i: Int) = padL + (i.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * cW
        fun yOf(v: Double) = padT + cH - (v.coerceIn(0.0, 100.0) / 100.0 * cH).toFloat()
        listOf(0, 25, 50, 75, 100).forEach { pct ->
            val y = yOf(pct.toDouble())
            drawLine(gridColor, Offset(padL, y), Offset(padL + cW, y), strokeWidth = 1f)
            drawIntoCanvas { c: Canvas ->
                c.nativeCanvas.drawText("$pct%", padL - 4f, y + 7f,
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb(); textSize = 22f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    })
            }
        }
        thresholds.forEach { t ->
            val ty = yOf(t.value)
            drawLine(t.color.copy(alpha = 0.7f), Offset(padL, ty), Offset(padL + cW, ty),
                strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            drawIntoCanvas { c: Canvas ->
                c.nativeCanvas.drawText(t.label, padL + 4f, ty - 4f,
                    android.graphics.Paint().apply { color = t.color.copy(alpha = 0.9f).toArgb(); textSize = 20f })
            }
        }
        val ac = (dataPoints.size * animProgress.value).toInt().coerceAtLeast(2)
        val fillPath = Path().apply {
            moveTo(xOf(0), yOf(dataPoints[0].second))
            for (i in 1 until ac) {
                val cpx = (xOf(i - 1) + xOf(i)) / 2
                cubicTo(cpx, yOf(dataPoints[i - 1].second), cpx, yOf(dataPoints[i].second), xOf(i), yOf(dataPoints[i].second))
            }
            lineTo(xOf(ac - 1), padT + cH); lineTo(xOf(0), padT + cH); close()
        }
        drawPath(fillPath, Brush.verticalGradient(
            listOf(Color(0xFF38B6FF).copy(alpha = 0.6f), Color(0xFF38B6FF).copy(alpha = 0.05f)), padT, padT + cH))
        val linePath = Path().apply {
            moveTo(xOf(0), yOf(dataPoints[0].second))
            for (i in 1 until ac) {
                val cpx = (xOf(i - 1) + xOf(i)) / 2
                cubicTo(cpx, yOf(dataPoints[i - 1].second), cpx, yOf(dataPoints[i].second), xOf(i), yOf(dataPoints[i].second))
            }
        }
        drawPath(linePath, Color(0xFF38B6FF), style = Stroke(2.5f, cap = StrokeCap.Round))
        repeat(5) { i ->
            val idx = (i.toFloat() / 4 * (dataPoints.size - 1)).toInt().coerceIn(0, dataPoints.size - 1)
            drawIntoCanvas { c: Canvas ->
                c.nativeCanvas.drawText(SensorHistoryManager.formatTimestamp(dataPoints[idx].first),
                    xOf(idx), padT + cH + 20f,
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb(); textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER
                    })
            }
        }
    }
}

// ==================== DRAWER & STATUS BAR ====================

@Composable
fun DrawerMenuItem(
    icon: ImageVector, label: String, selected: Boolean, themeColors: ThemeColors,
    isDestructive: Boolean = false, onClick: () -> Unit
) {
    val bgColor = if (selected) themeColors.primary.copy(alpha = 0.1f) else Color.Transparent
    val contentColor = if (isDestructive) Color.Red else if (selected) themeColors.primary else themeColors.onBackground
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(bgColor).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = contentColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun AnimatedBattlePassMenuItem(onClick: () -> Unit) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) { while (true) { scale.animateTo(1.05f, tween(1000)); scale.animateTo(1f, tween(1000)) } }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale.value)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFFD700)),
                        startX = shimmerOffset - 500f, endX = shimmerOffset
                    )
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("⭐", fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                Column {
                    Text("GreenPass", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Sblocca ricompense!", fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.weight(1f)); Text("✨", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun StatusBar(
    isInternetConnected: Boolean, isArduinoAvailable: Boolean,
    currentDate: String, currentTime: String, weatherInfo: String, themeColors: ThemeColors
) {
    Column(modifier = Modifier.fillMaxWidth().background(themeColors.surface).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatusChip("🌐", if (isInternetConnected) "Online" else "Offline", isInternetConnected, themeColors)
            StatusChip("🔌", if (isArduinoAvailable) "Arduino OK" else "Arduino Off", isArduinoAvailable, themeColors)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("$currentDate  •  $currentTime", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = themeColors.onBackground)
        }
        Text("🌦 $weatherInfo", fontSize = 13.sp, color = themeColors.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun StatusChip(icon: String, label: String, isActive: Boolean, themeColors: ThemeColors) {
    val bgColor = if (isActive) themeColors.primary.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)
    val textColor = if (isActive) themeColors.primary else Color.Gray
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp); Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
    }
}

// ==================== DATA SCREEN ====================

@Composable
fun DataScreen(
    temperature: String, humidity: String, lightLevel: String,
    soilHumidity: String, remWater: String, themeColors: ThemeColors
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("📊 Dati Ambientali", fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = themeColors.onBackground, modifier = Modifier.padding(bottom = 16.dp))
        AnimatedInfoCard("🌡️", "Temperatura", "$temperature°C", themeColors, Color(0xFFFF6B6B))
        AnimatedInfoCard("💧", "Umidità Aria", "$humidity%", themeColors, Color(0xFF4ECDC4))
        AnimatedInfoCard("☀️", "Luminosità", lightLevel, themeColors, Color(0xFFFFE66D))
        AnimatedInfoCard("🌱", "Umidità Terreno", "$soilHumidity%", themeColors, Color(0xFF95E1D3))
        AnimatedInfoCard("🚰", "Acqua Rimanente", "$remWater%", themeColors, Color(0xFF38B6FF))
    }
}

@Composable
fun AnimatedInfoCard(icon: String, title: String, value: String, themeColors: ThemeColors, accentColor: Color) {
    val scale = remember { Animatable(0.95f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(300, easing = EaseOutBack)) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).scale(scale.value),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 28.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, color = themeColors.onBackground.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
            }
        }
    }
}

// ==================== CONTROLS SCREEN ====================

@Composable
fun ControlsScreen(
    isControllingByMe: Boolean, activeControllerEmail: String?, plantRules: List<PlantRule>,
    lightOn: Boolean, fanOn: Boolean, roofOpen: Boolean, irrigationOn: Boolean, humidifierOn: Boolean,
    onAcquireControl: () -> Unit, onToggleLight: () -> Unit, onToggleFan: () -> Unit,
    onToggleRoof: () -> Unit, onToggleIrrigation: () -> Unit, onToggleHumidifier: () -> Unit,
    themeColors: ThemeColors
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isControllingByMe) {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔒", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                    Text("Serra Occupata", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    Spacer(Modifier.height(8.dp))
                    Text("In controllo: $activeControllerEmail", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAcquireControl, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                        Text("Forza Controllo")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (plantRules.any { it.isActive }) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                    Text("Comandi manuali disabilitati: una card è attiva", fontSize = 14.sp,
                        color = Color(0xFFE65100), fontWeight = FontWeight.Medium)
                }
            }
        }
        Text("🎮 Controlli Manuali", fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = themeColors.onBackground, modifier = Modifier.padding(bottom = 24.dp))
        val isEnabled = !plantRules.any { it.isActive } && isControllingByMe
        AnimatedControlButton("💧", if (irrigationOn) "Irrigazione Attiva" else "Irrigazione Disattivata",
            irrigationOn, isEnabled, onToggleIrrigation, themeColors)
        AnimatedControlButton("💨", if (humidifierOn) "Umidificatore Acceso" else "Umidificatore Spento",
            humidifierOn, isEnabled, onToggleHumidifier, themeColors)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedControlButton("💡", if (lightOn) "Luci Accese" else "Luci Spente",
                lightOn, isEnabled, onToggleLight, themeColors, Modifier.weight(1f))
            AnimatedControlButton("🌀", if (fanOn) "Ventole Accese" else "Ventole Spente",
                fanOn, isEnabled, onToggleFan, themeColors, Modifier.weight(1f))
        }
        AnimatedControlButton(if (roofOpen) "🛖" else "☂️", if (roofOpen) "Chiudi Tetto" else "Apri Tetto",
            roofOpen, isEnabled, onToggleRoof, themeColors)
    }
}

@Composable
fun AnimatedControlButton(
    icon: String, label: String, isActive: Boolean, enabled: Boolean, onClick: () -> Unit,
    themeColors: ThemeColors, modifier: Modifier = Modifier.fillMaxWidth()
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            onClick()
            scope.launch { scale.animateTo(0.95f, tween(100)); scale.animateTo(1f, tween(100)) }
        },
        enabled = enabled,
        modifier = modifier.padding(vertical = 8.dp).scale(scale.value),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) themeColors.primary else themeColors.cardBackground,
            contentColor = if (isActive) themeColors.onPrimary else themeColors.onBackground,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
        ),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(icon, fontSize = 24.sp); Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==================== CARDS SCREEN ====================

@Composable
fun CardsScreen(
    plantRules: List<PlantRule>, isControllingByMe: Boolean, activeControllerEmail: String?,
    temperature: String, humidity: String, soilHumidity: String,
    onAcquireControl: () -> Unit, onRulesChange: (List<PlantRule>) -> Unit, themeColors: ThemeColors
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedRule by remember { mutableStateOf<PlantRule?>(null) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (!isControllingByMe) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔒", fontSize = 36.sp); Spacer(Modifier.height(8.dp))
                    Text("Serra Occupata", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    Spacer(Modifier.height(4.dp))
                    Text("In controllo: $activeControllerEmail", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAcquireControl,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(12.dp)) { Text("Forza Controllo") }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🌱 Automazioni Piante", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
            Button(onClick = { showAddDialog = true }, enabled = isControllingByMe,
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary, disabledContainerColor = Color.Gray.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, contentDescription = null) }
        }
        Spacer(Modifier.height(16.dp))
        if (plantRules.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground),
                shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌿", fontSize = 64.sp); Spacer(Modifier.height(16.dp))
                    Text("Nessuna Automazione", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    Text("Crea la tua prima automazione per gestire le piante", fontSize = 14.sp,
                        color = themeColors.onBackground.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                }
            }
        } else {
            plantRules.forEach { rule ->
                PlantRuleCard(rule, isControllingByMe, temperature, humidity, soilHumidity,
                    onToggleActive = {
                        if (isControllingByMe) onRulesChange(plantRules.map {
                            if (it.name == rule.name) it.copy(isActive = !it.isActive) else it.copy(isActive = false)
                        })
                    },
                    onDelete = { if (isControllingByMe) onRulesChange(plantRules.filter { it.name != rule.name }) },
                    onShowDetails = { selectedRule = rule; showDetailsDialog = true },
                    themeColors = themeColors
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (showAddDialog && isControllingByMe)
        AddPlantDialog(onDismiss = { showAddDialog = false },
            onAdd = { onRulesChange(plantRules + it); showAddDialog = false }, themeColors = themeColors)
    if (showDetailsDialog && selectedRule != null)
        PlantDetailsDialog(selectedRule!!, onDismiss = { showDetailsDialog = false }, themeColors = themeColors)
}

@Composable
fun PlantRuleCard(
    rule: PlantRule, isEnabled: Boolean, temperature: String, humidity: String, soilHumidity: String,
    onToggleActive: () -> Unit, onDelete: () -> Unit, onShowDetails: () -> Unit, themeColors: ThemeColors
) {
    val temp = temperature.toDoubleOrNull(); val airHum = humidity.toDoubleOrNull(); val soilHum = soilHumidity.toDoubleOrNull()
    val tempStatus = temp?.let { when { it < rule.minTemperature -> "❄️ Freddo"; it > rule.maxTemperature -> "🔥 Caldo"; else -> "✅ OK" } } ?: "..."
    val airStatus = airHum?.let { when { it < rule.minAirHumidity -> "🏜️ Secca"; it > rule.maxAirHumidity -> "💧 Umida"; else -> "✅ OK" } } ?: "..."
    val soilStatus = soilHum?.let { when { it < rule.minSoilHumidity -> "🌵 Secco"; it > rule.maxSoilHumidity -> "💦 Bagnato"; else -> "✅ OK" } } ?: "..."
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (isEnabled) 1f else 0.5f),
        colors = CardDefaults.cardColors(containerColor = if (rule.isActive) themeColors.primary.copy(alpha = 0.15f) else themeColors.cardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(if (rule.isActive) 8.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (rule.isActive) "🟢" else "⚪", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
                    Text(rule.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
                }
                Row {
                    IconButton(onClick = onShowDetails, enabled = isEnabled) { Icon(Icons.Default.Info, "Dettagli", tint = themeColors.primary) }
                    IconButton(onClick = onDelete, enabled = isEnabled && !rule.isActive) {
                        Icon(Icons.Default.Delete, "Elimina", tint = if (rule.isActive) Color.Gray else Color(0xFFE53935))
                    }
                }
            }
            if (rule.isActive) {
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = themeColors.surface),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        StatusRow("🌡️ Temperatura", tempStatus, themeColors)
                        StatusRow("💧 Umidità Aria", airStatus, themeColors)
                        StatusRow("🌱 Umidità Terreno", soilStatus, themeColors)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onToggleActive, enabled = isEnabled, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (rule.isActive) Color(0xFFFF6B6B) else themeColors.primary,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)) {
                Icon(if (rule.isActive) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (rule.isActive) "Disattiva" else "Attiva", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StatusRow(label: String, status: String, themeColors: ThemeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
        Text(status, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = themeColors.onBackground)
    }
}

// ==================== DIALOGS ====================

@Composable
fun PlantDetailsDialog(rule: PlantRule, onDismiss: () -> Unit, themeColors: ThemeColors) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dettagli - ${rule.name}", fontWeight = FontWeight.Bold, color = themeColors.onBackground) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailSection("🌱 Umidità Terreno", "${rule.minSoilHumidity}% - ${rule.maxSoilHumidity}%", themeColors)
                DetailSection("💧 Umidità Aria", "${rule.minAirHumidity}% - ${rule.maxAirHumidity}%", themeColors)
                DetailSection("🌡️ Temperatura", "${rule.minTemperature}°C - ${rule.maxTemperature}°C", themeColors)
                DetailSection("☀️ Luminosità", "${rule.minLightLevel}% - ${rule.maxLightLevel}%", themeColors)
                Spacer(Modifier.height(16.dp))
                Text("⚙️ Azioni Automatiche:", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = themeColors.onBackground, modifier = Modifier.padding(bottom = 8.dp))
                ActionDetail("Terreno secco", rule.actionSoilDry, themeColors)
                ActionDetail("Terreno umido", rule.actionSoilWet, themeColors)
                ActionDetail("Aria secca", rule.actionAirLow, themeColors)
                ActionDetail("Aria umida", rule.actionAirHigh, themeColors)
                ActionDetail("Temp. bassa", rule.actionTempLow, themeColors)
                ActionDetail("Temp. alta", rule.actionTempHigh, themeColors)
                ActionDetail("Luce bassa", rule.actionLightLow, themeColors)
                ActionDetail("Luce alta", rule.actionLightHigh, themeColors)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary)) { Text("Chiudi") }
        },
        containerColor = themeColors.surface
    )
}

@Composable
fun DetailSection(label: String, value: String, themeColors: ThemeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
    }
}

@Composable
fun ActionDetail(condition: String, action: Action, themeColors: ThemeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("• ", fontSize = 14.sp, color = themeColors.primary)
        Text(condition, fontSize = 13.sp, color = themeColors.onBackground.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
        Text(action.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = themeColors.primary)
    }
}

@Composable
fun AddPlantDialog(onDismiss: () -> Unit, onAdd: (PlantRule) -> Unit, themeColors: ThemeColors) {
    var name by remember { mutableStateOf("") }
    var minSoilHumidity by remember { mutableStateOf("30") }; var maxSoilHumidity by remember { mutableStateOf("70") }
    var minAirHumidity by remember { mutableStateOf("40") }; var maxAirHumidity by remember { mutableStateOf("80") }
    var minTemperature by remember { mutableStateOf("15") }; var maxTemperature by remember { mutableStateOf("30") }
    var minLightLevel by remember { mutableStateOf("20") }; var maxLightLevel by remember { mutableStateOf("80") }
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
        title = { Text("🌱 Crea Nuova Automazione", fontWeight = FontWeight.Bold, color = themeColors.onBackground) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome Pianta") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColors.primary, focusedLabelColor = themeColors.primary))
                SectionHeader("🌱 Umidità Terreno", themeColors)
                RangeRow(minSoilHumidity, maxSoilHumidity, { minSoilHumidity = it }, { maxSoilHumidity = it })
                ActionSelector("Se troppo secco", actionSoilDry, actionOptions, themeColors) { actionSoilDry = it }
                ActionSelector("Se troppo umido", actionSoilWet, actionOptions, themeColors) { actionSoilWet = it }
                Spacer(Modifier.height(16.dp))
                SectionHeader("💧 Umidità Aria", themeColors)
                RangeRow(minAirHumidity, maxAirHumidity, { minAirHumidity = it }, { maxAirHumidity = it })
                ActionSelector("Se troppo secca", actionAirLow, actionOptions, themeColors) { actionAirLow = it }
                ActionSelector("Se troppo umida", actionAirHigh, actionOptions, themeColors) { actionAirHigh = it }
                Spacer(Modifier.height(16.dp))
                SectionHeader("🌡️ Temperatura", themeColors)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = minTemperature,
                        onValueChange = { minTemperature = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                        label = { Text("Min °C") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = maxTemperature,
                        onValueChange = { maxTemperature = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                        label = { Text("Max °C") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                }
                Spacer(Modifier.height(8.dp))
                ActionSelector("Se troppo freddo", actionTempLow, actionOptions, themeColors) { actionTempLow = it }
                ActionSelector("Se troppo caldo", actionTempHigh, actionOptions, themeColors) { actionTempHigh = it }
                Spacer(Modifier.height(16.dp))
                SectionHeader("☀️ Luminosità", themeColors)
                RangeRow(minLightLevel, maxLightLevel, { minLightLevel = it }, { maxLightLevel = it })
                ActionSelector("Se poca luce", actionLightLow, actionOptions, themeColors) { actionLightLow = it }
                ActionSelector("Se troppa luce", actionLightHigh, actionOptions, themeColors) { actionLightHigh = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onAdd(PlantRule(name,
                    minSoilHumidity.toDoubleOrNull() ?: 30.0, maxSoilHumidity.toDoubleOrNull() ?: 70.0,
                    minAirHumidity.toDoubleOrNull() ?: 40.0, maxAirHumidity.toDoubleOrNull() ?: 80.0,
                    minTemperature.toDoubleOrNull() ?: 15.0, maxTemperature.toDoubleOrNull() ?: 30.0,
                    minLightLevel.toDoubleOrNull() ?: 20.0, maxLightLevel.toDoubleOrNull() ?: 80.0,
                    actionSoilDry, actionSoilWet, actionAirLow, actionAirHigh,
                    actionTempLow, actionTempHigh, actionLightLow, actionLightHigh))
            }, colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary), enabled = name.isNotBlank()) {
                Text("Crea", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = themeColors.onBackground.copy(alpha = 0.6f)) } },
        containerColor = themeColors.surface
    )
}

@Composable
private fun SectionHeader(text: String, themeColors: ThemeColors) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = themeColors.onBackground,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun RangeRow(minVal: String, maxVal: String, onMinChange: (String) -> Unit, onMaxChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = minVal, onValueChange = { onMinChange(it.filter { c -> c.isDigit() || c == '.' }) },
            label = { Text("Min %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
        OutlinedTextField(value = maxVal, onValueChange = { onMaxChange(it.filter { c -> c.isDigit() || c == '.' }) },
            label = { Text("Max %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
    }
}

@Composable
fun ActionSelector(
    label: String, currentAction: Action, options: List<Action>,
    themeColors: ThemeColors, onSelect: (Action) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 12.sp, color = themeColors.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = themeColors.onBackground)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(currentAction.displayName, fontSize = 14.sp)
                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(20.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
                options.forEach { action ->
                    DropdownMenuItem(text = { Text(action.displayName) }, onClick = { onSelect(action); expanded = false })
                }
            }
        }
    }
}

// ==================== SNAKE GAME ====================

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SnakeGameScreen(themeColors: ThemeColors, userStats: UserStats, onStatsUpdate: (UserStats) -> Unit) {
    var snakeSegments by remember { mutableStateOf(listOf(Pair(10, 10))) }
    var food by remember { mutableStateOf(Pair(5, 5)) }
    var direction by remember { mutableStateOf(Pair(0, -1)) }
    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    val gridSize = 20
    LaunchedEffect(gameOver, isPaused) {
        if (!gameOver && !isPaused) {
            while (true) {
                delay(150)
                val newHead = Pair(
                    (snakeSegments.first().first + direction.first + gridSize) % gridSize,
                    (snakeSegments.first().second + direction.second + gridSize) % gridSize
                )
                if (snakeSegments.contains(newHead)) {
                    gameOver = true
                    onStatsUpdate(userStats.copy(snakeGamesPlayed = userStats.snakeGamesPlayed + 1))
                    break
                }
                val newSegments = mutableListOf(newHead).also { it.addAll(snakeSegments) }
                if (newHead == food) {
                    food = Pair((0 until gridSize).random(), (0 until gridSize).random())
                    score++
                } else newSegments.removeAt(newSegments.size - 1)
                snakeSegments = newSegments
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🐍 Segma Snake", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = themeColors.primary, modifier = Modifier.padding(16.dp))
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground),
                shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Punteggio", fontSize = 14.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
                        Text("$score", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = themeColors.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Lunghezza", fontSize = 14.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
                        Text("${snakeSegments.size}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = themeColors.accent)
                    }
                }
            }
            if (gameOver) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💀", fontSize = 48.sp); Spacer(Modifier.height(8.dp))
                        Text("Game Over!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        Spacer(Modifier.height(8.dp)); Text("Punteggio finale: $score", fontSize = 18.sp, color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { snakeSegments = listOf(Pair(10, 10)); food = Pair(5, 5); direction = Pair(0, -1); score = 0; gameOver = false; isPaused = false },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp))
                            Text("Rigioca", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier.aspectRatio(1f).padding(16.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.radialGradient(listOf(themeColors.surface, themeColors.cardBackground)))
                        .border(2.dp, themeColors.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    val tileSize = maxWidth / gridSize
                    Box(modifier = Modifier.offset(x = tileSize * food.first, y = tileSize * food.second), contentAlignment = Alignment.Center) {
                        Text("🎯", fontSize = (tileSize.value * 0.8).sp)
                    }
                    snakeSegments.forEachIndexed { i, s ->
                        Box(modifier = Modifier.offset(x = tileSize * s.first, y = tileSize * s.second).size(tileSize)
                            .clip(if (i == 0) CircleShape else RoundedCornerShape(30))
                            .background(if (i == 0) themeColors.primary else themeColors.accent.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center) {
                            if (i == 0) Text("👀", fontSize = (tileSize.value * 0.3).sp)
                        }
                    }
                    if (isPaused) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⏸", fontSize = 64.sp, color = Color.White)
                                Text("In Pausa", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { isPaused = !isPaused },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isPaused) themeColors.primary else themeColors.accent),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                        Spacer(Modifier.width(4.dp)); Text(if (isPaused) "Riprendi" else "Pausa")
                    }
                    Button(
                        onClick = { snakeSegments = listOf(Pair(10, 10)); food = Pair(5, 5); direction = Pair(0, -1); score = 0; gameOver = false; isPaused = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Restart") }
                }
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Controlli", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = themeColors.onBackground, modifier = Modifier.padding(bottom = 12.dp))
                        IconButton(onClick = { if (direction.second == 0) direction = Pair(0, -1) },
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(themeColors.primary.copy(alpha = 0.2f))) {
                            Icon(Icons.Default.KeyboardArrowUp, "Su", tint = themeColors.primary, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (direction.first == 0) direction = Pair(-1, 0) },
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(themeColors.primary.copy(alpha = 0.2f))) {
                                // Fix: uso AutoMirrored per le frecce direzionali
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Sinistra", tint = themeColors.primary, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(themeColors.cardBackground), contentAlignment = Alignment.Center) {
                                Text("🎮", fontSize = 32.sp)
                            }
                            Spacer(Modifier.width(16.dp))
                            IconButton(onClick = { if (direction.first == 0) direction = Pair(1, 0) },
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(themeColors.primary.copy(alpha = 0.2f))) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Destra", tint = themeColors.primary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        IconButton(onClick = { if (direction.second == 0) direction = Pair(0, 1) },
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(themeColors.primary.copy(alpha = 0.2f))) {
                            Icon(Icons.Default.KeyboardArrowDown, "Giù", tint = themeColors.primary, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==================== BATTLE PASS SCREEN ====================

@Composable
fun BattlePassScreen(
    themeColors: ThemeColors,
    sharedPreferences: android.content.SharedPreferences,
    userStats: UserStats,
    appSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onUserStatsChange: (UserStats) -> Unit
) {
    val missions = remember {
        listOf(
            BattlePassMission("irrigate_5", "Innaffiatore Principiante", "Irriga le piante 5 volte", "💧", 5, 0, 100),
            BattlePassMission("irrigate_20", "Esperto di Irrigazione", "Irriga le piante 20 volte", "🌊", 20, 0, 300),
            BattlePassMission("plants_3", "Giardiniere", "Crea 3 automazioni piante", "🌱", 3, 0, 200),
            BattlePassMission("plants_5", "Maestro Botanico", "Crea 5 automazioni piante", "🌿", 5, 0, 400),
            BattlePassMission("controls_10", "Controller Attivo", "Usa i controlli manuali 10 volte", "🎮", 10, 0, 150),
            BattlePassMission("controls_30", "Maestro dei Controlli", "Usa i controlli manuali 30 volte", "🕹️", 30, 0, 350),
            BattlePassMission("snake_3", "Giocatore Casuale", "Gioca 3 partite a Snake", "🐍", 3, 0, 100),
            BattlePassMission("snake_10", "Amante di Snake", "Gioca 10 partite a Snake", "🎯", 10, 0, 250)
        )
    }
    val rewards = remember {
        listOf(
            BattlePassReward(1, "Benvenuto", "Inizia la tua avventura", "🌱", "badge"),
            BattlePassReward(2, "Tema Aurora", "Sblocca il tema Aurora", "🌸", "theme_AURORA"),
            BattlePassReward(3, "Giardiniere", "Primo traguardo", "🌿", "badge"),
            BattlePassReward(4, "Tema Ocean", "Sblocca il tema Ocean", "🌊", "theme_OCEAN"),
            BattlePassReward(5, "Esperto", "Padroneggi le basi", "⭐", "badge"),
            BattlePassReward(6, "Tema Dark", "Sblocca il tema Dark", "🌙", "theme_DARK"),
            BattlePassReward(7, "Maestro", "Sei un professionista", "👑", "badge"),
            BattlePassReward(8, "Tema Light", "Sblocca il tema Light", "☀️", "theme_LIGHT"),
            BattlePassReward(9, "Leggenda", "Livello massimo!", "🏆", "badge"),
            BattlePassReward(10, "Tutti i Temi", "Hai sbloccato tutto!", "🎨", "all_themes")
        )
    }
    var activeMissions by remember {
        mutableStateOf(try {
            val j = sharedPreferences.getString("active_missions", null)
            if (j != null) Json.decodeFromString<List<BattlePassMission>>(j) else missions
        } catch (e: Exception) { missions })
    }
    var activeRewards by remember {
        mutableStateOf(try {
            val j = sharedPreferences.getString("active_rewards", null)
            if (j != null) Json.decodeFromString<List<BattlePassReward>>(j) else rewards
        } catch (e: Exception) { rewards })
    }
    fun saveMissions() { sharedPreferences.edit { putString("active_missions", Json.encodeToString(activeMissions)) } }
    fun saveRewards() { sharedPreferences.edit { putString("active_rewards", Json.encodeToString(activeRewards)) } }
    LaunchedEffect(userStats) {
        var updated = false
        activeMissions = activeMissions.map { mission ->
            val newProgress = when (mission.id) {
                "irrigate_5", "irrigate_20" -> userStats.irrigationCount
                "plants_3", "plants_5" -> userStats.plantsCreated
                "controls_10", "controls_30" -> userStats.controlChanges
                "snake_3", "snake_10" -> userStats.snakeGamesPlayed
                else -> mission.currentProgress
            }
            if (newProgress != mission.currentProgress) {
                updated = true
                mission.copy(currentProgress = newProgress.coerceAtMost(mission.targetValue), completed = newProgress >= mission.targetValue)
            } else mission
        }
        if (updated) saveMissions()
    }
    val totalXP = activeMissions.filter { it.completed }.sumOf { it.xpReward }
    val currentLevel = (totalXP / 500).coerceIn(1, 10)
    val xpForNextLevel = (currentLevel * 500) - totalXP
    LaunchedEffect(currentLevel) {
        var rewardsUpdated = false; var updatedStats = userStats
        activeRewards = activeRewards.map { reward ->
            if (reward.level <= currentLevel && !reward.unlocked) {
                rewardsUpdated = true
                val newThemes = updatedStats.themesUnlocked.toMutableList()
                when {
                    reward.rewardType.startsWith("theme_") -> {
                        val tn = reward.rewardType.removePrefix("theme_")
                        if (!newThemes.contains(tn)) newThemes.add(tn)
                    }
                    reward.rewardType == "all_themes" -> {
                        newThemes.clear(); newThemes.addAll(listOf("GREEN_NATURE", "AURORA", "OCEAN", "DARK", "LIGHT"))
                    }
                }
                updatedStats = updatedStats.copy(themesUnlocked = newThemes)
                reward.copy(unlocked = true)
            } else reward
        }
        if (rewardsUpdated) { onUserStatsChange(updatedStats); onSettingsChange(appSettings); saveRewards() }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("⭐ GreenPass", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = themeColors.primary, modifier = Modifier.padding(bottom = 8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = themeColors.primary), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Livello $currentLevel", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp)); Text("XP Totale: $totalXP", fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.3f))) {
                    val progress = if (currentLevel < 10) (totalXP % 500) / 500f else 1f
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(12.dp)).background(Color.White))
                }
                if (currentLevel < 10) {
                    Spacer(Modifier.height(8.dp))
                    Text("$xpForNextLevel XP al livello successivo", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("🎯 Missioni", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground, modifier = Modifier.padding(bottom = 12.dp))
        activeMissions.forEach { MissionCard(it, themeColors); Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("🎁 Ricompense", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground, modifier = Modifier.padding(bottom = 12.dp))
        activeRewards.forEach { reward ->
            RewardCard(reward, themeColors, currentLevel) { themeName ->
                onSettingsChange(appSettings.copy(theme = when (themeName) {
                    "AURORA" -> AppTheme.AURORA; "OCEAN" -> AppTheme.OCEAN
                    "DARK" -> AppTheme.DARK; "LIGHT" -> AppTheme.LIGHT; else -> appSettings.theme
                }))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun MissionCard(mission: BattlePassMission, themeColors: ThemeColors) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (mission.completed) Color(0xFFE8F5E9) else themeColors.cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mission.icon, fontSize = 32.sp); Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mission.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground)
                    if (mission.completed) Text("✅", fontSize = 20.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(mission.description, fontSize = 13.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(themeColors.surface)) {
                        val progress = (mission.currentProgress.toFloat() / mission.targetValue).coerceIn(0f, 1f)
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(4.dp))
                            .background(if (mission.completed) Color(0xFF4CAF50) else themeColors.primary))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${mission.currentProgress}/${mission.targetValue}", fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = themeColors.onBackground)
                }
                Spacer(Modifier.height(4.dp))
                Text("+${mission.xpReward} XP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
            }
        }
    }
}

@Composable
fun RewardCard(reward: BattlePassReward, themeColors: ThemeColors, currentLevel: Int, onApplyTheme: (String) -> Unit) {
    val isUnlockable = currentLevel >= reward.level
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = when {
            reward.unlocked -> Color(0xFFFFD700).copy(alpha = 0.2f)
            isUnlockable -> themeColors.primary.copy(alpha = 0.1f)
            else -> themeColors.cardBackground.copy(alpha = 0.5f)
        }),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape)
                .background(if (reward.unlocked) Color(0xFFFFD700).copy(alpha = 0.3f) else themeColors.surface),
                contentAlignment = Alignment.Center) {
                Text(reward.icon, fontSize = 28.sp, modifier = Modifier.alpha(if (isUnlockable) 1f else 0.3f))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Livello ${reward.level}", fontSize = 11.sp, color = themeColors.primary, fontWeight = FontWeight.Medium)
                Text(reward.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground,
                    modifier = Modifier.alpha(if (isUnlockable) 1f else 0.5f))
                Text(reward.description, fontSize = 13.sp, color = themeColors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.alpha(if (isUnlockable) 1f else 0.5f))
            }
            if (reward.unlocked) {
                if (reward.rewardType.startsWith("theme_"))
                    Button(onClick = { onApplyTheme(reward.rewardType.removePrefix("theme_")) },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                        shape = RoundedCornerShape(8.dp)) { Text("Usa", fontSize = 12.sp) }
                else Text("✅", fontSize = 24.sp)
            } else if (isUnlockable) Text("🔓", fontSize = 24.sp)
            else Text("🔒", fontSize = 24.sp)
        }
    }
}

// ==================== SETTINGS SCREEN ====================

@Composable
fun SettingsScreen(appSettings: AppSettings, onSettingsChange: (AppSettings) -> Unit, themeColors: ThemeColors, userStats: UserStats) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("⚙️ Impostazioni", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = themeColors.primary, modifier = Modifier.padding(bottom = 16.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎨 Tema Applicazione", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground, modifier = Modifier.padding(bottom = 12.dp))
                listOf(
                    AppTheme.GREEN_NATURE to "🌿 Green Nature",
                    AppTheme.AURORA to "🌸 Aurora",
                    AppTheme.OCEAN to "🌊 Ocean",
                    AppTheme.DARK to "🌙 Dark",
                    AppTheme.LIGHT to "☀️ Light"
                ).forEach { (theme, name) ->
                    val isUnlocked = userStats.themesUnlocked.contains(theme.name)
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = isUnlocked) { if (isUnlocked) onSettingsChange(appSettings.copy(theme = theme)) }
                        .background(if (appSettings.theme == theme) themeColors.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = appSettings.theme == theme,
                            onClick = { if (isUnlocked) onSettingsChange(appSettings.copy(theme = theme)) },
                            enabled = isUnlocked,
                            colors = RadioButtonDefaults.colors(selectedColor = themeColors.primary))
                        Spacer(Modifier.width(8.dp))
                        Text(name, fontSize = 16.sp,
                            color = if (isUnlocked) themeColors.onBackground else themeColors.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.weight(1f))
                        if (!isUnlocked) Text("🔒 Bloccato", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔊 Audio", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground, modifier = Modifier.padding(bottom = 12.dp))
                Text("Volume: ${(appSettings.volume * 100).toInt()}%", fontSize = 14.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
                Slider(value = appSettings.volume, onValueChange = { onSettingsChange(appSettings.copy(volume = it)) },
                    colors = SliderDefaults.colors(thumbColor = themeColors.primary, activeTrackColor = themeColors.primary))
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔔 Preferenze", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground, modifier = Modifier.padding(bottom = 12.dp))
                SettingSwitch("Notifiche", appSettings.notificationsEnabled, { onSettingsChange(appSettings.copy(notificationsEnabled = it)) }, themeColors)
                if (appSettings.notificationsEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = themeColors.primary.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "📋 Soglie di Allerta Attive", fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = themeColors.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            ThresholdRow("🌡️ Temp. alta", "> ${AlertThresholds.TEMP_MAX}°C", themeColors)
                            ThresholdRow("❄️ Temp. bassa", "< ${AlertThresholds.TEMP_MIN}°C", themeColors)
                            ThresholdRow("💧 Umidità alta", "> ${AlertThresholds.HUMIDITY_MAX}%", themeColors)
                            ThresholdRow("🏜️ Umidità bassa", "< ${AlertThresholds.HUMIDITY_MIN}%", themeColors)
                            ThresholdRow("💦 Suolo saturo", "> ${AlertThresholds.SOIL_MAX}%", themeColors)
                            ThresholdRow("🌵 Suolo secco", "< ${AlertThresholds.SOIL_MIN}%", themeColors)
                            ThresholdRow("🚰 Acqua bassa", "< ${AlertThresholds.WATER_LOW}%", themeColors)
                            ThresholdRow("🔌 Arduino offline", "Nessun dato per 3 min", themeColors)
                        }
                    }
                }
                SettingSwitch("Aggiornamento Automatico", appSettings.autoUpdate, { onSettingsChange(appSettings.copy(autoUpdate = it)) }, themeColors)
                SettingSwitch("Feedback Aptico", appSettings.hapticFeedback, { onSettingsChange(appSettings.copy(hapticFeedback = it)) }, themeColors)
            }
        }
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊 Le Tue Statistiche", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground, modifier = Modifier.padding(bottom = 12.dp))
                StatRow("💧 Irrigazioni Totali", "${userStats.irrigationCount}", themeColors)
                StatRow("🌱 Piante Create", "${userStats.plantsCreated}", themeColors)
                StatRow("🎮 Modifiche Controlli", "${userStats.controlChanges}", themeColors)
                StatRow("🐍 Partite Snake", "${userStats.snakeGamesPlayed}", themeColors)
                StatRow("⭐ XP Totale", "${userStats.totalXPEarned}", themeColors)
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ℹ️ Info Applicazione", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeColors.onBackground, modifier = Modifier.padding(bottom = 12.dp))
                Text("SegmaSirra v7.0", fontSize = 14.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
                Text("Smart Greenhouse Control System", fontSize = 12.sp, color = themeColors.onBackground.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Text("© 2026 - Powered by Firebase & Kotlin", fontSize = 10.sp, color = themeColors.onBackground.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, themeColors: ThemeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = themeColors.onBackground)
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = themeColors.onPrimary, checkedTrackColor = themeColors.primary,
                uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color.LightGray))
    }
}

@Composable
fun StatRow(label: String, value: String, themeColors: ThemeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeColors.primary)
    }
}

@Composable
fun ThresholdRow(label: String, value: String, themeColors: ThemeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = themeColors.onBackground.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = themeColors.onBackground.copy(alpha = 0.9f))
    }
}

// ==================== MAIN ACTIVITY ====================

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("Serra", "Permesso notifiche: ${if (granted) "concesso" else "negato"}")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }


        val stayLoggedInPrefs = getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)
        val stayLoggedIn = stayLoggedInPrefs.getBoolean("stay_logged_in", false)
        setContent {
            val context = LocalContext.current
            val sharedPreferences = context.getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)
            val savedSettings = try {
                val j = sharedPreferences.getString("app_settings", null)
                if (j != null) Json.decodeFromString<AppSettings>(j) else AppSettings()
            } catch (e: Exception) { AppSettings() }
            var appSettings by remember { mutableStateOf(savedSettings) }
            val themeColors = getThemeColors(appSettings.theme)
            MaterialTheme {
                var showAnimation by remember { mutableStateOf(true) }
                var isLoggedIn by remember { mutableStateOf(stayLoggedIn) }
                LaunchedEffect(Unit) { delay(3000); showAnimation = false }
                when {
                    showAnimation -> SplashAnimation(themeColors)
                    isLoggedIn -> {
                        LaunchedEffect(Unit) { SensorForegroundService.start(context) }
                        Dashboard(
                            onLogout = {
                                SensorForegroundService.stop(context)
                                isLoggedIn = false
                                sharedPreferences.edit { putBoolean("stay_logged_in", false) }
                            },
                            appSettings = appSettings,
                            onSettingsChange = { newSettings ->
                                appSettings = newSettings
                                sharedPreferences.edit {
                                    putString("app_settings", Json.encodeToString(newSettings))
                                    putBoolean("notificationsEnabled", newSettings.notificationsEnabled)
                                }
                            }
                        )
                    }
                    else -> LoginScreen(onLoginSuccess = {
                        isLoggedIn = true
                        sharedPreferences.edit { putBoolean("stay_logged_in", true) }
                    }, themeColors = themeColors)
                }
            }
        }
    }
}

// ==================== SPLASH ANIMATION ====================

@Composable
fun SplashAnimation(themeColors: ThemeColors) {
    val scale = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1.2f, tween(600, easing = FastOutSlowInEasing)); scale.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
        launch { rotation.animateTo(360f, tween(1000, easing = LinearEasing)) }
        launch { alpha.animateTo(1f, tween(800)) }
    }
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(themeColors.primary, themeColors.secondary))),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha.value)) {
            Text("🌿", fontSize = (80 * scale.value).sp, modifier = Modifier.rotate(rotation.value))
            Spacer(Modifier.height(16.dp))
            Text("SegmaSirra", fontSize = (48 * scale.value).sp, color = themeColors.onPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Smart Greenhouse", fontSize = 16.sp, color = themeColors.onPrimary.copy(alpha = 0.8f))
        }
    }
}

// ==================== LOGIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, themeColors: ThemeColors) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var stayLoggedIn by remember { mutableStateOf(false) }
    val mAuth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(themeColors.background, themeColors.surface)))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val scale = remember { Animatable(0.8f) }
            LaunchedEffect(Unit) { while (true) { scale.animateTo(1f, tween(1000)); scale.animateTo(0.8f, tween(1000)) } }
            Text("🌿", fontSize = 80.sp, modifier = Modifier.scale(scale.value))
            Spacer(Modifier.height(16.dp))
            Text("Benvenuto", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = themeColors.primary)
            Text("nella SegmaSirra", fontSize = 24.sp, color = themeColors.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(48.dp))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null) }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColors.primary,
                    focusedLabelColor = themeColors.primary, cursorColor = themeColors.primary))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColors.primary,
                    focusedLabelColor = themeColors.primary, cursorColor = themeColors.primary))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = stayLoggedIn, onCheckedChange = {
                    stayLoggedIn = it
                    sharedPreferences.edit { putBoolean("stay_logged_in", it) }
                }, colors = CheckboxDefaults.colors(checkedColor = themeColors.primary))
                Text("Resta connesso", color = themeColors.onBackground.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) onLoginSuccess()
                    else { errorMessage = "Account non esistente con queste credenziali."; showRegisterDialog = true }
                }
            }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary)) {
                Text("Accedi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (errorMessage.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp)) {
                    Text(errorMessage, color = Color(0xFFC62828), modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
    if (showRegisterDialog) {
        AlertDialog(onDismissRequest = { showRegisterDialog = false },
            title = { Text("Registrazione", fontWeight = FontWeight.Bold) },
            text = { Text("Vuoi registrarti con queste credenziali?") },
            confirmButton = {
                Button(onClick = {
                    mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                        if (task.isSuccessful) onLoginSuccess()
                        else errorMessage = "Registrazione fallita: ${task.exception?.message}"
                    }
                    showRegisterDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary)) { Text("Registrati") }
            },
            dismissButton = { TextButton(onClick = { showRegisterDialog = false }) { Text("Annulla", color = Color.Gray) } })
    }
}

// ==================== DASHBOARD ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(onLogout: () -> Unit, appSettings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("serra_prefs", Context.MODE_PRIVATE)
    val themeColors = getThemeColors(appSettings.theme)
    fun getCurrentDate() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    fun getCurrentTime() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val initialRules: List<PlantRule> = try {
        val j = sharedPreferences.getString("plantRules", null)
        if (j != null) Json.decodeFromString(j) else emptyList()
    } catch (e: Exception) { emptyList() }
    val initialStats: UserStats = try {
        val j = sharedPreferences.getString("user_stats", null)
        if (j != null) Json.decodeFromString(j) else UserStats()
    } catch (e: Exception) { UserStats() }
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
    var userStats by remember { mutableStateOf(initialStats) }
    var lastSensorUpdate by remember { mutableLongStateOf(0L) }
    var isArduinoAvailable by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf("...") }
    var humidity by remember { mutableStateOf("...") }
    var lightLevel by remember { mutableStateOf("...") }
    var soilHumidity by remember { mutableStateOf("...") }
    var remWater by remember { mutableStateOf("...") }
    var isInternetConnected by remember { mutableStateOf(false) }
    var weatherInfo by remember { mutableStateOf("...") }
    var currentDate by remember { mutableStateOf(getCurrentDate()) }
    var currentTime by remember { mutableStateOf(getCurrentTime()) }

    var isAppBlocked by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    val firebaseUrl = "https://serra-d44cc-default-rtdb.europe-west1.firebasedatabase.app/serra"
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun saveUserStats() { sharedPreferences.edit { putString("user_stats", Json.encodeToString(userStats)) } }
    fun updateFirebaseCommands() {
        val json = """{"lightOn":$lightOn,"fanOn":$fanOn,"roofOpen":$roofOpen,"irrigationOn":$irrigationOn,"humidifierOn":$humidifierOn}"""
        OkHttpClient().newCall(Request.Builder().url("$firebaseUrl/commands.json")
            .put(json.toRequestBody("application/json".toMediaType())).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { Log.e("Firebase", "Errore invio: ${e.message}") }
                override fun onResponse(call: Call, response: Response) {}
            })
    }
    fun checkInternet(): Boolean {
        val cap = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return cap?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun acquireControl() {
        val json = """{"email":"$currentUserEmail","timestamp":${System.currentTimeMillis()}}"""
        OkHttpClient().newCall(Request.Builder().url("$firebaseUrl/active_controller.json")
            .put(json.toRequestBody("application/json".toMediaType())).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    scope.launch(Dispatchers.Main) { isControllingByMe = true }
                }
            })
    }

    fun acquireControlWithPassword(inputPassword: String, onResult: (Boolean) -> Unit) {
        OkHttpClient().newCall(Request.Builder().url("$firebaseUrl/control_password.json").build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    scope.launch(Dispatchers.Main) { onResult(false) }
                }
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()?.trim('"') ?: ""
                    scope.launch(Dispatchers.Main) {
                        if (body == inputPassword) { acquireControl(); onResult(true) }
                        else onResult(false)
                    }
                }
            })
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isControllingByMe)
                OkHttpClient().newCall(Request.Builder().url("$firebaseUrl/active_controller.json").delete().build()).execute()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val response = OkHttpClient().newCall(
                Request.Builder().url("$firebaseUrl/min_app_version.json").build()
            ).execute()
            val minVersion = response.body?.string()?.trim('"', ' ')?.toIntOrNull() ?: 1
            if (APP_VERSION < minVersion) isAppBlocked = true
        } catch (e: Exception) {
            Log.e("VersionCheck", "Errore: ${e.message}")
        }
    }

    LaunchedEffect(Unit) { delay(500) }
    LaunchedEffect(Unit) {
        while (true) { isInternetConnected = checkInternet(); currentDate = getCurrentDate(); currentTime = getCurrentTime(); delay(5000) }
    }
    LaunchedEffect(Unit) {
        val client = OkHttpClient()
        while (true) {
            client.newCall(Request.Builder().url("$firebaseUrl/active_controller.json").build())
                .enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.body?.string()?.let { body ->
                            if (body == "null" || body.isEmpty()) { isControllingByMe = false; activeControllerEmail = null }
                            else try {
                                val json = JSONObject(body)
                                val em = json.optString("email", "")
                                val ts = json.optLong("timestamp", 0)
                                val now = System.currentTimeMillis()
                                when {
                                    (now - ts) > 120000 -> { isControllingByMe = false; activeControllerEmail = null }
                                    em == currentUserEmail -> { isControllingByMe = true; activeControllerEmail = em }
                                    else -> { isControllingByMe = false; activeControllerEmail = em }
                                }
                            } catch (e: Exception) { Log.e("Firebase", "Errore parsing: ${e.message}") }
                        }
                    }
                    override fun onFailure(call: Call, e: IOException) {}
                })
            if (isControllingByMe) {
                val upd = """{"email":"$currentUserEmail","timestamp":${System.currentTimeMillis()}}"""
                client.newCall(Request.Builder().url("$firebaseUrl/active_controller.json")
                    .put(upd.toRequestBody("application/json".toMediaType())).build())
                    .enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {}
                        override fun onResponse(call: Call, response: Response) {}
                    })
            }
            delay(5000)
        }
    }
    LaunchedEffect(Unit) {
        val client = OkHttpClient()
        while (true) {
            client.newCall(Request.Builder().url("$firebaseUrl/sensors.json").build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) {
                        response.body?.string()?.let { body ->
                            try {
                                val json = JSONObject(body)
                                lastSensorUpdate = System.currentTimeMillis()
                                temperature = json.optDouble("temperature", Double.NaN).takeIf { !it.isNaN() }?.toString() ?: "..."
                                humidity = json.optDouble("humidity", Double.NaN).takeIf { !it.isNaN() }?.toString() ?: "..."
                                lightLevel = json.optString("light", "...")
                                soilHumidity = json.optDouble("soil", Double.NaN).takeIf { !it.isNaN() }?.toString() ?: "..."
                                remWater = json.optDouble("remWater", Double.NaN).takeIf { !it.isNaN() }?.toString() ?: "..."

                                SensorHistoryManager.appendRecord(
                                    context,
                                    temperature.toDoubleOrNull(),
                                    humidity.toDoubleOrNull(),
                                    soilHumidity.toDoubleOrNull(),
                                    remWater.toDoubleOrNull(),
                                    lightLevel
                                )

                                val temp = temperature.toDoubleOrNull()
                                val airHum = humidity.toDoubleOrNull()
                                val soilHum = soilHumidity.toDoubleOrNull()
                                if (temp == null || airHum == null || soilHum == null) return

                                plantRules.find { it.isActive }?.let { rule ->
                                    var changed = false
                                    val newIrrigation = when {
                                        soilHum < rule.minSoilHumidity -> rule.actionSoilDry == Action.IRRIGATE_ON
                                        soilHum > rule.maxSoilHumidity -> rule.actionSoilWet == Action.IRRIGATE_ON
                                        else -> false
                                    }
                                    if (irrigationOn != newIrrigation) {
                                        irrigationOn = newIrrigation
                                        if (newIrrigation) { userStats = userStats.copy(irrigationCount = userStats.irrigationCount + 1); saveUserStats() }
                                        changed = true
                                    }
                                    val newHumidifier = when {
                                        airHum < rule.minAirHumidity -> rule.actionAirLow == Action.HUMIDIFIER_ON
                                        airHum > rule.maxAirHumidity -> rule.actionAirHigh == Action.HUMIDIFIER_ON
                                        else -> false
                                    }
                                    if (humidifierOn != newHumidifier) { humidifierOn = newHumidifier; changed = true }
                                    val newFan = when {
                                        temp < rule.minTemperature -> rule.actionTempLow == Action.FAN_ON
                                        temp > rule.maxTemperature -> rule.actionTempHigh == Action.FAN_ON
                                        else -> false
                                    }
                                    if (fanOn != newFan) { fanOn = newFan; changed = true }
                                    val newRoof = when {
                                        temp < rule.minTemperature -> rule.actionTempLow == Action.ROOF_OPEN
                                        temp > rule.maxTemperature -> rule.actionTempHigh == Action.ROOF_OPEN
                                        else -> false
                                    }
                                    if (roofOpen != newRoof) { roofOpen = newRoof; changed = true }
                                    if (changed) {
                                        sharedPreferences.edit {
                                            putBoolean("irrigationOn", irrigationOn); putBoolean("humidifierOn", humidifierOn)
                                            putBoolean("fanOn", fanOn); putBoolean("roofOpen", roofOpen)
                                        }
                                        updateFirebaseCommands()
                                    }
                                }
                            } catch (e: Exception) { Log.e("Firebase", "Parsing error: ${e.message}") }
                        }
                    }
                })
            client.newCall(Request.Builder().url("https://api.openweathermap.org/data/2.5/weather?q=Milano&appid=97dc1e64fb64a50f2b0d9d9b3070d4b9").build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) {
                        response.body?.string()?.let {
                            try {
                                val wa = JSONObject(it).optJSONArray("weather")
                                if (wa != null && wa.length() > 0)
                                    weatherInfo = wa.getJSONObject(0).optString("description", "").replaceFirstChar { c -> c.uppercaseChar() }
                            } catch (e: Exception) { Log.e("Meteo", "Parsing error: ${e.message}") }
                        }
                    }
                })
            delay(3000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            isArduinoAvailable = (System.currentTimeMillis() - lastSensorUpdate) <= 3 * 60 * 1000
            delay(10_000)
        }
    }

    if (isAppBlocked) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1B0000)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("🚫", fontSize = 72.sp)
                Spacer(Modifier.height(24.dp))
                Text("Versione non supportata", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("Questa versione dell'app non è più supportata.\nAggiorna l'app per continuare.",
                    fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Text("Versione attuale: $APP_VERSION", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
            }
        }
        return
    }



    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = themeColors.surface) {
            Column(modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(themeColors.primary, themeColors.secondary)))
                .padding(24.dp)) {
                Text("🌿", fontSize = 48.sp)
                Text("SegmaSirra", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeColors.onPrimary)
                Text(currentUserEmail, fontSize = 12.sp, color = themeColors.onPrimary.copy(alpha = 0.8f))
            }
            Spacer(Modifier.height(8.dp))
            DrawerMenuItem(Icons.Default.BarChart, "Dati", currentScreen == Screen.DATA, themeColors) {
                currentScreen = Screen.DATA; scope.launch { drawerState.close() }
            }
            DrawerMenuItem(Icons.Default.Settings, "Controlli Manuali", currentScreen == Screen.CONTROLS, themeColors) {
                currentScreen = Screen.CONTROLS; scope.launch { drawerState.close() }
            }
            DrawerMenuItem(Icons.Default.Dashboard, "Cards", currentScreen == Screen.CARDS, themeColors) {
                currentScreen = Screen.CARDS; scope.launch { drawerState.close() }
            }
            DrawerMenuItem(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                label = "Grafici",
                selected = currentScreen == Screen.CHARTS,
                themeColors = themeColors
            ) {
                currentScreen = Screen.CHARTS
                scope.launch { drawerState.close() }
            }
            DrawerMenuItem(Icons.Default.SportsEsports, "Segma Snake", currentScreen == Screen.SNAKE, themeColors) {
                currentScreen = Screen.SNAKE; scope.launch { drawerState.close() }
            }
            AnimatedBattlePassMenuItem {
                currentScreen = Screen.BATTLEPASS; scope.launch { drawerState.close() }
            }
            DrawerMenuItem(Icons.Default.Settings, "Impostazioni", currentScreen == Screen.SETTINGS, themeColors) {
                currentScreen = Screen.SETTINGS; scope.launch { drawerState.close() }
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = themeColors.onBackground.copy(alpha = 0.1f))
            DrawerMenuItem(Icons.AutoMirrored.Filled.Logout, "Esci", false, themeColors, isDestructive = true) { onLogout() }
        }
    }) {
        Scaffold(
            containerColor = themeColors.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌿 "); Text("SegmaSirra", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeColors.primary,
                        titleContentColor = themeColors.onPrimary,
                        navigationIconContentColor = themeColors.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(themeColors.background)) {
                StatusBar(isInternetConnected, isArduinoAvailable, currentDate, currentTime, weatherInfo, themeColors)
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    when (currentScreen) {
                        Screen.DATA -> DataScreen(temperature, humidity, lightLevel, soilHumidity, remWater, themeColors)
                        Screen.CONTROLS -> ControlsScreen(
                            isControllingByMe, activeControllerEmail, plantRules,
                            lightOn, fanOn, roofOpen, irrigationOn, humidifierOn,
                            onAcquireControl = { showPasswordDialog = true },
                            onToggleLight = {
                                lightOn = !lightOn
                                userStats = userStats.copy(controlChanges = userStats.controlChanges + 1); saveUserStats()
                                sharedPreferences.edit { putBoolean("lightOn", lightOn) }; updateFirebaseCommands()
                            },
                            onToggleFan = {
                                fanOn = !fanOn
                                userStats = userStats.copy(controlChanges = userStats.controlChanges + 1); saveUserStats()
                                sharedPreferences.edit { putBoolean("fanOn", fanOn) }; updateFirebaseCommands()
                            },
                            onToggleRoof = {
                                roofOpen = !roofOpen
                                userStats = userStats.copy(controlChanges = userStats.controlChanges + 1); saveUserStats()
                                sharedPreferences.edit { putBoolean("roofOpen", roofOpen) }; updateFirebaseCommands()
                            },
                            onToggleIrrigation = {
                                irrigationOn = !irrigationOn
                                if (irrigationOn) userStats = userStats.copy(irrigationCount = userStats.irrigationCount + 1)
                                userStats = userStats.copy(controlChanges = userStats.controlChanges + 1); saveUserStats()
                                sharedPreferences.edit { putBoolean("irrigationOn", irrigationOn) }; updateFirebaseCommands()
                            },
                            onToggleHumidifier = {
                                humidifierOn = !humidifierOn
                                userStats = userStats.copy(controlChanges = userStats.controlChanges + 1); saveUserStats()
                                sharedPreferences.edit { putBoolean("humidifierOn", humidifierOn) }; updateFirebaseCommands()
                            },
                            themeColors
                        )
                        Screen.CARDS -> CardsScreen(
                            plantRules, isControllingByMe, activeControllerEmail,
                            temperature, humidity, soilHumidity,
                            onAcquireControl = { showPasswordDialog = true },
                            onRulesChange = { newRules ->
                                val oldCount = plantRules.size; plantRules = newRules
                                if (newRules.size > oldCount) { userStats = userStats.copy(plantsCreated = userStats.plantsCreated + 1); saveUserStats() }
                                sharedPreferences.edit { putString("plantRules", Json.encodeToString(newRules)) }
                            },
                            themeColors
                        )
                        Screen.CHARTS -> ChartsScreen(themeColors)
                        Screen.SNAKE -> SnakeGameScreen(themeColors, userStats) { newStats -> userStats = newStats; saveUserStats() }
                        Screen.BATTLEPASS -> BattlePassScreen(themeColors, sharedPreferences, userStats, appSettings, onSettingsChange) { newStats -> userStats = newStats; saveUserStats() }
                        Screen.SETTINGS -> SettingsScreen(appSettings, onSettingsChange, themeColors, userStats)
                    }
                }
            }
        }
        if (showPasswordDialog) {
            var inputPwd by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false; passwordError = false },
                title = { Text("🔐 Inserisci Password", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Inserisci la password per prendere il controllo della serra.", fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = inputPwd,
                            onValueChange = { inputPwd = it; passwordError = false },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            isError = passwordError,
                            supportingText = if (passwordError) {{ Text("Password errata!", color = Color.Red) }} else null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        acquireControlWithPassword(inputPwd) { success ->
                            if (success) { showPasswordDialog = false; passwordError = false }
                            else passwordError = true
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary)) {
                        Text("Conferma")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false; passwordError = false }) {
                        Text("Annulla")
                    }
                }
            )
        }
    }
}