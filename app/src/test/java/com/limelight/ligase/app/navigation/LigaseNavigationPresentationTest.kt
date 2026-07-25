package com.limelight.ligase.app.navigation

import android.content.res.Configuration
import com.limelight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
