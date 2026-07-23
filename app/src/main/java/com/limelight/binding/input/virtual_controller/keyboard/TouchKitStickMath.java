package com.limelight.binding.input.virtual_controller.keyboard;

final class TouchKitStickMath {
    private TouchKitStickMath() {
    }

    static float outputX(float deltaX, float deltaY, double clampedRadius) {
        double rawRadius = Math.hypot(deltaX, deltaY);
        return rawRadius == 0 ? 0 : (float) (deltaX * clampedRadius / rawRadius);
    }

    static float outputY(float deltaX, float deltaY, double clampedRadius) {
        double rawRadius = Math.hypot(deltaX, deltaY);
        return rawRadius == 0 ? 0 : (float) (-deltaY * clampedRadius / rawRadius);
    }
}
