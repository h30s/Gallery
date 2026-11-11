package com.oxlo.gallary.ui.theme

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
import com.oxlo.gallary.data.settings.ThemeMode

private val Violet = Color(0xFF7C5CFF)
private val VioletLight = Color(0xFFB9A6FF)
private val Teal = Color(0xFF38E1C4)
private val Rose = Color(0xFFFF7A9A)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF1B0F45),
    primaryContainer = Color(0xFF32245F),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Teal,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF0F3A33),
    onSecondaryContainer = Color(0xFFB9F5EA),
    tertiary = Rose,
    onTertiary = Color(0xFF3A0B18),
    background = Color(0xFF0E0E12),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF14141B),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1E1E27),
    onSurfaceVariant = Color(0xFFA9A9B8),
    surfaceContainer = Color(0xFF17171F),
    surfaceContainerHigh = Color(0xFF1C1C25),
    surfaceContainerHighest = Color(0xFF22222D),
    outline = Color(0xFF33333F),
    outlineVariant = Color(0xFF26262F),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3BE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF1B0F45),
    secondary = Color(0xFF0F8F7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F5EA),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFFC1355C),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF14141B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14141B),
    surfaceVariant = Color(0xFFEDEDF3),
    onSurfaceVariant = Color(0xFF5B5B6B),
    surfaceContainer = Color(0xFFF1F1F6),
    surfaceContainerHigh = Color(0xFFEAEAF1),
    surfaceContainerHighest = Color(0xFFE3E3EC),
    outline = Color(0xFFC6C6D4),
    outlineVariant = Color(0xFFDDDDE7),
    error = Color(0xFFC62828)
)

private val GallaryTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun GallaryTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GallaryTypography,
        content = content
    )
}
