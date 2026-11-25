package app.hourstracker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentThemeTest {
    @Test
    fun everyAccentKeepsTextAndPrimaryActionsLegibleInBothThemes() {
        for (dark in listOf(false, true)) {
            for (accent in AccentColor.entries) {
                val colors = hourThreadColorScheme(dark, accent)
                val label = "${accent.key}, dark=$dark"
                for (background in listOf(colors.background, colors.surface, colors.surfaceContainer, colors.surfaceVariant)) {
                    assertContrast(label, colors.primary, background)
                }
                assertContrast(label, colors.onPrimary, colors.primary)
                assertContrast(label, colors.onPrimaryContainer, colors.primaryContainer)
                assertContrast(label, Color.White, colors.secondary)
                assertContrast(label, Color.White, colors.tertiary)
            }
        }
    }

    private fun assertContrast(label: String, foreground: Color, background: Color) {
        val first = foreground.luminance() + .05f
        val second = background.luminance() + .05f
        val ratio = maxOf(first, second) / minOf(first, second)
        assertTrue("$label: contrast ratio $ratio for $foreground on $background", ratio >= 4.5f)
    }
}
