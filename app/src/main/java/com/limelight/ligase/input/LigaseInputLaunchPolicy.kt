package com.limelight.ligase.input

import com.limelight.ligase.InputDeviceMode

data class LigaseInputLaunchDecision(
    val modeValue: String,
    val showVirtualGamepad: Boolean,
    val showTouchKitKeyboard: Boolean,
    val touchLayoutId: String?,
) {
    val showTouchControls: Boolean
        get() = showVirtualGamepad || showTouchKitKeyboard
}

object LigaseInputLaunchPolicy {
    @JvmStatic
    fun showTouchControls(modeValue: String?): Boolean? = when (modeValue) {
        InputDeviceMode.TOUCH.storedValue -> true
        InputDeviceMode.GAMEPAD.storedValue,
        InputDeviceMode.KEYBOARD_MOUSE.storedValue,
        -> false
        else -> null
    }

    fun resolve(
        mode: InputDeviceMode,
        selectedTouchLayoutId: String?,
        availableTouchLayoutIds: Set<String>,
        overlayMode: LigaseTouchOverlayMode = LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
    ): LigaseInputLaunchDecision? = when (mode) {
        InputDeviceMode.GAMEPAD -> LigaseInputLaunchDecision(
            modeValue = mode.storedValue,
            showVirtualGamepad = false,
            showTouchKitKeyboard = false,
            touchLayoutId = null,
        )
        InputDeviceMode.KEYBOARD_MOUSE -> LigaseInputLaunchDecision(
            modeValue = mode.storedValue,
            showVirtualGamepad = false,
            showTouchKitKeyboard = false,
            touchLayoutId = null,
        )
        InputDeviceMode.TOUCH -> {
            if (
                overlayMode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD &&
                (
                    selectedTouchLayoutId == null ||
                        selectedTouchLayoutId !in availableTouchLayoutIds
                )
            ) {
                null
            } else {
                LigaseInputLaunchDecision(
                    modeValue = mode.storedValue,
                    showVirtualGamepad =
                        overlayMode == LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
                    showTouchKitKeyboard =
                        overlayMode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
                    touchLayoutId = selectedTouchLayoutId
                        .takeIf {
                            overlayMode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD
                        },
                )
            }
        }
    }
}
