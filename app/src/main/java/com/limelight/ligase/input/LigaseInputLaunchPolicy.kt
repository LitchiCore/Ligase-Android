package com.limelight.ligase.input

import com.limelight.ligase.InputDeviceMode

data class LigaseInputLaunchDecision(
    val modeValue: String,
    val showTouchControls: Boolean,
    val touchLayoutId: String?,
)

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
    ): LigaseInputLaunchDecision? = when (mode) {
        InputDeviceMode.GAMEPAD -> LigaseInputLaunchDecision(
            modeValue = mode.storedValue,
            showTouchControls = false,
            touchLayoutId = null,
        )
        InputDeviceMode.KEYBOARD_MOUSE -> LigaseInputLaunchDecision(
            modeValue = mode.storedValue,
            showTouchControls = false,
            touchLayoutId = null,
        )
        InputDeviceMode.TOUCH -> {
            if (
                selectedTouchLayoutId == null ||
                selectedTouchLayoutId !in availableTouchLayoutIds
            ) {
                null
            } else {
                LigaseInputLaunchDecision(
                    modeValue = mode.storedValue,
                    showTouchControls = true,
                    touchLayoutId = selectedTouchLayoutId,
                )
            }
        }
    }
}
