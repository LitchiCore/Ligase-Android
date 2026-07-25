package com.limelight.ligase.feature.stream.infrastructure

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamBitratePreferencesTest {
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
    fun `legal current value remains custom without rewrite`() {
        sharedPreferences.edit()
            .putInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 15_500)
            .commit()
        val before = sharedPreferences.all.toMap()

        val stored = StreamBitratePreferences(sharedPreferences).readOrMigrate(20_000)

        assertEquals(15_500, stored.kbps)
        assertEquals(StreamBitrateSelectionSource.CUSTOM, stored.source)
        assertEquals(before, sharedPreferences.all)
    }

    @Test
    fun `legacy integer Mbps migrates exactly once as custom`() {
        sharedPreferences.edit()
            .putInt(StreamBitratePreferences.LEGACY_MBPS_KEY, 15)
            .commit()

        val stored = StreamBitratePreferences(sharedPreferences).readOrMigrate(20_000)

        assertEquals(15_000, stored.kbps)
        assertEquals(StreamBitrateSelectionSource.CUSTOM, stored.source)
        assertEquals(15_000, sharedPreferences.getInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 0))
        assertFalse(sharedPreferences.contains(StreamBitratePreferences.LEGACY_MBPS_KEY))
    }

    @Test
    fun `missing value adopts current default only once`() {
        val preferences = StreamBitratePreferences(sharedPreferences)
        assertEquals(20_000, preferences.readOrMigrate(20_000).kbps)
        assertEquals(20_000, preferences.readOrMigrate(40_000).kbps)
    }

    @Test
    fun `preset identity persists only when exact value matches`() {
        val preferences = StreamBitratePreferences(sharedPreferences)
        assertTrue(preferences.savePreset(StreamBitratePresetId.MBPS_40))
        val restored = preferences.readOrMigrate(20_000)
        assertEquals(StreamBitrateSelectionSource.PRESET, restored.source)
        assertEquals(StreamBitratePresetId.MBPS_40, restored.presetId)

        sharedPreferences.edit()
            .putInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 40_500)
            .commit()
        assertEquals(
            StreamBitrateSelectionSource.CUSTOM,
            preferences.readOrMigrate(20_000).source,
        )
    }

    @Test
    fun `removed preset marker becomes custom without changing bitrate`() {
        sharedPreferences.edit()
            .putInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 15_000)
            .putString("ligase_stream_bitrate_selection", "preset:MBPS_15")
            .commit()
        val before = sharedPreferences.all.toMap()

        val restored = StreamBitratePreferences(sharedPreferences).readOrMigrate(20_000)

        assertEquals(15_000, restored.kbps)
        assertEquals(StreamBitrateSelectionSource.CUSTOM, restored.source)
        assertEquals(before, sharedPreferences.all)
        assertFalse(
            StreamBitratePreferences(sharedPreferences)
                .savePreset(StreamBitratePresetId.MBPS_15),
        )
    }

    @Test
    fun `launch mapping validates and returns exact kbps`() {
        sharedPreferences.edit()
            .putInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 80_500)
            .commit()
        assertEquals(
            80_500,
            StreamBitratePreferences.readLaunchKbps(sharedPreferences, 20_000),
        )
    }

    @Test
    fun `default initialization never overwrites an explicit value`() {
        sharedPreferences.edit()
            .putInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 15_500)
            .commit()

        assertEquals(
            15_500,
            StreamBitratePreferences.initializeDefaultIfMissing(
                sharedPreferences,
                80_000,
            ),
        )
        assertEquals(
            15_500,
            sharedPreferences.getInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 0),
        )
    }

    @Test
    fun `legacy settings text writer shares strict fixed point validation`() {
        assertFalse(
            StreamBitratePreferences.saveCustomMbpsText(sharedPreferences, "15.2"),
        )
        assertFalse(sharedPreferences.contains(StreamBitratePreferences.CURRENT_KBPS_KEY))
        assertTrue(
            StreamBitratePreferences.saveCustomMbpsText(sharedPreferences, "15.5"),
        )
        assertEquals(
            15_500,
            sharedPreferences.getInt(StreamBitratePreferences.CURRENT_KBPS_KEY, 0),
        )
    }
}
