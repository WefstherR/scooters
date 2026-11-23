package cl.recibos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF1F2328),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E5E8),
    onPrimaryContainer = Color(0xFF1F2328),
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAED),
    onSecondaryContainer = Color(0xFF1F2328),
    tertiary = Color(0xFF1B7F3B),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF1C1F23),
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF1C1F23),
    surfaceVariant = Color(0xFFECEDEF),
    onSurfaceVariant = Color(0xFF5B616B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F2F4),
    surfaceContainerHigh = Color(0xFFECEDEF),
    surfaceContainerHighest = Color(0xFFE6E7EA),
    outline = Color(0xFFC9CDD2),
    outlineVariant = Color(0xFFE3E5E8),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE6E7EA),
    onPrimary = Color(0xFF1C1F23),
    primaryContainer = Color(0xFF33373D),
    onPrimaryContainer = Color(0xFFE6E7EA),
    secondary = Color(0xFFB8BDC5),
    secondaryContainer = Color(0xFF2E3238),
    onSecondaryContainer = Color(0xFFE6E7EA),
    tertiary = Color(0xFF7BD495),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE6E7EA),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE6E7EA),
    surfaceVariant = Color(0xFF2A2D32),
    onSurfaceVariant = Color(0xFFAEB3BB),
    surfaceContainerLowest = Color(0xFF0D0E10),
    surfaceContainerLow = Color(0xFF1A1C20),
    surfaceContainer = Color(0xFF1E2024),
    surfaceContainerHigh = Color(0xFF24272B),
    surfaceContainerHighest = Color(0xFF2E3136),
    outline = Color(0xFF4A4F57),
    outlineVariant = Color(0xFF33373D),
    error = Color(0xFFF2B8B5),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

@Composable
fun RecibosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Colores semánticos de estado, legibles en claro y oscuro. */
object StatusColors {
    val paid = Color(0xFF1B7F3B)
    val pending = Color(0xFFB45309)
    val void = Color(0xFFB3261E)
    val draft = Color(0xFF6B7280)
}
