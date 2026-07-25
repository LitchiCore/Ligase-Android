package com.limelight.ligase.feature.input.ui.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputSelection
import com.limelight.ligase.input.LigaseInputSelectionStatus
import com.limelight.ligase.input.LigaseTouchLayout

data class InputDeviceSelectionPresentation(
    val devices: List<LigaseInputDevice>,
    val selectedKey: String?,
    val status: LigaseInputSelectionStatus,
    val selectedDeviceName: String?,
)

data class InputPresentation(
    val mode: InputDeviceMode,
    val gamepads: InputDeviceSelectionPresentation,
    val keyboards: InputDeviceSelectionPresentation,
    val mice: InputDeviceSelectionPresentation,
    val selectedTouchLayout: LigaseTouchLayout?,
    val touchLayoutMissing: Boolean,
    val canEditTouchLayout: Boolean,
)

fun inputPresentation(
    selectedInput: InputDeviceMode?,
    devices: List<LigaseInputDevice>,
    selectedGamepadKey: String?,
    selectedKeyboardKey: String?,
    selectedMouseKey: String?,
    touchLayouts: List<LigaseTouchLayout>,
    selectedTouchLayoutId: String?,
): InputPresentation = InputPresentation(
    mode = selectedInput ?: InputDeviceMode.TOUCH,
    gamepads = selection(
        devices,
        LigaseInputCategory.GAMEPAD,
        selectedGamepadKey,
    ),
    keyboards = selection(
        devices,
        LigaseInputCategory.KEYBOARD,
        selectedKeyboardKey,
    ),
    mice = selection(
        devices,
        LigaseInputCategory.MOUSE,
        selectedMouseKey,
    ),
    selectedTouchLayout = touchLayouts.firstOrNull { it.id == selectedTouchLayoutId },
    touchLayoutMissing = selectedTouchLayoutId != null &&
        touchLayouts.none { it.id == selectedTouchLayoutId },
    canEditTouchLayout = selectedTouchLayoutId != null,
)

private fun selection(
    devices: List<LigaseInputDevice>,
    category: LigaseInputCategory,
    selectedKey: String?,
): InputDeviceSelectionPresentation {
    val matchingDevices = devices.filter { it.category == category }
    return InputDeviceSelectionPresentation(
        devices = matchingDevices,
        selectedKey = selectedKey,
        status = LigaseInputSelection.status(
            selectedKey,
            matchingDevices.mapTo(mutableSetOf()) { it.stableKey },
        ),
        selectedDeviceName = matchingDevices
            .firstOrNull { it.stableKey == selectedKey }
            ?.name,
    )
}
