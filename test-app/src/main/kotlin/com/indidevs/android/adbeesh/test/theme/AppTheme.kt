package com.indidevs.android.adbeesh.test.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFBF00)
val Black = Color(0xFF000000)

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Black,
    background = Black,
    onBackground = Amber,
    surface = Black,
    onSurface = Amber,
    secondary = Amber
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
