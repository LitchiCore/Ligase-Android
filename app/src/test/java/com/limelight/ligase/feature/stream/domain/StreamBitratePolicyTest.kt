package com.limelight.ligase.feature.stream.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBitratePolicyTest {
    @Test
    fun `custom parser uses exact half Mbps fixed point`() {
        assertEquals(
            CustomBitrateParseResult.Valid(500),
            StreamBitratePolicy.parseCustomMbps("0.5"),
        )
        assertEquals(
            CustomBitrateParseResult.Valid(15_500),
            StreamBitratePolicy.parseCustomMbps(" 15.5 "),
        )
        assertEquals(
            CustomBitrateParseResult.Valid(300_000),
            StreamBitratePolicy.parseCustomMbps("300"),
        )
        assertEquals("15.5", StreamBitratePolicy.formatMbps(15_500))
        assertEquals("15", StreamBitratePolicy.formatMbps(15_000))
    }

    @Test
    fun `custom parser rejects blank fractions signs exponent and overflow`() {
        assertEquals(CustomBitrateParseResult.Empty, StreamBitratePolicy.parseCustomMbps(" "))
        listOf("15.0", "15.50", "15.25", "+15", "-1", "1e2", "01", "NaN").forEach {
            assertEquals(
                it,
                CustomBitrateParseResult.NotCanonicalNumber,
                StreamBitratePolicy.parseCustomMbps(it),
            )
        }
        listOf("0", "300.5", "301", "999").forEach {
            assertEquals(
                it,
                CustomBitrateParseResult.OutOfRange,
                StreamBitratePolicy.parseCustomMbps(it),
            )
        }
    }

    @Test
    fun `recommendation is stable and lower preset wins ties`() {
        assertEquals(
            StreamBitratePresetId.MBPS_10,
            StreamBitratePolicy.recommendedPreset(12_500).id,
        )
        assertEquals(
            StreamBitratePresetId.MBPS_300,
            StreamBitratePolicy.recommendedPreset(Int.MAX_VALUE).id,
        )
        assertTrue(StreamBitratePolicy.presets.zipWithNext().all { (a, b) -> a.kbps < b.kbps })
    }
}
