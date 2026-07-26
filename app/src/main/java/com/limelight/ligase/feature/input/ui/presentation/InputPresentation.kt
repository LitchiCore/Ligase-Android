package com.limelight.ligase.feature.input.ui.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputSelection
import com.limelight.ligase.input.LigaseInputSelectionStatus

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
)

fun inputPresentation(
    selectedInput: InputDeviceMode?,
    devices: List<LigaseInputDevice>,
    selectedGamepadKey: String?,
    selectedKeyboardKey: String?,
    selectedMouseKey: String?,
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
