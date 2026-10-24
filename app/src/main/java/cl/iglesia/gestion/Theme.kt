package cl.iglesia.gestion

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta institucional. Todos los colores de texto cumplen WCAG AA (4.5:1) sobre blanco.
val Brand = Color(0xFF1E3A8A)
val BrandLight = Color(0xFF2563EB)
val BrandSurface = Color(0xFFEFF6FF)
val BrandContainer = Color(0xFFDBEAFE)
val Gold = Color(0xFFC9A84C)
val GoldText = Color(0xFF8A6A12)
val GoldSurface = Color(0xFFFBF5E3)
val AppBackground = Color(0xFFF5F7FB)
val CardBorder = Color(0xFFE2E8F0)
val MutedSurface = Color(0xFFF1F5F9)
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF475569)
// Más oscuro que slate-500 para mantener 4.5:1 también sobre AppBackground y MutedSurface.
val TextMuted = Color(0xFF5B6B80)
val Success = Color(0xFF15803D)
val SuccessSurface = Color(0xFFDCFCE7)
val Warning = Color(0xFFB45309)
val WarningSurface = Color(0xFFFEF3C7)
val Danger = Color(0xFFB42318)
val DangerSurface = Color(0xFFFEE4E2)

/** Colores semánticos por estado: Por hacer, En curso, En pausa, Completada, Cancelada. */
data class StatusStyle(val label: String, val content: Color, val container: Color)

fun statusStyle(status: String, feminine: Boolean = true): StatusStyle = when (status) {
    "pending" -> StatusStyle("Por hacer", TextSecondary, MutedSurface)
    "in_progress" -> StatusStyle("En curso", BrandLight, BrandSurface)
    "paused" -> StatusStyle("En pausa", Warning, WarningSurface)
    "completed" -> StatusStyle(if (feminine) "Completada" else "Completado", Success, SuccessSurface)
    "cancelled" -> StatusStyle(if (feminine) "Cancelada" else "Cancelado", Danger, DangerSurface)
    else -> StatusStyle("Sin estado", TextSecondary, MutedSurface)
}

// Escala ampliada: la mayoría de los usuarios son personas mayores. Ningún texto
// baja de 15 sp y el cuerpo principal usa 18 sp, con interlineado generoso.
private val ChurchTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

private val ChurchShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ChurchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            primaryContainer = BrandContainer,
            onPrimaryContainer = Brand,
            secondary = GoldText,
            onSecondary = Color.White,
            secondaryContainer = GoldSurface,
            onSecondaryContainer = GoldText,
            tertiary = BrandLight,
            background = AppBackground,
            onBackground = TextPrimary,
            surface = Color.White,
            onSurface = TextPrimary,
            surfaceVariant = MutedSurface,
            onSurfaceVariant = TextSecondary,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = MutedSurface,
            outline = Color(0xFFCBD5E1),
            outlineVariant = CardBorder,
            error = Danger,
            errorContainer = DangerSurface,
        ),
        typography = ChurchTypography,
        shapes = ChurchShapes,
        content = content,
    )
}
