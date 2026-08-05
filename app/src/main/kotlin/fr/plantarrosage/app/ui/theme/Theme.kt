package fr.plantarrosage.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------------------------
// Palette végétale
//
// Trois familles, empruntées à ce qu'on a sous les yeux quand on s'occupe d'une plante :
//   — le feuillage, vert profond, porte l'identité et toutes les actions ;
//   — la terre cuite du pot signale l'arrosage, le manque d'eau, l'urgence ;
//   — la floraison, mauve, distingue les informations de sécurité (toxicité) du reste.
// ---------------------------------------------------------------------------------------------

private val Feuille = Color(0xFF2F6B41)
private val FeuilleClaire = Color(0xFFB4F1C2)
private val FeuilleSombre = Color(0xFF00210E)
private val FeuillePale = Color(0xFF98D8A8)
private val FeuilleNuit = Color(0xFF1A5230)

private val TerreCuite = Color(0xFF9C5B34)
private val TerreClaire = Color(0xFFFFDBC8)
private val TerreSombre = Color(0xFF34160A)
private val TerrePale = Color(0xFFFFB68D)
private val TerreNuit = Color(0xFF7A3F1C)

private val Floraison = Color(0xFF6B4E9E)
private val FloraisonClaire = Color(0xFFEBDDFF)
private val FloraisonSombre = Color(0xFF260E4F)
private val FloraisonPale = Color(0xFFD3BBFF)
private val FloraisonNuit = Color(0xFF533A82)

// Neutres tirés vers le végétal : le fond est un sable très légèrement verdi, jamais un gris pur.
private val Sable = Color(0xFFF7F6EE)
private val SableSurface = Color(0xFFFFFDF7)
private val Mousse = Color(0xFFDDE6D8)
private val MousseTexte = Color(0xFF424B3E)
private val Ecorce = Color(0xFF1A1C18)

private val NuitTerreau = Color(0xFF11150F)
private val NuitSurface = Color(0xFF181D15)
private val NuitVariante = Color(0xFF414B3D)
private val NuitTexte = Color(0xFFE2E4DC)

private val LightColors = lightColorScheme(
    primary = Feuille,
    onPrimary = Color.White,
    primaryContainer = FeuilleClaire,
    onPrimaryContainer = FeuilleSombre,
    secondary = TerreCuite,
    onSecondary = Color.White,
    secondaryContainer = TerreClaire,
    onSecondaryContainer = TerreSombre,
    tertiary = Floraison,
    onTertiary = Color.White,
    tertiaryContainer = FloraisonClaire,
    onTertiaryContainer = FloraisonSombre,
    error = Color(0xFFA8352A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410100),
    background = Sable,
    onBackground = Ecorce,
    surface = SableSurface,
    onSurface = Ecorce,
    surfaceVariant = Mousse,
    onSurfaceVariant = MousseTexte,
    outline = Color(0xFF72796C),
    outlineVariant = Color(0xFFC2CABB),
)

private val DarkColors = darkColorScheme(
    primary = FeuillePale,
    onPrimary = Color(0xFF00391B),
    primaryContainer = FeuilleNuit,
    onPrimaryContainer = FeuilleClaire,
    secondary = TerrePale,
    onSecondary = Color(0xFF54200A),
    secondaryContainer = TerreNuit,
    onSecondaryContainer = TerreClaire,
    tertiary = FloraisonPale,
    onTertiary = Color(0xFF3B2069),
    tertiaryContainer = FloraisonNuit,
    onTertiaryContainer = FloraisonClaire,
    error = Color(0xFFFFB4AA),
    onError = Color(0xFF690003),
    errorContainer = Color(0xFF930008),
    onErrorContainer = Color(0xFFFFDAD5),
    background = NuitTerreau,
    onBackground = NuitTexte,
    surface = NuitSurface,
    onSurface = NuitTexte,
    surfaceVariant = NuitVariante,
    onSurfaceVariant = Color(0xFFC2CBB9),
    outline = Color(0xFF8C9385),
    outlineVariant = NuitVariante,
)

/**
 * Couleurs d'état d'arrosage.
 *
 * Elles ne rentrent dans aucun rôle Material : « en retard » n'est pas une erreur de
 * l'application, et « aujourd'hui » n'est ni primaire ni secondaire. Les garder à part évite de
 * détourner `error` de son sens et permet de les régler séparément dans les deux thèmes.
 */
data class WateringColors(
    val overdue: Color,
    val onOverdue: Color,
    val dueToday: Color,
    val onDueToday: Color,
    val upcoming: Color,
    val onUpcoming: Color,
    /** Aplat léger posé derrière les grands visuels et les en-têtes de section. */
    val leafTint: Color,
)

private val LightWatering = WateringColors(
    overdue = Color(0xFFFFDAD5),
    onOverdue = Color(0xFF8C1D14),
    dueToday = Color(0xFFFFE9BE),
    onDueToday = Color(0xFF6B4A00),
    upcoming = FeuilleClaire,
    onUpcoming = Color(0xFF11512A),
    leafTint = Color(0xFFEAF2E4),
)

private val DarkWatering = WateringColors(
    overdue = Color(0xFF7A2019),
    onOverdue = Color(0xFFFFDAD5),
    dueToday = Color(0xFF5C4200),
    onDueToday = Color(0xFFFFE9BE),
    upcoming = FeuilleNuit,
    onUpcoming = FeuilleClaire,
    leafTint = Color(0xFF1E2A1D),
)

private val LocalWateringColors = staticCompositionLocalOf { LightWatering }

/** Accès aux couleurs d'arrosage depuis n'importe quel composable placé sous le thème. */
object PlantTheme {
    val watering: WateringColors
        @Composable @ReadOnlyComposable get() = LocalWateringColors.current
}

private val PlantTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

/**
 * Les couleurs dynamiques d'Android 12+ sont volontairement écartées : elles remplaçaient la
 * palette végétale par celle du fond d'écran, et l'application perdait exactement ce qui la rend
 * lisible d'un coup d'œil — le vert du feuillage et la terre cuite de l'arrosage en retard.
 */
@Composable
fun PlantArrosageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val watering = if (darkTheme) DarkWatering else LightWatering

    CompositionLocalProvider(LocalWateringColors provides watering) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PlantTypography,
            content = content,
        )
    }
}
