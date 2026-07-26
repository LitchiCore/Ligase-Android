package com.limelight.ligase.feature.input.ui.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseTouchOverlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class InputRouteContractTest {
    @Test
    fun `route state keeps missing mode fallback as presentation only`() {
        val state = state(selectedInput = null)

        assertEquals(InputDeviceMode.TOUCH, state.presentation().mode)
        assertEquals(null, state.selectedInput)
    }

    private fun state(
        selectedInput: InputDeviceMode?,
    ) = InputRouteState(
        selectedInput = selectedInput,
        onboarding = false,
        devices = emptyList(),
        selectedGamepadKey = null,
        selectedKeyboardKey = null,
        selectedMouseKey = null,
        touchOverlayMode = LigaseTouchOverlayMode.GESTURES_ONLY,
    )
}
