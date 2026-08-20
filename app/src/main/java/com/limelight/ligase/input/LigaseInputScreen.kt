package com.limelight.ligase.input

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.feature.input.ui.InputRoute
import com.limelight.ligase.feature.input.ui.presentation.InputRouteActions
import com.limelight.ligase.feature.input.ui.presentation.InputRouteState
import com.limelight.ligase.feature.input.application.GameInputOverrideTarget

@Composable
fun LigaseInputPage(
    selectedInput: InputDeviceMode?,
    onboarding: Boolean,
    devices: List<LigaseInputDevice>,
    selectedGamepadKey: String?,
    selectedKeyboardKey: String?,
    selectedMouseKey: String?,
    touchOverlayMode: LigaseTouchOverlayMode,
    cloudTouchMode: LigaseCloudTouchMode = LigaseCloudTouchMode.SINGLE_TOUCH,
    inputSettingsWritable: Boolean = true,
    effectiveStreamingTouchMode: EffectiveStreamingTouchMode =
        EffectiveStreamingTouchMode.ABSOLUTE_POINTER,
    gameOverrideTargets: List<GameInputOverrideTarget> = emptyList(),
    onInputSelected: (InputDeviceMode) -> Unit,
    onInputConfirmed: () -> Unit,
    onDeviceSelected: (LigaseInputCategory, String) -> Unit,
    onTouchOverlayModeChanged: (LigaseTouchOverlayMode) -> Unit,
    onCloudTouchModeChanged: (LigaseCloudTouchMode) -> Unit,
    onGameInputOverrideSelected: (GameInputOverrideTarget) -> Unit = {},
    listState: LazyListState? = null,
) {
    InputRoute(
        state = InputRouteState(
            selectedInput = selectedInput,
            onboarding = onboarding,
            devices = devices,
            selectedGamepadKey = selectedGamepadKey,
            selectedKeyboardKey = selectedKeyboardKey,
            selectedMouseKey = selectedMouseKey,
            touchOverlayMode = touchOverlayMode,
            cloudTouchMode = cloudTouchMode,
            inputSettingsWritable = inputSettingsWritable,
            effectiveStreamingTouchMode = effectiveStreamingTouchMode,
            gameOverrideTargets = gameOverrideTargets,
        ),
        actions = InputRouteActions(
            onInputSelected = onInputSelected,
            onInputConfirmed = onInputConfirmed,
            onDeviceSelected = onDeviceSelected,
            onTouchOverlayModeChanged = onTouchOverlayModeChanged,
            onCloudTouchModeChanged = onCloudTouchModeChanged,
            onGameInputOverrideSelected = onGameInputOverrideSelected,
        ),
        listState = listState,
    )
}
