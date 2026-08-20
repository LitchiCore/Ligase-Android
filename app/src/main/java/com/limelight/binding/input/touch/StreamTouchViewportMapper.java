package com.limelight.binding.input.touch;

/** Pure video-viewport coordinate mapping shared by direct touch and pointer modes. */
public final class StreamTouchViewportMapper {
    private StreamTouchViewportMapper() { }

    public static float[] normalized(
            float rawX,
            float rawY,
            float viewportX,
            float viewportY,
            float scaleX,
            float scaleY,
            int viewportWidth,
            int viewportHeight) {
        if (scaleX <= 0.0f || scaleY <= 0.0f || viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport must have positive scale and dimensions");
        }

        float localX = (rawX - viewportX) / scaleX;
        float localY = (rawY - viewportY) / scaleY;
        localX = Math.min(Math.max(localX, 0.0f), viewportWidth);
        localY = Math.min(Math.max(localY, 0.0f), viewportHeight);
        return new float[] { localX / viewportWidth, localY / viewportHeight };
    }
}
