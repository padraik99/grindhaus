package com.padraik99.grindhaus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette from the mockups. */
object GrindhausColors {
    val Roast = Color(0xFF17130F)      // screen background
    val Surface = Color(0xFF241E19)    // cards
    val SurfaceHigh = Color(0xFF2E2620)
    val Line = Color(0xFF3A312A)
    val Outline = Color(0xFF4A3F37)
    val Foam = Color(0xFFF2EBE3)       // primary text
    val FoamDim = Color(0xFFD8CEC3)
    val Muted = Color(0xFFA89C90)      // secondary text, 6.8:1 on Roast
    val Crema = Color(0xFFE9A23B)      // accent
    val Peak = Color(0xFF8CC7A1)
    val Sour = Color(0xFFE9C46A)
    val Balanced = Color(0xFF7FB7A4)
    val Bitter = Color(0xFFD98A6E)
}

private val scheme = darkColorScheme(
    primary = GrindhausColors.Crema,
    onPrimary = GrindhausColors.Roast,
    background = GrindhausColors.Roast,
    onBackground = GrindhausColors.Foam,
    surface = GrindhausColors.Roast,
    onSurface = GrindhausColors.Foam,
    surfaceContainer = GrindhausColors.Surface,
    surfaceVariant = GrindhausColors.Surface,
    onSurfaceVariant = GrindhausColors.Muted,
    outline = GrindhausColors.Outline,
    secondaryContainer = GrindhausColors.SurfaceHigh,
    onSecondaryContainer = GrindhausColors.Crema,
)

@Composable
fun GrindhausTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
