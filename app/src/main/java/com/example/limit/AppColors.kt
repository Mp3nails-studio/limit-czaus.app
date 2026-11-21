package com.example.limit

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

/** Kolory aplikacji: dynamiczne (z tapety, Android 12+) albo wybrany kolor przewodni. */
object AppColors {

    data class Seed(val name: String, val hue: Float)

    val SEEDS = listOf(
        Seed("Fioletowy", 265f),
        Seed("Niebieski", 215f),
        Seed("Zielony", 140f),
        Seed("Pomarańczowy", 25f),
        Seed("Czerwony", 355f),
        Seed("Różowy", 325f)
    )

    const val DYNAMIC = "dynamic"

    val dynamicAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun seedChoice(index: Int) = "seed:$index"

    /** Czy wybór to kolory dynamiczne i czy da się je zastosować na tym telefonie. */
    fun useDynamic(choice: String) = choice == DYNAMIC && dynamicAvailable

    fun hueFor(choice: String): Float {
        val i = choice.removePrefix("seed:").toIntOrNull() ?: 0
        return SEEDS.getOrElse(i) { SEEDS[0] }.hue
    }

    /** Kolor do pokazania w kółku wyboru. */
    fun swatch(hue: Float): Int = hsl(hue, 0.62f, 0.48f)

    private fun hsl(h: Float, s: Float, l: Float): Int =
        ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, l))

    // Kolory błędu - takie same jak w standardowej palecie Material 3.
    private const val ERR_L = 0xFFBA1A1A.toInt()
    private const val ON_ERR_L = 0xFFFFFFFF.toInt()
    private const val ERR_CONT_L = 0xFFFFDAD6.toInt()
    private const val ON_ERR_CONT_L = 0xFF410002.toInt()
    private const val ERR_D = 0xFFFFB4AB.toInt()
    private const val ON_ERR_D = 0xFF690005.toInt()
    private const val ERR_CONT_D = 0xFF93000A.toInt()
    private const val ON_ERR_CONT_D = 0xFFFFDAD6.toInt()

    /** Paleta Material 3 z koloru przewodniego (odcień) - dla Compose. */
    fun scheme(hue: Float, dark: Boolean): ColorScheme {
        fun c(v: Int) = Color(v)
        val h2 = hue + 60f
        return if (!dark) lightColorScheme(
            primary = c(hsl(hue, 0.60f, 0.40f)),
            onPrimary = c(0xFFFFFFFF.toInt()),
            primaryContainer = c(hsl(hue, 0.75f, 0.89f)),
            onPrimaryContainer = c(hsl(hue, 0.80f, 0.12f)),
            secondary = c(hsl(hue, 0.25f, 0.40f)),
            onSecondary = c(0xFFFFFFFF.toInt()),
            secondaryContainer = c(hsl(hue, 0.40f, 0.89f)),
            onSecondaryContainer = c(hsl(hue, 0.40f, 0.12f)),
            tertiary = c(hsl(h2, 0.30f, 0.40f)),
            onTertiary = c(0xFFFFFFFF.toInt()),
            tertiaryContainer = c(hsl(h2, 0.45f, 0.89f)),
            onTertiaryContainer = c(hsl(h2, 0.45f, 0.12f)),
            background = c(hsl(hue, 0.30f, 0.98f)),
            onBackground = c(hsl(hue, 0.15f, 0.10f)),
            surface = c(hsl(hue, 0.30f, 0.98f)),
            onSurface = c(hsl(hue, 0.15f, 0.10f)),
            surfaceVariant = c(hsl(hue, 0.25f, 0.90f)),
            onSurfaceVariant = c(hsl(hue, 0.12f, 0.30f)),
            surfaceContainerLowest = c(0xFFFFFFFF.toInt()),
            surfaceContainerLow = c(hsl(hue, 0.30f, 0.96f)),
            surfaceContainer = c(hsl(hue, 0.28f, 0.94f)),
            surfaceContainerHigh = c(hsl(hue, 0.26f, 0.92f)),
            surfaceContainerHighest = c(hsl(hue, 0.25f, 0.90f)),
            outline = c(hsl(hue, 0.10f, 0.48f)),
            outlineVariant = c(hsl(hue, 0.20f, 0.80f)),
            error = c(ERR_L),
            onError = c(ON_ERR_L),
            errorContainer = c(ERR_CONT_L),
            onErrorContainer = c(ON_ERR_CONT_L)
        ) else darkColorScheme(
            primary = c(hsl(hue, 0.80f, 0.78f)),
            onPrimary = c(hsl(hue, 0.70f, 0.18f)),
            primaryContainer = c(hsl(hue, 0.55f, 0.27f)),
            onPrimaryContainer = c(hsl(hue, 0.80f, 0.90f)),
            secondary = c(hsl(hue, 0.35f, 0.76f)),
            onSecondary = c(hsl(hue, 0.35f, 0.18f)),
            secondaryContainer = c(hsl(hue, 0.25f, 0.27f)),
            onSecondaryContainer = c(hsl(hue, 0.40f, 0.89f)),
            tertiary = c(hsl(h2, 0.40f, 0.76f)),
            onTertiary = c(hsl(h2, 0.40f, 0.18f)),
            tertiaryContainer = c(hsl(h2, 0.30f, 0.27f)),
            onTertiaryContainer = c(hsl(h2, 0.45f, 0.89f)),
            background = c(hsl(hue, 0.15f, 0.08f)),
            onBackground = c(hsl(hue, 0.12f, 0.90f)),
            surface = c(hsl(hue, 0.15f, 0.08f)),
            onSurface = c(hsl(hue, 0.12f, 0.90f)),
            surfaceVariant = c(hsl(hue, 0.12f, 0.24f)),
            onSurfaceVariant = c(hsl(hue, 0.15f, 0.78f)),
            surfaceContainerLowest = c(hsl(hue, 0.15f, 0.05f)),
            surfaceContainerLow = c(hsl(hue, 0.15f, 0.10f)),
            surfaceContainer = c(hsl(hue, 0.15f, 0.12f)),
            surfaceContainerHigh = c(hsl(hue, 0.14f, 0.16f)),
            surfaceContainerHighest = c(hsl(hue, 0.13f, 0.20f)),
            outline = c(hsl(hue, 0.10f, 0.60f)),
            outlineVariant = c(hsl(hue, 0.12f, 0.30f)),
            error = c(ERR_D),
            onError = c(ON_ERR_D),
            errorContainer = c(ERR_CONT_D),
            onErrorContainer = c(ON_ERR_CONT_D)
        )
    }

    /** Kolory potrzebne ekranowi blokady (rysowanemu zwykłymi widokami, nie Compose). */
    data class Pal(
        val surface: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val surfaceVariant: Int,
        val primary: Int,
        val onPrimary: Int,
        val error: Int
    )

    fun palFromSeed(hue: Float, dark: Boolean): Pal =
        if (!dark) Pal(
            surface = hsl(hue, 0.30f, 0.98f),
            onSurface = hsl(hue, 0.15f, 0.10f),
            onSurfaceVariant = hsl(hue, 0.12f, 0.30f),
            surfaceVariant = hsl(hue, 0.25f, 0.90f),
            primary = hsl(hue, 0.60f, 0.40f),
            onPrimary = 0xFFFFFFFF.toInt(),
            error = ERR_L
        ) else Pal(
            surface = hsl(hue, 0.15f, 0.08f),
            onSurface = hsl(hue, 0.12f, 0.90f),
            onSurfaceVariant = hsl(hue, 0.15f, 0.78f),
            surfaceVariant = hsl(hue, 0.12f, 0.24f),
            primary = hsl(hue, 0.80f, 0.78f),
            onPrimary = hsl(hue, 0.70f, 0.18f),
            error = ERR_D
        )

    /** Kolory z motywu kontekstu (dla kolorów dynamicznych - kontekst owinięty DynamicColors). */
    fun palFromTheme(ctx: Context): Pal {
        fun col(attr: Int, fallback: Int) = MaterialColors.getColor(ctx, attr, fallback)
        return Pal(
            surface = col(com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF.toInt()),
            onSurface = col(com.google.android.material.R.attr.colorOnSurface, 0xFF000000.toInt()),
            onSurfaceVariant = col(
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF444444.toInt()
            ),
            surfaceVariant = col(
                com.google.android.material.R.attr.colorSurfaceVariant, 0xFFE0E0E0.toInt()
            ),
            primary = col(com.google.android.material.R.attr.colorPrimary, 0xFF6750A4.toInt()),
            onPrimary = col(com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF.toInt()),
            error = col(com.google.android.material.R.attr.colorError, ERR_L)
        )
    }
}
