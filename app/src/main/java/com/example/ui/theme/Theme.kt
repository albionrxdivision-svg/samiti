package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalIsDarkEkotaTheme = staticCompositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = EmeraldGreenPrimary,
    onPrimary = SurfaceCardLight,
    primaryContainer = Color(0xFFD3EFE1),
    onPrimaryContainer = EmeraldGreenDark,
    secondary = NavyBlueSecondary,
    onSecondary = SurfaceCardLight,
    secondaryContainer = Color(0xFFD9E6F7),
    onSecondaryContainer = NavyBlueDark,
    tertiary = GoldenAmber,
    onTertiary = SurfaceCardLight,
    tertiaryContainer = Color(0xFFFFECC8),
    onTertiaryContainer = GoldenDarkText,
    background = SilverBackgroundLight,
    onBackground = TextDark,
    surface = SurfaceCardLight,
    onSurface = TextDark,
    surfaceVariant = SilverSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFE7ECF2),
    surfaceContainerHigh = Color(0xFFDDE3EB),
    surfaceContainerHighest = Color(0xFFD2D9E3),
    outline = OutlineBorder,
    error = DangerRed,
    onError = SurfaceCardLight,
    errorContainer = Color(0xFFFFE5E5),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF042F22),
    primaryContainer = Color(0xFF124D38),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF0A2240),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = Color(0xFF4F3404),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF222933),
    onBackground = Color(0xFFF3F6FA),
    surface = Color(0xFF2D3642),
    onSurface = Color(0xFFF3F6FA),
    surfaceVariant = Color(0xFF394452),
    onSurfaceVariant = Color(0xFFD5DCE6),
    outline = Color(0xFF64748B),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF691818),
    onErrorContainer = Color(0xFFFEE2E2)
)

@Composable
fun ekotaSilverBackgroundBrush(): Brush =
    if (LocalIsDarkEkotaTheme.current) {
        Brush.verticalGradient(listOf(Color(0xFF27303C), Color(0xFF1E252E)))
    } else {
        Brush.verticalGradient(
            listOf(SilverBackgroundTop, SilverBackgroundLight, SilverBackgroundBottom)
        )
    }

@Composable
fun ekotaSectionTitleColor(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFF93C5FD) else HeaderSlateNavy

@Composable
fun ekotaBrandTextColor(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFF6EE7B7) else EmeraldGreenDark

@Composable
fun ekotaNavyTextColor(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFF93C5FD) else NavyBlueSecondary

@Composable
fun ekotaSuccessTextColor(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFF86EFAC) else SuccessGreen

@Composable
fun ekotaDangerTextColor(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFFFCA5A5) else DangerRed

@Composable
fun ekotaWarningTextColor(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFFFCD34D) else Color(0xFFE65100)

@Composable
fun ekotaBadgeSuccessBg(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFF134E2E) else Color(0xFFE8F5E9)

@Composable
fun ekotaBadgeDangerBg(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFF5C1515) else Color(0xFFFFEBEE)

@Composable
fun ekotaBadgeWarningBg(): Color =
    if (LocalIsDarkEkotaTheme.current) Color(0xFF4F3205) else Color(0xFFFFF3E0)

@Composable
fun EkotaSamitiTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = HeaderSlateNavy.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalIsDarkEkotaTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BengaliTypography,
            content = content
        )
    }
}
