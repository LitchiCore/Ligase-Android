package com.limelight.ligase.feature.stream.application

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId
import com.limelight.ligase.feature.stream.infrastructure.StreamBitratePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamBitrateStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Before
    @After
    fun clear() {
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun `legacy half step is displayed and resaved without loss`() {
        sharedPreferences.edit()
            .putInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 15_500)
            .commit()
        val state = StreamBitrateState(StreamBitratePreferences(sharedPreferences), 20_000)

        assertEquals("15.5", state.state.currentDisplayMbps)
        assertNull(state.state.selectedPreset)
        assertTrue(state.setStreamBitrateKbps(15_500) is StreamBitrateSaveResult.Success)
        assertEquals(15_500, state.state.currentKbps)
    }

    @Test
    fun `recommendation change never changes explicit value`() {
        val state = StreamBitrateState(StreamBitratePreferences(sharedPreferences), 10_000)
        state.setStreamBitrateKbps(15_500)

        state.updateRecommendation(80_000)

        assertEquals(15_500, state.state.currentKbps)
        assertEquals(StreamBitratePresetId.MBPS_80, state.state.recommendedPreset.id)
        assertNull(state.state.selectedPreset)
    }

    @Test
    fun `preset and invalid save results are typed`() {
        val state = StreamBitrateState(StreamBitratePreferences(sharedPreferences), 10_000)
        assertTrue(
            state.setStreamBitratePreset(StreamBitratePresetId.MBPS_40) is
                StreamBitrateSaveResult.Success,
        )
        assertEquals(StreamBitratePresetId.MBPS_40, state.state.selectedPreset?.id)

        assertEquals(
            StreamBitrateSaveResult.Failed(StreamBitrateSaveError.OUT_OF_RANGE),
            state.setStreamBitrateKbps(40_001),
        )
        assertEquals(40_000, state.state.currentKbps)
    }

    @Test
    fun `custom submission maps parser failures without writing`() {
        val state = StreamBitrateState(StreamBitratePreferences(sharedPreferences), 10_000)
        assertEquals(
            StreamBitrateSaveResult.Failed(StreamBitrateSaveError.NOT_CANONICAL_NUMBER),
            state.submitCustomMbps("15.50"),
        )
        assertEquals(10_000, state.state.currentKbps)
        assertTrue(state.submitCustomMbps("15.5") is StreamBitrateSaveResult.Success)
        assertEquals(15_500, state.state.currentKbps)
    }

    @Test
    fun `state string is limited to bitrate projection`() {
        val state = StreamBitrateState(StreamBitratePreferences(sharedPreferences), 20_000)
        val rendered = state.state.toString()
        assertTrue(rendered.contains("currentKbps=20000"))
        assertTrue(!rendered.contains("SharedPreferences"))
        assertTrue(!rendered.contains("token"))
    }
}
