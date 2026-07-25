package com.limelight.ligase.feature.input.application

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputConnection
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputSelection
import com.limelight.ligase.input.LigaseInputSelectionStatus
import com.limelight.ligase.input.LigaseTouchOverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSelectionCoordinatorTest {
    @Test
    fun `onboarding mode is not persisted until confirmation`() {
        val harness = Harness(hasMode = false)

        assertTrue(harness.coordinator.state.onboarding)
        assertNull(harness.coordinator.state.selectedMode)
        harness.coordinator.selectMode(InputDeviceMode.GAMEPAD)
        assertEquals(emptyList<InputDeviceMode>(), harness.modeWrites)

        assertTrue(harness.coordinator.confirmSelection())
        assertFalse(harness.coordinator.state.onboarding)
        assertEquals(listOf(InputDeviceMode.GAMEPAD), harness.modeWrites)
    }

    @Test
    fun `existing selection writes mode immediately`() {
        val harness = Harness(hasMode = true, inputMode = InputDeviceMode.TOUCH)

        harness.coordinator.selectMode(InputDeviceMode.KEYBOARD_MOUSE)

        assertEquals(InputDeviceMode.KEYBOARD_MOUSE, harness.coordinator.state.selectedMode)
        assertEquals(listOf(InputDeviceMode.KEYBOARD_MOUSE), harness.modeWrites)
    }

    @Test
    fun `device disconnect and exact stable key reconnect retain selection`() {
        val selected = "gamepad|1118|654|usb-1234"
        val harness = Harness(
            selectedDevices = mutableMapOf(LigaseInputCategory.GAMEPAD to selected),
        )
        harness.coordinator.start()

        harness.deviceCallbacks.single()(listOf(device(selected)))
        assertEquals(
            LigaseInputSelectionStatus.CONNECTED,
            status(harness.coordinator, LigaseInputCategory.GAMEPAD),
        )
        harness.deviceCallbacks.single()(emptyList())
        assertEquals(selected, harness.coordinator.state.selectedGamepadKey)
        assertEquals(InputDeviceMode.TOUCH, harness.coordinator.effectiveMode())
        assertEquals(
            LigaseInputSelectionStatus.DISCONNECTED,
            status(harness.coordinator, LigaseInputCategory.GAMEPAD),
        )
        harness.deviceCallbacks.single()(listOf(device(selected)))
        assertEquals(
            LigaseInputSelectionStatus.CONNECTED,
            status(harness.coordinator, LigaseInputCategory.GAMEPAD),
        )
    }

    @Test
    fun `background and restarted foreground reject stale device callback`() {
        val harness = Harness()
        harness.coordinator.start()
        val first = harness.deviceCallbacks.single()
        harness.coordinator.stop()
        first(listOf(device("gamepad|1|2|stale")))
        assertTrue(harness.coordinator.state.devices.isEmpty())

        harness.coordinator.start()
        val second = harness.deviceCallbacks.last()
        first(listOf(device("gamepad|1|2|still-stale")))
        assertTrue(harness.coordinator.state.devices.isEmpty())
        second(listOf(device("gamepad|1|2|current")))
        assertEquals("gamepad|1|2|current", harness.coordinator.state.devices.single().stableKey)
        assertEquals(2, harness.startCount)
        assertEquals(1, harness.stopCount)
    }

    @Test
    fun `selected stable device survives process style reconstruction`() {
        val persisted = mutableMapOf<LigaseInputCategory, String?>()
        val first = Harness(selectedDevices = persisted)
        first.coordinator.selectDevice(LigaseInputCategory.MOUSE, "mouse|9|8|descriptor")

        val restored = Harness(selectedDevices = persisted)

        assertEquals("mouse|9|8|descriptor", restored.coordinator.state.selectedMouseKey)
        assertEquals(
            listOf(LigaseInputCategory.MOUSE to "mouse|9|8|descriptor"),
            first.deviceWrites,
        )
    }

    @Test
    fun `screen overlay choices remain mutually exclusive single value`() {
        val harness = Harness()

        harness.coordinator.selectOverlayMode(LigaseTouchOverlayMode.VIRTUAL_GAMEPAD)
        harness.coordinator.selectOverlayMode(LigaseTouchOverlayMode.GESTURES_ONLY)

        assertEquals(LigaseTouchOverlayMode.GESTURES_ONLY, harness.coordinator.state.overlayMode)
        assertEquals(
            listOf(
                LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
                LigaseTouchOverlayMode.GESTURES_ONLY,
            ),
            harness.overlayWrites,
        )
    }

    private fun status(
        coordinator: InputSelectionCoordinator,
        category: LigaseInputCategory,
    ): LigaseInputSelectionStatus {
        val selected = when (category) {
            LigaseInputCategory.GAMEPAD -> coordinator.state.selectedGamepadKey
            LigaseInputCategory.KEYBOARD -> coordinator.state.selectedKeyboardKey
            LigaseInputCategory.MOUSE -> coordinator.state.selectedMouseKey
        }
        return LigaseInputSelection.status(
            selected,
            coordinator.state.devices.mapTo(mutableSetOf()) { it.stableKey },
        )
    }

    private fun device(stableKey: String) = LigaseInputDevice(
        stableKey = stableKey,
        descriptor = stableKey.substringAfterLast('|'),
        vendorId = 1,
        productId = 2,
        category = LigaseInputCategory.GAMEPAD,
        name = "Controller",
        connection = LigaseInputConnection.EXTERNAL,
    )

    private class Harness(
        hasMode: Boolean = true,
        inputMode: InputDeviceMode = InputDeviceMode.TOUCH,
        selectedDevices: MutableMap<LigaseInputCategory, String?> = mutableMapOf(),
    ) {
        val modeWrites = mutableListOf<InputDeviceMode>()
        val deviceWrites = mutableListOf<Pair<LigaseInputCategory, String>>()
        val overlayWrites = mutableListOf<LigaseTouchOverlayMode>()
        val deviceCallbacks = mutableListOf<(List<LigaseInputDevice>) -> Unit>()
        var startCount = 0
        var stopCount = 0

        val coordinator = InputSelectionCoordinator(
            hasInputMode = { hasMode },
            readInputMode = { inputMode },
            writeInputMode = modeWrites::add,
            readSelectedDevice = selectedDevices::get,
            writeSelectedDevice = { category, key ->
                selectedDevices[category] = key
                deviceWrites += category to key
            },
            readOverlayMode = { LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD },
            writeOverlayMode = overlayWrites::add,
            createDeviceSession = { callback ->
                deviceCallbacks += callback
                InputDeviceSession(
                    start = { startCount++ },
                    stop = { stopCount++ },
                )
            },
            onStateChanged = {},
        )
    }
}
