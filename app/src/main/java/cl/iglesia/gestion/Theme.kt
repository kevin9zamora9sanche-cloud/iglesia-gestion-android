package cl.iglesia.gestion

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF1E3A8A)
val BrandLight = Color(0xFF2563EB)
val BrandSurface = Color(0xFFEFF6FF)
val Gold = Color(0xFFC9A84C)
val SurfaceWarm = Color(0xFFFAF8F5)
val BorderWarm = Color(0xFFE8E2DA)
val MutedWarm = Color(0xFFF2EDE6)
val TextMuted = Color(0xFF78716C)
val Navy = Color(0xFF1E3A5F)
val Green = Color(0xFF5A7A2B)

@Composable
fun ChurchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            secondary = Gold,
            background = SurfaceWarm,
            surface = Color.White,
            onSurface = Color(0xFF1C1917),
            outline = BorderWarm,
        ),
        content = content,
    )
}
