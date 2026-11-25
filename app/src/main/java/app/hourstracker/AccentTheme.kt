package app.hourstracker

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal const val DEFAULT_ACCENT_KEY = "default"

internal enum class AccentColor(
    val key: String,
    @param:StringRes @get:StringRes val label: Int,
    private val lightInk: Color,
    private val darkInk: Color,
) {
    Default(DEFAULT_ACCENT_KEY, R.string.accent_default, Color(0xFF006F76), Color(0xFFB9B2FF)),
    Teal("teal", R.string.accent_teal, Color(0xFF006F76), Color(0xFF69DDD5)),
    Blue("blue", R.string.accent_blue, Color(0xFF145AB1), Color(0xFF8CBFFF)),
    Violet("violet", R.string.accent_violet, Color(0xFF613EB0), Color(0xFFC5ABFF)),
    Rose("rose", R.string.accent_rose, Color(0xFFA22962), Color(0xFFFFA3C9)),
    Green("green", R.string.accent_green, Color(0xFF386B33), Color(0xFFA7D992)),
    Amber("amber", R.string.accent_amber, Color(0xFF80520A), Color(0xFFFFD18D)),
    Slate("slate", R.string.accent_slate, Color(0xFF455670), Color(0xFFB8C6DD));

    fun swatch(dark: Boolean): Color = if (dark) darkInk else lightInk

    fun chartColor(dark: Boolean): Color = when {
        dark -> darkInk
        this == Default -> Color(0xFF17BEA8)
        else -> lerp(darkInk, lightInk, .25f)
    }

    fun applyTo(base: ColorScheme, dark: Boolean): ColorScheme {
        if (this == Default) return base
        val ink = swatch(dark)
        val container = lerp(base.surface, ink, if (dark) .18f else .10f)
        return base.copy(
            primary = ink,
            onPrimary = if (dark) Color.Black else Color.White,
            primaryContainer = container,
            onPrimaryContainer = ink,
            secondary = lerp(lightInk, Color.Black, .18f),
            onSecondary = Color.White,
            secondaryContainer = container,
            onSecondaryContainer = ink,
            tertiary = lightInk,
            onTertiary = Color.White,
            tertiaryContainer = container,
            onTertiaryContainer = ink,
            inversePrimary = swatch(!dark),
            surfaceTint = ink,
        )
    }

    companion object {
        fun fromKey(key: String?): AccentColor? = entries.firstOrNull { it.key == key }
    }
}

internal val LocalChartAccent = staticCompositionLocalOf { AccentColor.Default.chartColor(dark = false) }

internal fun hourThreadColorScheme(dark: Boolean, accent: AccentColor): ColorScheme =
    accent.applyTo(if (dark) darkColors else lightColors, dark)

private val lightColors = lightColorScheme(
    primary = Color(0xFF006F76),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF6F4),
    onPrimaryContainer = Color(0xFF005B62),
    secondary = Color(0xFF0863BC),
    secondaryContainer = Color(0xFFE8EFFD),
    onSecondaryContainer = Color(0xFF233B62),
    tertiary = Color(0xFF008082),
    background = Color(0xFFF4F8FA),
    onBackground = Color(0xFF111D39),
    surface = Color.White,
    onSurface = Color(0xFF111D39),
    surfaceContainer = Color(0xFFEEF4F7),
    surfaceVariant = Color(0xFFEAF0F4),
    onSurfaceVariant = Color(0xFF4C5A71),
    outline = Color(0xFF738398),
    outlineVariant = Color(0xFFDCE7EE),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFB9B2FF),
    onPrimary = Color(0xFF262584),
    primaryContainer = Color(0xFF27204E),
    onPrimaryContainer = Color(0xFFE3E1FF),
    background = Color.Black,
    onBackground = Color(0xFFE8EAF2),
    secondary = Color(0xFF6832BC),
    secondaryContainer = Color(0xFF242544),
    onSecondaryContainer = Color(0xFFE5E1FF),
    tertiary = Color(0xFF5145D5),
    surface = Color(0xFF111117),
    onSurface = Color(0xFFE8EAF2),
    surfaceContainer = Color(0xFF1B1A25),
    surfaceVariant = Color(0xFF201F2A),
    onSurfaceVariant = Color(0xFFACB1C7),
    outline = Color(0xFF838AA4),
    outlineVariant = Color(0xFF2A2A37),
)

