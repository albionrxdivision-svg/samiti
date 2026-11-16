package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Cooperative Palette: Rich Emerald Green, Warm Saffron Gold, Deep Navy Blue, High-Contrast Neutrals
val EmeraldGreenPrimary = Color(0xFF0D6E4B)
val EmeraldGreenLight = Color(0xFF1B8F65)
val EmeraldGreenDark = Color(0xFF07442E)

val GoldenAccent = Color(0xFFF4B43A)
val GoldenAmber = Color(0xFFD9820B)
val GoldenDarkText = Color(0xFF7A4800)

val NavyBlueSecondary = Color(0xFF1A365D)
val NavyBlueDark = Color(0xFF0D1F38)

val HeaderSlateNavy = Color(0xFF1B2E4B)
val HeaderSlateDeep = Color(0xFF122036)

val SilverBackgroundTop = Color(0xFFE7ECF2)
val SilverBackgroundLight = Color(0xFFDCE2EA)
val SilverBackgroundBottom = Color(0xFFCED6E2)
val SilverSurfaceVariant = Color(0xFFEAEEF4)
val SilverOutline = Color(0xFFB7C2D0)
val SoftSurfaceLight = SilverBackgroundLight
val SurfaceCardLight = Color(0xFFFDFEFF)
val TextDark = Color(0xFF111827)
val TextSecondary = Color(0xFF374151)
val OutlineBorder = SilverOutline

val SuccessGreen = Color(0xFF1E7E34)
val WarningOrange = Color(0xFFD95B00)
val DangerRed = Color(0xFFC62828)
val PendingYellow = Color(0xFFF9A825)

// Dark Theme Variants (High Contrast)
val EmeraldGreenPrimaryDark = Color(0xFF66E2AA)
val EmeraldGreenContainerDark = Color(0xFF134532)
val NavySecondaryDark = Color(0xFF93BDF2)
val BackgroundDark = Color(0xFF0E1411)
val SurfaceDark = Color(0xFF18211D)
val TextLight = Color(0xFFEDF5F0)
val TextSecondaryDark = Color(0xFFC2D3C9)

// Vibrant Gradient Card Palette (high contrast for white text)
val CardPurpleGradient = listOf(Color(0xFF5B3EE0), Color(0xFF422BB5))
val CardTealGradient = listOf(Color(0xFF006D77), Color(0xFF008B96))
val CardEmeraldGradient = listOf(Color(0xFF0F7A46), Color(0xFF199D5E))
val CardAmberGradient = listOf(Color(0xFFC26A00), Color(0xFFDC7E09))
val CardPinkGradient = listOf(Color(0xFFC2185B), Color(0xFFDF3274))
val CardOrangeGradient = listOf(Color(0xFFC84606), Color(0xFFE36119))
val CardBlueGradient = listOf(Color(0xFF1E4FD6), Color(0xFF336EE8))
val CardRoseGradient = listOf(Color(0xFFB71C2E), Color(0xFFD63043))

data class CardColorAccent(
    val gradient: List<Color>,
    val softSurface: Color,
    val accentInk: Color
)

val VibrantCardAccents = listOf(
    CardColorAccent(CardPurpleGradient, Color(0xFFF4F1FF), Color(0xFF4338CA)),
    CardColorAccent(CardTealGradient, Color(0xFFECFEFF), Color(0xFF0E7490)),
    CardColorAccent(CardEmeraldGradient, Color(0xFFECFDF5), Color(0xFF065F46)),
    CardColorAccent(CardBlueGradient, Color(0xFFEFF6FF), Color(0xFF1E40AF)),
    CardColorAccent(CardAmberGradient, Color(0xFFFFFBEB), Color(0xFF92400E)),
    CardColorAccent(CardPinkGradient, Color(0xFFFDF2F8), Color(0xFF9D174D)),
    CardColorAccent(CardOrangeGradient, Color(0xFFFFF7ED), Color(0xFF9A3412)),
    CardColorAccent(CardRoseGradient, Color(0xFFFFF1F2), Color(0xFF9F1239))
)

