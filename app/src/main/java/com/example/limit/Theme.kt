package com.example.limit

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3. Wybór "dynamic" = kolory z tapety (Android 12+), w innym wypadku paleta
 * wygenerowana z wybranego koloru ("seed:N"). Jasny/ciemny według systemu.
 */
@Composable
fun LimitTheme(choice: String, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        AppColors.useDynamic(choice) ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> AppColors.scheme(AppColors.hueFor(choice), dark)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
