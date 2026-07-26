package com.limelight.ligase.feature.input.ui.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseTouchOverlayMode

data class InputRouteState(
    val selectedInput: InputDeviceMode?,
    val onboarding: Boolean,
    val devices: List<LigaseInputDevice>,
    val selectedGamepadKey: String?,
    val selectedKeyboardKey: String?,
    val selectedMouseKey: String?,
    val touchOverlayMode: LigaseTouchOverlayMode,
)

data class InputRouteActions(
    val onInputSelected: (InputDeviceMode) -> Unit,
    val onInputConfirmed: () -> Unit,
    val onDeviceSelected: (LigaseInputCategory, String) -> Unit,
    val onTouchOverlayModeChanged: (LigaseTouchOverlayMode) -> Unit,
)

fun InputRouteState.presentation(): InputPresentation = inputPresentation(
    selectedInput = selectedInput,
    devices = devices,
    selectedGamepadKey = selectedGamepadKey,
    selectedKeyboardKey = selectedKeyboardKey,
    selectedMouseKey = selectedMouseKey,
)
