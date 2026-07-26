package com.limelight.ligase.feature.input.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigasePageScaffold
import com.limelight.ligase.feature.input.ui.components.CurrentInputSummaryCard
import com.limelight.ligase.feature.input.ui.components.InputModePickerDialog
import com.limelight.ligase.feature.input.ui.components.InputOnboarding
import com.limelight.ligase.feature.input.ui.components.InputSectionTitle
import com.limelight.ligase.feature.input.ui.components.inputDeviceItems
import com.limelight.ligase.feature.input.ui.components.touchInputItems
import com.limelight.ligase.feature.input.ui.presentation.InputRouteActions
import com.limelight.ligase.feature.input.ui.presentation.InputRouteState
import com.limelight.ligase.feature.input.ui.presentation.presentation
import com.limelight.ligase.ligaseNavigationContentBottomPadding

@Composable
fun InputRoute(
    state: InputRouteState,
    actions: InputRouteActions,
    listState: LazyListState? = null,
) {
    if (state.onboarding) {
        InputOnboarding(
            selectedInput = state.selectedInput,
            onInputSelected = actions.onInputSelected,
            onConfirm = actions.onInputConfirmed,
        )
        return
    }

    var showModePicker by remember { mutableStateOf(false) }
    val presentation = state.presentation()
    val mode = presentation.mode
    val effectiveListState = listState ?: rememberLazyListState()
    LigasePageScaffold(stringResource(R.string.ligase_nav_input)) { pageModifier ->
        LazyColumn(
            state = effectiveListState,
            modifier = pageModifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 16.dp,
                end = 20.dp,
                bottom = ligaseNavigationContentBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CurrentInputSummaryCard(
                    presentation = presentation,
                    touchOverlayMode = state.touchOverlayMode,
                    onChange = { showModePicker = true },
                )
            }
            when (mode) {
                InputDeviceMode.GAMEPAD -> {
                    item { InputSectionTitle(R.string.ligase_detected_controllers) }
                    inputDeviceItems(
                        selection = presentation.gamepads,
                        emptyText = R.string.ligase_no_controller_detected,
                        onDeviceSelected = actions.onDeviceSelected,
                    )
                }
                InputDeviceMode.KEYBOARD_MOUSE -> {
                    item { InputSectionTitle(R.string.ligase_detected_keyboards) }
                    inputDeviceItems(
                        selection = presentation.keyboards,
                        emptyText = R.string.ligase_no_keyboard_detected,
                        onDeviceSelected = actions.onDeviceSelected,
                    )
                    item { InputSectionTitle(R.string.ligase_detected_mice) }
                    inputDeviceItems(
                        selection = presentation.mice,
                        emptyText = R.string.ligase_no_mouse_detected,
                        onDeviceSelected = actions.onDeviceSelected,
                    )
                }
                InputDeviceMode.TOUCH -> {
                    touchInputItems(
                        presentation = presentation,
                        touchLayouts = state.touchLayouts,
                        touchOverlayMode = state.touchOverlayMode,
                        selectedTouchLayoutId = state.selectedTouchLayoutId,
                        onTouchLayoutSelected = actions.onTouchLayoutSelected,
                        onTouchOverlayModeChanged = actions.onTouchOverlayModeChanged,
                        onBrowseLayouts = actions.onBrowseLayouts,
                    )
                }
            }
        }
    }

    if (showModePicker) {
        InputModePickerDialog(
            mode = mode,
            onDismiss = { showModePicker = false },
            onInputSelected = actions.onInputSelected,
        )
    }
}
