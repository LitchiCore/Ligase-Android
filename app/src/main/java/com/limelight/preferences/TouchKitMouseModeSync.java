package com.limelight.preferences;

/** Keeps the TouchKit cloud switch and Artemis mouse-mode list consistent. */
final class TouchKitMouseModeSync {
    static final String DEFAULT_MODE = "0";
    static final String CLOUD_MODE = "6";

    private TouchKitMouseModeSync() { }

    static boolean isCloudMode(String mouseMode) {
        return CLOUD_MODE.equals(mouseMode);
    }

    static String mouseModeForCloudToggle(boolean enabled, String currentMouseMode) {
        if (enabled) {
            return CLOUD_MODE;
        }
        return isCloudMode(currentMouseMode) ? DEFAULT_MODE : currentMouseMode;
    }
}
