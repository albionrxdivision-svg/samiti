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

// Page-balanced, larger and smoothly legible Bengali typography hierarchy across all screens
val BengaliTypography = Typography(
    displayLarge = bengaliTextStyle(FontWeight.Bold, 30, 38),
    displayMedium = bengaliTextStyle(FontWeight.Bold, 27, 35),
    displaySmall = bengaliTextStyle(FontWeight.Bold, 24, 32),
    headlineLarge = bengaliTextStyle(FontWeight.Bold, 26, 34),
    headlineMedium = bengaliTextStyle(FontWeight.Bold, 23, 31),
    headlineSmall = bengaliTextStyle(FontWeight.SemiBold, 21, 29),
    titleLarge = bengaliTextStyle(FontWeight.Bold, 21, 29),
    titleMedium = bengaliTextStyle(FontWeight.SemiBold, 19, 26),
    titleSmall = bengaliTextStyle(FontWeight.SemiBold, 18, 24),
    bodyLarge = bengaliTextStyle(FontWeight.Normal, 18, 26),
    bodyMedium = bengaliTextStyle(FontWeight.Normal, 17, 24),
    bodySmall = bengaliTextStyle(FontWeight.Normal, 15, 22),
    labelLarge = bengaliTextStyle(FontWeight.SemiBold, 16, 23),
    labelMedium = bengaliTextStyle(FontWeight.SemiBold, 15, 21),
    labelSmall = bengaliTextStyle(FontWeight.Medium, 14, 19)
)
