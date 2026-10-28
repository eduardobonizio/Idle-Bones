/*
 * Project Idle RPG - Idle Bones
 * Tema escuro medieval (dourado / ferro envelhecido).
 */
package com.idlebones.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta central do jogo. */
object GameColors {
    val Background = Color(0xFF121212)       // Fundo geral
    val Surface = Color(0xFF1E1E1E)          // Cards / painéis
    val SurfaceVariant = Color(0xFF2A2A2A)   // Elementos elevados
    val Gold = Color(0xFFD4AF37)             // Dourado principal
    val GoldDim = Color(0xFF9C7C29)          // Dourado envelhecido
    val Iron = Color(0xFF8A8A8A)             // Ferro envelhecido
    val IronDark = Color(0xFF3A3A3A)
    val TextPrimary = Color(0xFFEDE6D6)      // Pergaminho claro
    val TextSecondary = Color(0xFFA9A391)
    val HealthRed = Color(0xFFB03A2E)
    val ManaBlue = Color(0xFF2E6FB0)
    val XpGreen = Color(0xFF4CAF50)
    val CritOrange = Color(0xFFFF9800)
}

private val GameDarkScheme = darkColorScheme(
    primary = GameColors.Gold,
    onPrimary = Color(0xFF1A1A1A),
    secondary = GameColors.Iron,
    onSecondary = Color(0xFF121212),
    background = GameColors.Background,
    onBackground = GameColors.TextPrimary,
    surface = GameColors.Surface,
    onSurface = GameColors.TextPrimary,
    surfaceVariant = GameColors.SurfaceVariant,
    onSurfaceVariant = GameColors.TextSecondary,
    error = GameColors.HealthRed
)

private val GameTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun IdleBonesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Sempre escuro: o jogo não tem modo claro.
    MaterialTheme(
        colorScheme = GameDarkScheme,
        typography = GameTypography,
        content = content
    )
}
