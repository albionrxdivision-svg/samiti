package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    surfaceContainerLow = Color(0xFFF1F3F6),
    surfaceContainer = Color(0xFFE7EBEF),
    surfaceContainerHigh = Color(0xFFDDE2E8),
    surfaceContainerHighest = Color(0xFFD3D9E0),
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
    background = Color(0xFF0F1713),
    onBackground = Color(0xFFF3F9F5),
    surface = Color(0xFF1A2520),
    onSurface = Color(0xFFF3F9F5),
    surfaceVariant = Color(0xFF25342D),
    onSurfaceVariant = Color(0xFFD0E0D7),
    outline = Color(0xFF5A7567),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF691818),
    onErrorContainer = Color(0xFFFEE2E2)
)

@Composable
fun ekotaBrandTextColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF6EE7B7) else EmeraldGreenDark

@Composable
fun ekotaNavyTextColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF93C5FD) else NavyBlueSecondary

@Composable
fun ekotaSuccessTextColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF86EFAC) else SuccessGreen

@Composable
fun ekotaDangerTextColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else DangerRed

@Composable
fun ekotaWarningTextColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFCD34D) else Color(0xFFE65100)

@Composable
fun ekotaBadgeSuccessBg(): Color =
    if (isSystemInDarkTheme()) Color(0xFF134E2E) else Color(0xFFE8F5E9)

@Composable
fun ekotaBadgeDangerBg(): Color =
    if (isSystemInDarkTheme()) Color(0xFF5C1515) else Color(0xFFFFEBEE)

@Composable
fun ekotaBadgeWarningBg(): Color =
    if (isSystemInDarkTheme()) Color(0xFF4F3205) else Color(0xFFFFF3E0)

@Composable
fun EkotaSamitiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = EmeraldGreenPrimary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BengaliTypography,
        content = content
    )
}
