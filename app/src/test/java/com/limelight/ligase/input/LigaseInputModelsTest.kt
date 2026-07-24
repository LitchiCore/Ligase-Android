package com.limelight.ligase.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LigaseInputModelsTest {
    @Test
    fun `stable identity ignores transient Android device id and display name`() {
        val beforeDisconnect = LigaseInputIdentity.stableKey(
            descriptor = "usb-1234",
            vendorId = 1118,
            productId = 654,
            category = LigaseInputCategory.GAMEPAD,
        )
        val afterReconnect = LigaseInputIdentity.stableKey(
            descriptor = "usb-1234",
            vendorId = 1118,
            productId = 654,
            category = LigaseInputCategory.GAMEPAD,
        )

        assertEquals(beforeDisconnect, afterReconnect)
        assertFalse(beforeDisconnect!!.contains("deviceId"))
        assertNull(
            LigaseInputIdentity.stableKey(
                descriptor = " ",
                vendorId = 0,
                productId = 0,
                category = LigaseInputCategory.MOUSE,
            ),
        )
    }

    @Test
    fun `capabilities classify a combined keyboard and mouse without name guessing`() {
        val sourceKeyboard = 0x00000101
        val sourceMouse = 0x00002002
        val categories = LigaseInputSourceClassifier.categories(
            sources = sourceKeyboard or sourceMouse,
            keyboardType = 2,
            sourceGamepad = 0x00000401,
            sourceJoystick = 0x01000010,
            sourceKeyboard = sourceKeyboard,
            sourceMouse = sourceMouse,
            sourceMouseRelative = 0x00020004,
            keyboardTypeAlphabetic = 2,
        )

        assertEquals(
            setOf(LigaseInputCategory.KEYBOARD, LigaseInputCategory.MOUSE),
            categories,
        )
    }

    @Test
    fun `non alphabetic media controls are not listed as keyboards`() {
        val sourceKeyboard = 0x00000101

        assertEquals(
            emptySet<LigaseInputCategory>(),
            LigaseInputSourceClassifier.categories(
                sources = sourceKeyboard,
                keyboardType = 1,
                sourceGamepad = 0x00000401,
                sourceJoystick = 0x01000010,
                sourceKeyboard = sourceKeyboard,
                sourceMouse = 0x00002002,
                sourceMouseRelative = 0x00020004,
                keyboardTypeAlphabetic = 2,
            ),
        )
    }

    @Test
    fun `selected device remains disconnected until exact stable key reconnects`() {
        val selected = "gamepad|1118|654|usb-1234"

        assertEquals(
            LigaseInputSelectionStatus.DISCONNECTED,
            LigaseInputSelection.status(selected, emptySet()),
        )
        assertEquals(
            LigaseInputSelectionStatus.DISCONNECTED,
            LigaseInputSelection.status(
                selected,
                setOf("gamepad|1118|654|same-name-different-descriptor"),
            ),
        )
        assertEquals(
            LigaseInputSelectionStatus.CONNECTED,
            LigaseInputSelection.status(selected, setOf(selected)),
        )
        assertEquals(
            LigaseInputSelectionStatus.UNSELECTED,
            LigaseInputSelection.status(null, setOf(selected)),
        )
    }

    @Test
    fun `three modes map deterministically to launch overlay decisions`() {
        val layouts = setOf("OSC_Keyboard_1")
        val touch = LigaseInputLaunchPolicy.resolve(
            com.limelight.ligase.InputDeviceMode.TOUCH,
            "OSC_Keyboard_1",
            layouts,
        )!!
        val gamepad = LigaseInputLaunchPolicy.resolve(
            com.limelight.ligase.InputDeviceMode.GAMEPAD,
            "OSC_Keyboard_1",
            layouts,
        )!!
        val keyboardMouse = LigaseInputLaunchPolicy.resolve(
            com.limelight.ligase.InputDeviceMode.KEYBOARD_MOUSE,
            "OSC_Keyboard_1",
            layouts,
        )!!

        assertTrue(touch.showTouchControls)
        assertFalse(touch.showVirtualGamepad)
        assertTrue(touch.showTouchKitKeyboard)
        assertEquals("OSC_Keyboard_1", touch.touchLayoutId)
        assertFalse(gamepad.showTouchControls)
        assertNull(gamepad.touchLayoutId)
        assertFalse(keyboardMouse.showTouchControls)
        assertNull(keyboardMouse.touchLayoutId)
        assertEquals(true, LigaseInputLaunchPolicy.showTouchControls("touch"))
        assertEquals(false, LigaseInputLaunchPolicy.showTouchControls("gamepad"))
        assertNull(LigaseInputLaunchPolicy.showTouchControls("unknown"))
    }

    @Test
    fun `missing global touch layout fails closed`() {
        assertNull(
            LigaseInputLaunchPolicy.resolve(
                com.limelight.ligase.InputDeviceMode.TOUCH,
                "deleted-layout",
                setOf("available-layout"),
            ),
        )
    }

    @Test
    fun `touch screen control modes are mutually exclusive`() {
        val gamepadOnly = LigaseInputLaunchPolicy.resolve(
            mode = com.limelight.ligase.InputDeviceMode.TOUCH,
            selectedTouchLayoutId = null,
            availableTouchLayoutIds = emptySet(),
            overlayMode = LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
        )!!
        val keyboardOnly = LigaseInputLaunchPolicy.resolve(
            mode = com.limelight.ligase.InputDeviceMode.TOUCH,
            selectedTouchLayoutId = "layout",
            availableTouchLayoutIds = setOf("layout"),
            overlayMode = LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
        )!!
        val gesturesOnly = LigaseInputLaunchPolicy.resolve(
            mode = com.limelight.ligase.InputDeviceMode.TOUCH,
            selectedTouchLayoutId = null,
            availableTouchLayoutIds = emptySet(),
            overlayMode = LigaseTouchOverlayMode.GESTURES_ONLY,
        )!!

        assertTrue(gamepadOnly.showVirtualGamepad)
        assertFalse(gamepadOnly.showTouchKitKeyboard)
        assertNull(gamepadOnly.touchLayoutId)
        assertFalse(keyboardOnly.showVirtualGamepad)
        assertTrue(keyboardOnly.showTouchKitKeyboard)
        assertEquals("layout", keyboardOnly.touchLayoutId)
        assertFalse(gesturesOnly.showTouchControls)
        assertNull(gesturesOnly.touchLayoutId)
    }

    @Test
    fun `keyboard overlay still requires exact available layout`() {
        assertNull(
            LigaseInputLaunchPolicy.resolve(
                mode = com.limelight.ligase.InputDeviceMode.TOUCH,
                selectedTouchLayoutId = "missing",
                availableTouchLayoutIds = emptySet(),
                overlayMode = LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
            ),
        )
    }
}
