package com.hillmanapps.wordclock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun WordClockTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        content = content,
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = WordClockBlue,
                secondary = Color.White,
                background = Color.Black
            )
        } else {
            lightColorScheme(
                primary = WordClockBlue,
                secondary = Color.Black,
                background = Color.White
            )
        }
    )
}