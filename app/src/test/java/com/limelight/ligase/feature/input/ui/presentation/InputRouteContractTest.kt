package com.limelight.ligase.feature.input.ui.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseTouchLayout
import com.limelight.ligase.input.LigaseTouchOverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputRouteContractTest {
    @Test
    fun `route state preserves touch selection and editability`() {
        val state = state(
            selectedInput = InputDeviceMode.TOUCH,
            selectedTouchLayoutId = "layout-a",
            selectedTouchLayoutEditable = true,
        )

        val presentation = state.presentation()

        assertEquals(InputDeviceMode.TOUCH, presentation.mode)
        assertEquals("layout-a", presentation.selectedTouchLayout?.id)
        assertTrue(presentation.canEditTouchLayout)
        assertFalse(presentation.touchLayoutMissing)
    }

    @Test
    fun `route state keeps missing mode fallback as presentation only`() {
        val state = state(selectedInput = null)

        assertEquals(InputDeviceMode.TOUCH, state.presentation().mode)
        assertEquals(null, state.selectedInput)
    }

    private fun state(
        selectedInput: InputDeviceMode?,
        selectedTouchLayoutId: String? = null,
        selectedTouchLayoutEditable: Boolean = false,
    ) = InputRouteState(
        selectedInput = selectedInput,
        onboarding = false,
        devices = emptyList(),
        selectedGamepadKey = null,
        selectedKeyboardKey = null,
        selectedMouseKey = null,
        touchLayouts = listOf(LigaseTouchLayout("layout-a", "Layout A")),
        selectedTouchLayoutId = selectedTouchLayoutId,
        touchOverlayMode = LigaseTouchOverlayMode.GESTURES_ONLY,
        selectedTouchLayoutEditable = selectedTouchLayoutEditable,
    )
}
