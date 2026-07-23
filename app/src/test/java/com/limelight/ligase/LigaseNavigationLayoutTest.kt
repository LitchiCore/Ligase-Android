package com.limelight.ligase

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class LigaseNavigationLayoutTest {
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
}
