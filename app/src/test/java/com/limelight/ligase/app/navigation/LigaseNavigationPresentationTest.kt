package com.limelight.ligase.app.navigation

import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import com.limelight.R
import com.limelight.ligase.LigaseDarkSemanticColors
import com.limelight.ligase.LigaseLightSemanticColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class LigaseNavigationPresentationTest {
    @Test
    fun `phone portrait uses bottom navigation`() {
        assertEquals(
            LigaseNavigationPlacement.BOTTOM,
            ligaseNavigationPlacement(360, Configuration.ORIENTATION_PORTRAIT),
        )
    }

    @Test
    fun `phone landscape uses side navigation even with compact height`() {
        assertEquals(
            LigaseNavigationPlacement.SIDE,
            ligaseNavigationPlacement(771, Configuration.ORIENTATION_LANDSCAPE),
        )
    }

    @Test
    fun `wide tablet portrait retains side navigation`() {
        assertEquals(
            LigaseNavigationPlacement.SIDE,
            ligaseNavigationPlacement(800, Configuration.ORIENTATION_PORTRAIT),
        )
    }

    @Test
    fun `input destination follows operation gate while other pages remain available`() {
        assertFalse(
            ligaseNavigationDestinationEnabled(
                LigasePage.INPUT,
                inputEnabled = false,
            ),
        )
        assertTrue(
            ligaseNavigationDestinationEnabled(
                LigasePage.HOME,
                inputEnabled = false,
            ),
        )
        assertTrue(
            ligaseNavigationDestinationEnabled(
                LigasePage.SETTINGS,
                inputEnabled = false,
            ),
        )
    }

    @Test
    fun `home sidebar uses product library title and other pages use labels`() {
        assertEquals(R.string.ligase_library_title, ligaseNavigationHeader(LigasePage.HOME))
        assertEquals(LigasePage.INPUT.label, ligaseNavigationHeader(LigasePage.INPUT))
        assertEquals(LigasePage.SETTINGS.label, ligaseNavigationHeader(LigasePage.SETTINGS))
    }

    @Test
    fun `focus ring is a visible non-color affordance only for enabled focused items`() {
        assertEquals(
            LigaseNavigationFocusPresentation(showRing = true, ringWidthDp = 3),
            ligaseNavigationFocusPresentation(focused = true, enabled = true),
        )
        assertFalse(ligaseNavigationFocusPresentation(focused = false, enabled = true).showRing)
        assertFalse(ligaseNavigationFocusPresentation(focused = true, enabled = false).showRing)
    }

    @Test
    fun `focus token clears navigation surface and background contrast gate`() {
        listOf(LigaseLightSemanticColors, LigaseDarkSemanticColors).forEach { colors ->
            assertTrue(contrastRatio(colors.focus, colors.background) >= 3.0)
            assertTrue(contrastRatio(colors.focus, colors.surface) >= 3.0)
        }
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = max(relativeLuminance(first), relativeLuminance(second))
        val darker = min(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
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
