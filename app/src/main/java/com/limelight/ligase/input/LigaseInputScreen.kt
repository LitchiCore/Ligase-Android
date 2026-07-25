package com.limelight.ligase.input

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.feature.input.ui.InputRoute
import com.limelight.ligase.feature.input.ui.presentation.InputRouteActions
import com.limelight.ligase.feature.input.ui.presentation.InputRouteState

@Composable
fun LigaseInputPage(
    selectedInput: InputDeviceMode?,
    onboarding: Boolean,
    devices: List<LigaseInputDevice>,
    selectedGamepadKey: String?,
    selectedKeyboardKey: String?,
    selectedMouseKey: String?,
    touchLayouts: List<LigaseTouchLayout>,
    selectedTouchLayoutId: String?,
    touchOverlayMode: LigaseTouchOverlayMode,
    onInputSelected: (InputDeviceMode) -> Unit,
    onInputConfirmed: () -> Unit,
    onDeviceSelected: (LigaseInputCategory, String) -> Unit,
    onTouchLayoutSelected: (String) -> Unit,
    onTouchOverlayModeChanged: (LigaseTouchOverlayMode) -> Unit,
    listState: LazyListState? = null,
    selectedTouchLayoutEditable: Boolean = false,
    onBrowseLayouts: () -> Unit = {},
    onEditTouchLayout: () -> Unit = {},
) {
    InputRoute(
        state = InputRouteState(
            selectedInput = selectedInput,
            onboarding = onboarding,
            devices = devices,
            selectedGamepadKey = selectedGamepadKey,
            selectedKeyboardKey = selectedKeyboardKey,
            selectedMouseKey = selectedMouseKey,
            touchLayouts = touchLayouts,
            selectedTouchLayoutId = selectedTouchLayoutId,
            touchOverlayMode = touchOverlayMode,
            selectedTouchLayoutEditable = selectedTouchLayoutEditable,
        ),
        actions = InputRouteActions(
            onInputSelected = onInputSelected,
            onInputConfirmed = onInputConfirmed,
            onDeviceSelected = onDeviceSelected,
            onTouchLayoutSelected = onTouchLayoutSelected,
            onTouchOverlayModeChanged = onTouchOverlayModeChanged,
            onBrowseLayouts = onBrowseLayouts,
            onEditTouchLayout = onEditTouchLayout,
        ),
        listState = listState,
    )
}
