package com.limelight.ligase

import android.content.Context
import android.util.TypedValue
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.R as AppCompatR
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.R as MaterialR
import com.limelight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
class LigaseThemeTest {
    @Test
    fun lightColorSchemeMapsEveryMaterial3SlotDeterministically() {
        assertScheme(LigaseLightColorScheme, LigaseLightSemanticColors, dark = false)
    }

    @Test
    fun darkColorSchemeMapsEveryMaterial3SlotDeterministically() {
        assertScheme(LigaseDarkColorScheme, LigaseDarkSemanticColors, dark = true)
    }

    @Test
    fun reviewedTextAndStatePairsMeetContrastContract() {
        listOf(LigaseLightSemanticColors, LigaseDarkSemanticColors).forEach { colors ->
            assertContrastAtLeast(colors.textPrimary, colors.surface, 4.5)
            assertContrastAtLeast(colors.textSecondary, colors.surface, 4.5)
            assertContrastAtLeast(colors.brandPrimary, colors.surface, 4.5)
            assertContrastAtLeast(colors.brandSecondary, colors.surface, 4.5)
            assertContrastAtLeast(colors.success, colors.surface, 4.5)
            assertContrastAtLeast(colors.warning, colors.surface, 4.5)
            assertContrastAtLeast(colors.errorDanger, colors.surface, 4.5)
            assertContrastAtLeast(colors.disabled, colors.surface, 4.5)
            assertContrastAtLeast(colors.textPrimary, colors.selected, 4.5)
            assertContrastAtLeast(colors.border, colors.surface, 3.0)
            assertContrastAtLeast(colors.focus, colors.background, 3.0)
        }
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightXmlResourcesMatchComposeTokenAuthority() {
        assertXmlTokens(LigaseLightSemanticColors, dark = false)
    }

    @Test
    @Config(qualifiers = "night")
    fun darkXmlResourcesMatchComposeTokenAuthority() {
        assertXmlTokens(LigaseDarkSemanticColors, dark = true)
    }

    private fun assertScheme(
        scheme: ColorScheme,
        colors: LigaseSemanticColors,
        dark: Boolean,
    ) {
        assertEquals(colors.brandPrimary, scheme.primary)
        assertEquals(colors.surface, scheme.onPrimary)
        assertEquals(colors.selected, scheme.primaryContainer)
        assertEquals(colors.textPrimary, scheme.onPrimaryContainer)
        assertEquals(colors.brandPrimary, scheme.inversePrimary)
        assertEquals(colors.brandSecondary, scheme.secondary)
        assertEquals(colors.surface, scheme.onSecondary)
        assertEquals(colors.surfaceVariant, scheme.secondaryContainer)
        assertEquals(colors.textPrimary, scheme.onSecondaryContainer)
        assertEquals(colors.brandSecondary, scheme.tertiary)
        assertEquals(colors.surface, scheme.onTertiary)
        assertEquals(colors.surfaceVariant, scheme.tertiaryContainer)
        assertEquals(colors.textPrimary, scheme.onTertiaryContainer)
        assertEquals(colors.background, scheme.background)
        assertEquals(colors.textPrimary, scheme.onBackground)
        assertEquals(colors.surface, scheme.surface)
        assertEquals(colors.textPrimary, scheme.onSurface)
        assertEquals(colors.surfaceVariant, scheme.surfaceVariant)
        assertEquals(colors.textSecondary, scheme.onSurfaceVariant)
        assertEquals(colors.brandPrimary, scheme.surfaceTint)
        assertEquals(colors.textPrimary, scheme.inverseSurface)
        assertEquals(colors.surface, scheme.inverseOnSurface)
        assertEquals(colors.errorDanger, scheme.error)
        assertEquals(colors.surface, scheme.onError)
        assertEquals(colors.surfaceVariant, scheme.errorContainer)
        assertEquals(colors.errorDanger, scheme.onErrorContainer)
        assertEquals(colors.border, scheme.outline)
        assertEquals(colors.border, scheme.outlineVariant)
        assertEquals(if (dark) colors.background else colors.textPrimary, scheme.scrim)
        assertEquals(colors.surface, scheme.surfaceBright)
        assertEquals(colors.background, scheme.surfaceDim)
        assertEquals(colors.surface, scheme.surfaceContainerLowest)
        assertEquals(colors.surface, scheme.surfaceContainerLow)
        assertEquals(colors.surfaceVariant, scheme.surfaceContainer)
        assertEquals(colors.surfaceVariant, scheme.surfaceContainerHigh)
        assertEquals(colors.surfaceVariant, scheme.surfaceContainerHighest)
        assertEquals(colors.brandPrimary, scheme.primaryFixed)
        assertEquals(colors.brandPrimary, scheme.primaryFixedDim)
        assertEquals(colors.surface, scheme.onPrimaryFixed)
        assertEquals(colors.surface, scheme.onPrimaryFixedVariant)
        assertEquals(colors.brandSecondary, scheme.secondaryFixed)
        assertEquals(colors.brandSecondary, scheme.secondaryFixedDim)
        assertEquals(colors.surface, scheme.onSecondaryFixed)
        assertEquals(colors.surface, scheme.onSecondaryFixedVariant)
        assertEquals(colors.brandSecondary, scheme.tertiaryFixed)
        assertEquals(colors.brandSecondary, scheme.tertiaryFixedDim)
        assertEquals(colors.surface, scheme.onTertiaryFixed)
        assertEquals(colors.surface, scheme.onTertiaryFixedVariant)
        assertEquals(
            if (dark) colors.background else colors.textPrimary,
            ligaseShadowOverlay(colors, dark),
        )
    }

    private fun assertXmlTokens(colors: LigaseSemanticColors, dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = mapOf(
            R.color.ligase_brand_primary to colors.brandPrimary,
            R.color.ligase_brand_secondary to colors.brandSecondary,
            R.color.ligase_background to colors.background,
            R.color.ligase_surface to colors.surface,
            R.color.ligase_surface_variant to colors.surfaceVariant,
            R.color.ligase_text_primary to colors.textPrimary,
            R.color.ligase_text_secondary to colors.textSecondary,
            R.color.ligase_border to colors.border,
            R.color.ligase_selected to colors.selected,
            R.color.ligase_success to colors.success,
            R.color.ligase_warning to colors.warning,
            R.color.ligase_error_danger to colors.errorDanger,
            R.color.ligase_disabled to colors.disabled,
            R.color.ligase_focus to colors.focus,
            R.color.ligase_scrim to if (dark) colors.background else colors.textPrimary,
            R.color.ligase_shadow to ligaseShadowOverlay(colors, dark),
        )
        expected.forEach { (resourceId, color) ->
            assertEquals(color.toArgb(), ContextCompat.getColor(context, resourceId))
        }

        val themedContext = ContextThemeWrapper(context, R.style.LigaseTheme)
        assertThemeColor(themedContext, AppCompatR.attr.colorPrimary, colors.brandPrimary)
        assertThemeColor(themedContext, MaterialR.attr.colorOnPrimary, colors.surface)
        assertThemeColor(themedContext, MaterialR.attr.colorPrimaryContainer, colors.selected)
        assertThemeColor(themedContext, MaterialR.attr.colorPrimaryFixed, colors.brandPrimary)
        assertThemeColor(themedContext, MaterialR.attr.colorOnPrimaryFixed, colors.surface)
        assertThemeColor(themedContext, MaterialR.attr.colorSecondary, colors.brandSecondary)
        assertThemeColor(themedContext, MaterialR.attr.colorSecondaryFixed, colors.brandSecondary)
        assertThemeColor(themedContext, MaterialR.attr.colorTertiary, colors.brandSecondary)
        assertThemeColor(themedContext, MaterialR.attr.colorSurface, colors.surface)
        assertThemeColor(themedContext, MaterialR.attr.colorSurfaceVariant, colors.surfaceVariant)
        assertThemeColor(
            themedContext,
            MaterialR.attr.colorSurfaceContainerHighest,
            colors.surfaceVariant,
        )
        assertThemeColor(themedContext, MaterialR.attr.colorSurfaceDim, colors.background)
        assertThemeColor(themedContext, MaterialR.attr.colorSurfaceInverse, colors.textPrimary)
        assertThemeColor(themedContext, MaterialR.attr.colorOutline, colors.border)
        assertThemeColor(themedContext, AppCompatR.attr.colorError, colors.errorDanger)
        assertThemeColor(themedContext, MaterialR.attr.colorOnError, colors.surface)
    }

    private fun assertThemeColor(context: Context, attribute: Int, expected: Color) {
        val value = TypedValue()
        assertTrue("Theme attribute $attribute was not resolved", context.theme.resolveAttribute(
            attribute,
            value,
            true,
        ))
        val actual = if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else {
            value.data
        }
        assertEquals(expected.toArgb(), actual)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("Expected contrast >= $minimum but was $ratio", ratio >= minimum)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linear(component: Float): Double {
            val value = component.toDouble()
            return if (value <= 0.04045) {
                value / 12.92
            } else {
                ((value + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
