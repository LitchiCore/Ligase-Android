package com.limelight.ligase.feature.stream.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDisplayPolicyTest {
    @Test
    fun `phone capabilities expose stable resolution order and explicit unsupported reasons`() {
        val choices = StreamDisplayPolicy.resolutionPresets(
            DeviceStreamCapabilities(2400, 1080, 120f, true),
        )

        assertEquals(StreamResolutionPresetId.DEVICE_MAX, choices[0].id)
        assertEquals(2400, choices[0].resolution?.width)
        assertEquals(StreamResolutionPresetId.BEST_16_9, choices[1].id)
        assertEquals(1920, choices[1].resolution?.width)
        assertEquals(StreamCapabilityReason.EXCEEDS_DEVICE_DISPLAY, choices.last().reason)
    }

    @Test
    fun `frame rate recommendation is highest fixed supported and never changes selection`() {
        val choices = StreamDisplayPolicy.frameRateChoices(
            DeviceStreamCapabilities(1920, 1080, 89.5f, true),
        )

        assertTrue(choices.single { it.mode == StreamFrameRateMode.FPS_90 }.recommended)
        assertFalse(choices.single { it.mode == StreamFrameRateMode.FPS_120 }.recommended)
        assertEquals(
            StreamCapabilityReason.EXCEEDS_DEVICE_DISPLAY,
            choices.single { it.mode == StreamFrameRateMode.FPS_120 }.reason,
        )
    }

    @Test
    fun `follow display preserves fractional refresh and fixed mode stays exact`() {
        val capabilities = DeviceStreamCapabilities(2560, 1440, 59.94f, true)
        assertEquals(59.94f, StreamDisplayPolicy.launchFps(StreamFrameRateMode.FOLLOW_DISPLAY, capabilities))
        assertEquals(30f, StreamDisplayPolicy.launchFps(StreamFrameRateMode.FPS_30, capabilities))
    }

    @Test
    fun `unknown capability never pretends fixed presets are supported`() {
        val capabilities = DeviceStreamCapabilities(1920, 1080, 60f, false)
        assertTrue(
            StreamDisplayPolicy.resolutionPresets(capabilities)
                .all { it.reason == StreamCapabilityReason.DEVICE_CAPABILITY_UNKNOWN },
        )
    }
}
