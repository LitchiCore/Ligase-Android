package com.limelight.binding.input.touch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrackpadContextTest {
    @Test
    public void heldMouseButton_suppressesMatchingTrackpadTap() {
        assertTrue(TrackpadContext.shouldSuppressButtonTap(true));
        assertFalse(TrackpadContext.shouldSuppressButtonTap(false));
    }

    @Test
    public void linearMode_hasConstantPointerResponse() {
        assertEquals(1.0, TrackpadContext.pointerResponseMultiplier(
                2, 0, false), 0.0001);
        assertEquals(1.0, TrackpadContext.pointerResponseMultiplier(
                80, 0, false), 0.0001);
        assertTrue(TrackpadContext.pointerResponseMultiplier(
                80, 0, true) > 1.0);
    }

    @Test
    public void heldPointer_discontinuityIsRejected() {
        assertFalse(TrackpadContext.isDiscontinuousPointerDelta(120, -180));
        assertTrue(TrackpadContext.isDiscontinuousPointerDelta(0, -700));
    }
}
