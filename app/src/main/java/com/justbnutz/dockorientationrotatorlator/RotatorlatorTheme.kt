/*
 * Overhaul Phase 3, 2026-07: Material 3 theme.
 */

package com.justbnutz.dockorientationrotatorlator

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material You (Decision 3): minSdk 31 == the dynamic-colour floor, so the wallpaper
 * palette is always available — no static fallback scheme needed. (The legacy teal
 * #009688 lives on only as the splash-screen background colour.)
 */
@Composable
fun RotatorlatorTheme(content: @Composable () -> Unit) {

    val context = LocalContext.current

    val colorScheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
