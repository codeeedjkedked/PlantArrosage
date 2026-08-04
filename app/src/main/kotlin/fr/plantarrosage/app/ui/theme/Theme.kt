package fr.plantarrosage.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette végétale : verts profonds, terre cuite pour les alertes d'arrosage en retard.
private val VertFeuille = Color(0xFF2E6B3E)
private val VertClair = Color(0xFFA8D5B5)
private val VertSombre = Color(0xFF1B4527)
private val TerreCuite = Color(0xFFB4552E)
private val TerreClaire = Color(0xFFFFDBCC)
private val Sable = Color(0xFFF6F4EC)

private val LightColors = lightColorScheme(
    primary = VertFeuille,
    onPrimary = Color.White,
    primaryContainer = VertClair,
    onPrimaryContainer = VertSombre,
    secondary = TerreCuite,
    onSecondary = Color.White,
    secondaryContainer = TerreClaire,
    onSecondaryContainer = Color(0xFF3D1200),
    background = Sable,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = VertClair,
    onPrimary = VertSombre,
    primaryContainer = Color(0xFF2E6B3E),
    onPrimaryContainer = VertClair,
    secondary = Color(0xFFFFB599),
    onSecondary = Color(0xFF5F1600),
    secondaryContainer = Color(0xFF852E0C),
    onSecondaryContainer = TerreClaire,
)

private val PlantTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PlantArrosageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PlantTypography,
        content = content,
    )
}
