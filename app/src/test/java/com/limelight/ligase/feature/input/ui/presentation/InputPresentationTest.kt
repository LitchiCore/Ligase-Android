package com.limelight.ligase.feature.input.ui.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputConnection
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputSelectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputPresentationTest {
    @Test
    fun `missing mode falls back to touch without changing stored state`() {
        val presentation = presentation(selectedInput = null)

        assertEquals(InputDeviceMode.TOUCH, presentation.mode)
    }

    @Test
    fun `stable device selections are grouped and disconnected explicitly`() {
        val connected = device("pad-key", LigaseInputCategory.GAMEPAD, "Controller")
        val presentation = presentation(
            devices = listOf(
                connected,
                device("keyboard-key", LigaseInputCategory.KEYBOARD, "Keyboard"),
            ),
            selectedGamepadKey = "pad-key",
            selectedMouseKey = "missing-mouse",
        )

        assertEquals(listOf(connected), presentation.gamepads.devices)
        assertEquals(LigaseInputSelectionStatus.CONNECTED, presentation.gamepads.status)
        assertEquals("Controller", presentation.gamepads.selectedDeviceName)
        assertEquals(LigaseInputSelectionStatus.DISCONNECTED, presentation.mice.status)
        assertNull(presentation.mice.selectedDeviceName)
    }

    private fun presentation(
        selectedInput: InputDeviceMode? = InputDeviceMode.TOUCH,
        devices: List<LigaseInputDevice> = emptyList(),
        selectedGamepadKey: String? = null,
        selectedKeyboardKey: String? = null,
        selectedMouseKey: String? = null,
    ): InputPresentation = inputPresentation(
        selectedInput = selectedInput,
        devices = devices,
        selectedGamepadKey = selectedGamepadKey,
        selectedKeyboardKey = selectedKeyboardKey,
        selectedMouseKey = selectedMouseKey,
    )

    private fun device(
        key: String,
        category: LigaseInputCategory,
        name: String,
    ): LigaseInputDevice = LigaseInputDevice(
        stableKey = key,
        descriptor = key,
        vendorId = 1,
        productId = 2,
        category = category,
        name = name,
        connection = LigaseInputConnection.EXTERNAL,
    )
}
