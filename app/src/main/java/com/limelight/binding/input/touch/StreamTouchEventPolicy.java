package com.limelight.binding.input.touch;

import android.view.MotionEvent;

import com.limelight.nvstream.jni.MoonBridge;

/** Closed mapping from Android pointer lifecycle edges to the Host touch transport. */
public final class StreamTouchEventPolicy {
    private StreamTouchEventPolicy() { }

    public static byte eventType(int actionMasked, boolean pointerCancelled) {
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                return MoonBridge.LI_TOUCH_EVENT_DOWN;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                return pointerCancelled
                        ? MoonBridge.LI_TOUCH_EVENT_CANCEL
                        : MoonBridge.LI_TOUCH_EVENT_UP;
            case MotionEvent.ACTION_MOVE:
                return MoonBridge.LI_TOUCH_EVENT_MOVE;
            case MotionEvent.ACTION_CANCEL:
                return MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL;
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                return MoonBridge.LI_TOUCH_EVENT_HOVER;
            case MotionEvent.ACTION_HOVER_EXIT:
                return MoonBridge.LI_TOUCH_EVENT_HOVER_LEAVE;
            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return MoonBridge.LI_TOUCH_EVENT_BUTTON_ONLY;
            default:
                return -1;
        }
    }
}
