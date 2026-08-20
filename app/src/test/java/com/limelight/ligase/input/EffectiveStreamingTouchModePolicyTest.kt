package com.limelight.ligase.input

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveStreamingTouchModePolicyTest {
    @Test
    fun projectsEachEffectiveTransportWithoutExposingPreferenceValues() {
        assertEquals(
            EffectiveStreamingTouchMode.DIRECT_TOUCH,
            EffectiveStreamingTouchModePolicy.resolve(true, false),
        )
        assertEquals(
            EffectiveStreamingTouchMode.ABSOLUTE_POINTER,
            EffectiveStreamingTouchModePolicy.resolve(false, false),
        )
        assertEquals(
            EffectiveStreamingTouchMode.TRACKPAD,
            EffectiveStreamingTouchModePolicy.resolve(false, true),
        )
        assertEquals(
            EffectiveStreamingTouchMode.TRACKPAD,
            EffectiveStreamingTouchModePolicy.resolve(true, true),
        )
    }
}
