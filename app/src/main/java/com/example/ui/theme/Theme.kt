package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RoyalLudoColorScheme = darkColorScheme(
    primary = RoyalGold,
    onPrimary = Color(0xFF1A103C),
    primaryContainer = RoyalCardVariant,
    onPrimaryContainer = RoyalGoldLight,
    secondary = RoyalSapphireBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF173A6A),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = RoyalEmeraldGreen,
    onTertiary = Color(0xFF00210B),
    background = RoyalNavyDark,
    onBackground = RoyalTextLight,
    surface = RoyalVelvetSurface,
    onSurface = RoyalTextLight,
    surfaceVariant = RoyalCardSurface,
    onSurfaceVariant = RoyalTextMuted,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000)
)

@Composable
fun RoyalDiceLudoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RoyalLudoColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    RoyalDiceLudoTheme(content = content)
}
