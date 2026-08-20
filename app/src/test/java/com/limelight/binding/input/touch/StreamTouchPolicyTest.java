package com.limelight.binding.input.touch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;

import com.limelight.nvstream.jni.MoonBridge;

import org.junit.Test;

public class StreamTouchPolicyTest {
    @Test
    public void directTouch_preservesTapDragCancelAndMultiPointerEdges() {
        assertEquals(MoonBridge.LI_TOUCH_EVENT_DOWN,
                StreamTouchEventPolicy.eventType(MotionEvent.ACTION_DOWN, false));
        assertEquals(MoonBridge.LI_TOUCH_EVENT_UP,
                StreamTouchEventPolicy.eventType(MotionEvent.ACTION_UP, false));
        assertEquals(MoonBridge.LI_TOUCH_EVENT_MOVE,
                StreamTouchEventPolicy.eventType(MotionEvent.ACTION_MOVE, false));
        assertEquals(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL,
                StreamTouchEventPolicy.eventType(MotionEvent.ACTION_CANCEL, false));
        assertEquals(MoonBridge.LI_TOUCH_EVENT_DOWN,
                StreamTouchEventPolicy.eventType(MotionEvent.ACTION_POINTER_DOWN, false));
        assertEquals(MoonBridge.LI_TOUCH_EVENT_UP,
                StreamTouchEventPolicy.eventType(MotionEvent.ACTION_POINTER_UP, false));
        assertEquals(MoonBridge.LI_TOUCH_EVENT_CANCEL,
                StreamTouchEventPolicy.eventType(MotionEvent.ACTION_POINTER_UP, true));
    }

    @Test
    public void viewport_clampsBlackBarsAndScalesAcrossLandscapeAndRotation() {
        assertArrayEquals(new float[] { 0.0f, 0.5f },
                StreamTouchViewportMapper.normalized(80, 600, 100, 0, 1, 1, 1000, 1200),
                0.0001f);
        assertArrayEquals(new float[] { 0.5f, 0.5f },
                StreamTouchViewportMapper.normalized(1100, 600, 100, 0, 2, 1, 1000, 1200),
                0.0001f);
        assertArrayEquals(new float[] { 0.5f, 0.5f },
                StreamTouchViewportMapper.normalized(600, 1100, 0, 100, 1, 2, 1200, 1000),
                0.0001f);
        assertArrayEquals(new float[] { 1.0f, 1.0f },
                StreamTouchViewportMapper.normalized(4000, 4000, 0, 0, 1, 1, 1920, 1080),
                0.0001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void viewport_rejectsUnmeasuredTarget() {
        StreamTouchViewportMapper.normalized(1, 1, 0, 0, 1, 1, 0, 1080);
    }
}
