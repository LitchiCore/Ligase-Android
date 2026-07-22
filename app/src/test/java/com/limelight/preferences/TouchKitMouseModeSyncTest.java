package com.limelight.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TouchKitMouseModeSyncTest {
    @Test
    public void cloudToggle_selectsCloudModeAndReturnsToMultiTouch() {
        assertEquals(TouchKitMouseModeSync.CLOUD_MODE,
                TouchKitMouseModeSync.mouseModeForCloudToggle(true, "2"));
        assertEquals(TouchKitMouseModeSync.DEFAULT_MODE,
                TouchKitMouseModeSync.mouseModeForCloudToggle(false,
                        TouchKitMouseModeSync.CLOUD_MODE));
    }

    @Test
    public void mouseSelection_enablesCloudOnlyForCloudMode() {
        assertTrue(TouchKitMouseModeSync.isCloudMode("6"));
        assertFalse(TouchKitMouseModeSync.isCloudMode("0"));
        assertFalse(TouchKitMouseModeSync.isCloudMode("3"));
    }

    @Test
    public void disablingCloud_preservesAlreadySelectedNormalMode() {
        assertEquals("3", TouchKitMouseModeSync.mouseModeForCloudToggle(false, "3"));
    }
}
