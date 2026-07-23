package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertArrayEquals;

import android.view.Surface;

import org.junit.Test;

public class TouchKitGyroMouseButtonTest {
    @Test
    public void mapsNaturalSensorAxesToEveryDisplayRotation() {
        assertArrayEquals(new float[]{3f, 2f},
                TouchKitGyroMouseButton.mapRatesForRotation(2f, 3f,
                        Surface.ROTATION_0), 0f);
        assertArrayEquals(new float[]{-2f, 3f},
                TouchKitGyroMouseButton.mapRatesForRotation(2f, 3f,
                        Surface.ROTATION_90), 0f);
        assertArrayEquals(new float[]{-3f, -2f},
                TouchKitGyroMouseButton.mapRatesForRotation(2f, 3f,
                        Surface.ROTATION_180), 0f);
        assertArrayEquals(new float[]{2f, -3f},
                TouchKitGyroMouseButton.mapRatesForRotation(2f, 3f,
                        Surface.ROTATION_270), 0f);
    }
}
