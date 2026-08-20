package com.limelight.ligase.feature.input.ui.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.feature.input.application.GameInputOverrideTarget
import com.limelight.ligase.input.LigaseInputCategory
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class InputRouteContractTest {
    @Test
    fun `route state keeps missing mode fallback as presentation only`() {
        val state = state(selectedInput = null)

        assertEquals(InputDeviceMode.TOUCH, state.presentation().mode)
        assertEquals(null, state.selectedInput)
    }

    @Test
    fun `canonical game target makes section visible and route calls exact target once`() {
        val target = GameInputOverrideTarget(
            "67209ea3-7129-42d0-9349-52f8799d292d",
            "Game",
            "Steam · App ID 3548580",
        )
        val routeState = state(InputDeviceMode.TOUCH).copy(gameOverrideTargets = listOf(target))
        var calls = 0
        var received: GameInputOverrideTarget? = null
        val actions = InputRouteActions(
            onInputSelected = {},
            onInputConfirmed = {},
            onDeviceSelected = { _: LigaseInputCategory, _: String -> },
            onTouchOverlayModeChanged = {},
            onCloudTouchModeChanged = {},
            onGameInputOverrideSelected = { value -> calls++; received = value },
        )

        assertTrue(routeState.showsGameOverrideSection)
        actions.onGameInputOverrideSelected(target)
        assertEquals(1, calls)
        assertEquals(target, received)
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
        touchOverlayMode = LigaseTouchOverlayMode.HIDDEN,
    )
}
