package com.limelight.ligase.input

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3RuntimeDecision
import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3RuntimeGate

data class LigaseInputLaunchDecision(
    val modeValue: String,
    val showVirtualGamepad: Boolean,
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
        overlayMode: LigaseTouchOverlayMode = LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
    ): LigaseInputLaunchDecision? = when (mode) {
        InputDeviceMode.GAMEPAD -> LigaseInputLaunchDecision(
            modeValue = mode.storedValue,
            showVirtualGamepad = false,
        )
        InputDeviceMode.KEYBOARD_MOUSE -> LigaseInputLaunchDecision(
            modeValue = mode.storedValue,
            showVirtualGamepad = false,
        )
        InputDeviceMode.TOUCH -> {
            if (overlayMode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD &&
                LayoutV3RuntimeGate.current() is LayoutV3RuntimeDecision.Unavailable
            ) {
                null
            } else {
                LigaseInputLaunchDecision(
                    modeValue = mode.storedValue,
                    showVirtualGamepad =
                        overlayMode == LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
                )
            }
        }
    }
}
