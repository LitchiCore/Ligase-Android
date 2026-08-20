package com.limelight.ligase.input

import com.limelight.ligase.InputDeviceMode
data class LigaseInputLaunchDecision(
    val modeValue: String,
    val showVirtualGamepad: Boolean,
    val cloudTouchMode: LigaseCloudTouchMode,
) {
    val showTouchControls: Boolean
        get() = showVirtualGamepad
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
        overlayMode: LigaseTouchOverlayMode = LigaseTouchOverlayMode.CLOUD_CONTROLS,
        cloudTouchMode: LigaseCloudTouchMode = LigaseCloudTouchMode.SINGLE_TOUCH,
    ): LigaseInputLaunchDecision? = when (mode) {
        InputDeviceMode.GAMEPAD -> LigaseInputLaunchDecision(
            modeValue = mode.storedValue,
            showVirtualGamepad = false,
            cloudTouchMode = cloudTouchMode,
        )
        InputDeviceMode.KEYBOARD_MOUSE -> LigaseInputLaunchDecision(
            modeValue = mode.storedValue,
            showVirtualGamepad = false,
            cloudTouchMode = cloudTouchMode,
        )
        InputDeviceMode.TOUCH -> {
            LigaseInputLaunchDecision(
                modeValue = mode.storedValue,
                showVirtualGamepad = overlayMode == LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
                cloudTouchMode = cloudTouchMode,
            )
        }
    }
}
