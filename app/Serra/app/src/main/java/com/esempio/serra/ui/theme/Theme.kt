package com.esempio.serra.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Definire i colori per la palette (Verde, Bianco, Oro)
val GreenLight = Color(0xFF76D7B7)  // Verde chiaro
val GreenDark = Color(0xFF2E8B57)   // Verde scuro
val White = Color(0xFFFFFFFF)       // Bianco
val Gold = Color(0xFFFFD700)        // Oro

private val DarkColorScheme = darkColorScheme(
    primary = GreenDark,       // Verde scuro per il testo principale
    secondary = GreenLight,    // Verde chiaro per gli elementi secondari
    tertiary = Gold,           // Oro per evidenziare parti importanti
    background = Color(0xFF121212), // Sfondo scuro
    surface = Color(0xFF1E1E1E),    // Superficie scura
    onPrimary = White,            // Testo chiaro su verde scuro
    onSecondary = White,          // Testo chiaro su verde chiaro
    onTertiary = White,           // Testo chiaro su oro
    onBackground = White,         // Testo bianco su sfondo scuro
    onSurface = White              // Testo bianco su superfici scure
)

private val LightColorScheme = lightColorScheme(
    primary = GreenLight,       // Verde chiaro per il testo principale
    secondary = GreenDark,      // Verde scuro per gli elementi secondari
    tertiary = Gold,            // Oro per evidenziare parti importanti
    background = White,         // Sfondo bianco
    surface = White,            // Superficie bianca
    onPrimary = White,          // Testo bianco su verde chiaro
    onSecondary = White,        // Testo bianco su verde scuro
    onTertiary = White,         // Testo bianco su oro
    onBackground = Color(0xFF121212),  // Testo scuro su sfondo chiaro
    onSurface = Color(0xFF121212)       // Testo scuro su superfici chiare
)

@Composable
fun SerraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
