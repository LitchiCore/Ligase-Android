package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KeyboardDigitalPadButtonTest {
    @Test
    public void topCenterMapsToUp() {
        assertEquals(KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_UP,
                KeyboardDigitalPadButton.directionForPosition(50, 10, 100, 100));
    }

    @Test
    public void upperCornersKeepUpAndAddHorizontalDirection() {
        assertEquals(KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_UP
                        | KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_LEFT,
                KeyboardDigitalPadButton.directionForPosition(10, 10, 100, 100));
        assertEquals(KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_UP
                        | KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_RIGHT,
                KeyboardDigitalPadButton.directionForPosition(90, 10, 100, 100));
    }

    @Test
    public void centerProducesNoDirection() {
        assertEquals(KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_NO_DIRECTION,
                KeyboardDigitalPadButton.directionForPosition(50, 50, 100, 100));
    }

    @Test
    public void movingFromUpToUpRightOnlyChangesRight() {
        int up = KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_UP;
        int upRight = up | KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_RIGHT;

        assertEquals(KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_RIGHT,
                KeyboardDigitalPadButton.changedDirections(up, upRight));
        assertEquals(KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_NO_DIRECTION,
                KeyboardDigitalPadButton.changedDirections(up, up));
        assertEquals(up, KeyboardDigitalPadButton.changedDirections(
                up, KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_NO_DIRECTION));
    }
}
