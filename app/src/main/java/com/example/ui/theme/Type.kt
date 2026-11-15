package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R

val BengaliFontFamily = FontFamily(
    Font(R.font.hind_siliguri, FontWeight.Normal),
    Font(R.font.hind_siliguri, FontWeight.Medium),
    Font(R.font.hind_siliguri, FontWeight.SemiBold),
    Font(R.font.hind_siliguri, FontWeight.Bold),
    Font(R.font.hind_siliguri, FontWeight.ExtraBold)
)

private val BengaliLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

@Suppress("DEPRECATION")
private fun bengaliTextStyle(
    fontWeight: FontWeight,
    fontSizeSp: Int,
    lineHeightSp: Int
) = TextStyle(
    fontFamily = BengaliFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = BengaliLineHeightStyle,
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

// Page-balanced, spacious and smoothly legible Bengali typography hierarchy
val BengaliTypography = Typography(
    displayLarge = bengaliTextStyle(FontWeight.Bold, 28, 36),
    displayMedium = bengaliTextStyle(FontWeight.Bold, 25, 33),
    displaySmall = bengaliTextStyle(FontWeight.Bold, 22, 30),
    headlineLarge = bengaliTextStyle(FontWeight.Bold, 24, 32),
    headlineMedium = bengaliTextStyle(FontWeight.Bold, 21, 28),
    headlineSmall = bengaliTextStyle(FontWeight.SemiBold, 19, 26),
    titleLarge = bengaliTextStyle(FontWeight.Bold, 19, 26),
    titleMedium = bengaliTextStyle(FontWeight.SemiBold, 17, 24),
    titleSmall = bengaliTextStyle(FontWeight.SemiBold, 16, 22),
    bodyLarge = bengaliTextStyle(FontWeight.Normal, 16, 24),
    bodyMedium = bengaliTextStyle(FontWeight.Normal, 15, 22),
    bodySmall = bengaliTextStyle(FontWeight.Normal, 14, 20),
    labelLarge = bengaliTextStyle(FontWeight.SemiBold, 15, 21),
    labelMedium = bengaliTextStyle(FontWeight.SemiBold, 14, 19),
    labelSmall = bengaliTextStyle(FontWeight.Medium, 13, 18)
)
