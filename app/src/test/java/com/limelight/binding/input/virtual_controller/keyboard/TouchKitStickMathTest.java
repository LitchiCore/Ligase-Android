package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TouchKitStickMathTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void upperDiagonalsKeepUpDirection() {
        double radius = Math.hypot(40, 40);

        assertTrue(TouchKitStickMath.outputY(-40, -40, radius) > 0);
        assertTrue(TouchKitStickMath.outputY(40, -40, radius) > 0);
        assertTrue(TouchKitStickMath.outputX(-40, -40, radius) < 0);
        assertTrue(TouchKitStickMath.outputX(40, -40, radius) > 0);
    }

    @Test
    public void lowerDiagonalsKeepDownDirection() {
        double radius = Math.hypot(40, 40);

        assertTrue(TouchKitStickMath.outputY(-40, 40, radius) < 0);
        assertTrue(TouchKitStickMath.outputY(40, 40, radius) < 0);
    }

    @Test
    public void centerProducesNoDirection() {
        assertEquals(0, TouchKitStickMath.outputX(0, 0, 0), EPSILON);
        assertEquals(0, TouchKitStickMath.outputY(0, 0, 0), EPSILON);
    }
}
