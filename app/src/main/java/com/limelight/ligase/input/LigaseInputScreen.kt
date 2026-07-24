package com.limelight.ligase.input

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigasePageScaffold
import com.limelight.ligase.LigaseSemanticTheme
import com.limelight.ligase.inputTitle
import com.limelight.ligase.ligaseNavigationContentBottomPadding

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
) {
    if (onboarding) {
        OnboardingInputPage(
            selectedInput = selectedInput,
            onInputSelected = onInputSelected,
            onConfirm = onInputConfirmed,
        )
        return
    }

    var showModePicker by remember { mutableStateOf(false) }
    val mode = selectedInput ?: InputDeviceMode.TOUCH
    LigasePageScaffold(stringResource(R.string.ligase_nav_input)) { pageModifier ->
        LazyColumn(
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
                CurrentInputCard(
                    mode = mode,
                    devices = devices,
                    selectedGamepadKey = selectedGamepadKey,
                    selectedKeyboardKey = selectedKeyboardKey,
                    selectedMouseKey = selectedMouseKey,
                    touchLayouts = touchLayouts,
                    selectedTouchLayoutId = selectedTouchLayoutId,
                    touchOverlayMode = touchOverlayMode,
                    onChange = { showModePicker = true },
                )
            }
            when (mode) {
                InputDeviceMode.GAMEPAD -> {
                    item { InputSectionTitle(R.string.ligase_detected_controllers) }
                    deviceItems(
                        devices = devices.filter {
                            it.category == LigaseInputCategory.GAMEPAD
                        },
                        selectedKey = selectedGamepadKey,
                        emptyText = R.string.ligase_no_controller_detected,
                        onDeviceSelected = onDeviceSelected,
                    )
                }
                InputDeviceMode.KEYBOARD_MOUSE -> {
                    item { InputSectionTitle(R.string.ligase_detected_keyboards) }
                    deviceItems(
                        devices = devices.filter {
                            it.category == LigaseInputCategory.KEYBOARD
                        },
                        selectedKey = selectedKeyboardKey,
                        emptyText = R.string.ligase_no_keyboard_detected,
                        onDeviceSelected = onDeviceSelected,
                    )
                    item { InputSectionTitle(R.string.ligase_detected_mice) }
                    deviceItems(
                        devices = devices.filter {
                            it.category == LigaseInputCategory.MOUSE
                        },
                        selectedKey = selectedMouseKey,
                        emptyText = R.string.ligase_no_mouse_detected,
                        onDeviceSelected = onDeviceSelected,
                    )
                }
                InputDeviceMode.TOUCH -> {
                    item { InputSectionTitle(R.string.ligase_touch_overlays) }
                    item {
                        TouchOverlaySelector(
                            mode = touchOverlayMode,
                            onChanged = onTouchOverlayModeChanged,
                        )
                    }
                    if (touchOverlayMode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD) {
                        item { InputSectionTitle(R.string.ligase_global_touch_layout) }
                        if (
                            selectedTouchLayoutId != null &&
                            touchLayouts.none { it.id == selectedTouchLayoutId }
                        ) {
                            item {
                                InputNoticeCard(
                                    text = stringResource(
                                        R.string.ligase_touch_layout_missing,
                                        selectedTouchLayoutId,
                                    ),
                                    error = true,
                                )
                            }
                        }
                        if (touchLayouts.isEmpty()) {
                            item {
                                InputNoticeCard(
                                    text = stringResource(R.string.ligase_no_touch_layout),
                                    error = true,
                                )
                            }
                        } else {
                            items(
                                count = touchLayouts.size,
                                key = { touchLayouts[it].id },
                            ) { index ->
                                val layout = touchLayouts[index]
                                TouchLayoutCard(
                                    layout = layout,
                                    selected = layout.id == selectedTouchLayoutId,
                                    onClick = { onTouchLayoutSelected(layout.id) },
                                )
                            }
                        }
                        item {
                            Text(
                                text = stringResource(
                                    R.string.ligase_global_touch_layout_summary,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.ligase_layout_edit_future))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModePicker) {
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = { Text(stringResource(R.string.ligase_input_change_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InputModeChoiceCard(
                        mode = InputDeviceMode.GAMEPAD,
                        selected = mode == InputDeviceMode.GAMEPAD,
                        icon = R.drawable.ic_ligase_gamepad,
                        title = R.string.ligase_input_gamepad,
                        summary = R.string.ligase_input_gamepad_summary,
                    ) {
                        onInputSelected(it)
                        showModePicker = false
                    }
                    InputModeChoiceCard(
                        mode = InputDeviceMode.KEYBOARD_MOUSE,
                        selected = mode == InputDeviceMode.KEYBOARD_MOUSE,
                        icon = R.drawable.ic_ligase_keyboard_mouse,
                        title = R.string.ligase_input_keyboard_mouse,
                        summary = R.string.ligase_input_keyboard_mouse_summary,
                    ) {
                        onInputSelected(it)
                        showModePicker = false
                    }
                    InputModeChoiceCard(
                        mode = InputDeviceMode.TOUCH,
                        selected = mode == InputDeviceMode.TOUCH,
                        icon = R.drawable.ic_ligase_touch,
                        title = R.string.ligase_input_touch,
                        summary = R.string.ligase_input_touch_summary,
                    ) {
                        onInputSelected(it)
                        showModePicker = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun OnboardingInputPage(
    selectedInput: InputDeviceMode?,
    onInputSelected: (InputDeviceMode) -> Unit,
    onConfirm: () -> Unit,
) {
    LigasePageScaffold(stringResource(R.string.ligase_brand)) { pageModifier ->
        LazyColumn(
            modifier = pageModifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.ligase_input_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ligase_input_setup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                InputModeChoiceCard(
                    InputDeviceMode.GAMEPAD,
                    selectedInput == InputDeviceMode.GAMEPAD,
                    R.drawable.ic_ligase_gamepad,
                    R.string.ligase_input_gamepad,
                    R.string.ligase_input_gamepad_summary,
                    onInputSelected,
                )
            }
            item {
                InputModeChoiceCard(
                    InputDeviceMode.KEYBOARD_MOUSE,
                    selectedInput == InputDeviceMode.KEYBOARD_MOUSE,
                    R.drawable.ic_ligase_keyboard_mouse,
                    R.string.ligase_input_keyboard_mouse,
                    R.string.ligase_input_keyboard_mouse_summary,
                    onInputSelected,
                )
            }
            item {
                InputModeChoiceCard(
                    InputDeviceMode.TOUCH,
                    selectedInput == InputDeviceMode.TOUCH,
                    R.drawable.ic_ligase_touch,
                    R.string.ligase_input_touch,
                    R.string.ligase_input_touch_summary,
                    onInputSelected,
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onConfirm,
                    enabled = selectedInput != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = LigaseSemanticTheme.colors.surfaceVariant,
                        disabledContentColor = LigaseSemanticTheme.colors.disabled,
                    ),
                ) {
                    Text(stringResource(R.string.ligase_continue))
                }
            }
        }
    }
}

@Composable
private fun CurrentInputCard(
    mode: InputDeviceMode,
    devices: List<LigaseInputDevice>,
    selectedGamepadKey: String?,
    selectedKeyboardKey: String?,
    selectedMouseKey: String?,
    touchLayouts: List<LigaseTouchLayout>,
    selectedTouchLayoutId: String?,
    touchOverlayMode: LigaseTouchOverlayMode,
    onChange: () -> Unit,
) {
    val detail = when (mode) {
        InputDeviceMode.GAMEPAD -> deviceSummary(
            devices,
            selectedGamepadKey,
            LigaseInputCategory.GAMEPAD,
        )
        InputDeviceMode.KEYBOARD_MOUSE -> keyboardMouseSummary(
            devices,
            selectedKeyboardKey,
            selectedMouseKey,
        )
        InputDeviceMode.TOUCH -> touchOverlaySummary(
            mode = touchOverlayMode,
            layoutName = touchLayouts
                .firstOrNull { it.id == selectedTouchLayoutId }
                ?.displayName,
        )
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(inputIcon(mode)),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = stringResource(inputTitle(mode)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChange) {
                    Text(stringResource(R.string.ligase_change))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (mode == InputDeviceMode.TOUCH) {
                    stringResource(
                        when {
                            touchOverlayMode == LigaseTouchOverlayMode.VIRTUAL_GAMEPAD ->
                                R.string.ligase_touch_behavior_gamepad
                            touchOverlayMode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD ->
                                R.string.ligase_touch_behavior_keyboard
                            else -> R.string.ligase_touch_behavior_gestures
                        },
                    )
                } else {
                    stringResource(inputModeBehavior(mode))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TouchOverlaySelector(
    mode: LigaseTouchOverlayMode,
    onChanged: (LigaseTouchOverlayMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.ligase_touch_overlays_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TouchOverlayOptionCard(
            title = R.string.ligase_virtual_keyboard,
            summary = R.string.ligase_virtual_keyboard_option_summary,
            selected = mode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
            onClick = { onChanged(LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD) },
        )
        TouchOverlayOptionCard(
            title = R.string.ligase_virtual_gamepad,
            summary = R.string.ligase_virtual_gamepad_option_summary,
            selected = mode == LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
            onClick = { onChanged(LigaseTouchOverlayMode.VIRTUAL_GAMEPAD) },
        )
        TouchOverlayOptionCard(
            title = R.string.ligase_no_screen_controls,
            summary = R.string.ligase_no_screen_controls_summary,
            selected = mode == LigaseTouchOverlayMode.GESTURES_ONLY,
            onClick = { onChanged(LigaseTouchOverlayMode.GESTURES_ONLY) },
        )
    }
}

@Composable
private fun TouchOverlayOptionCard(
    @StringRes title: Int,
    @StringRes summary: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                LigaseSemanticTheme.colors.selected
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun touchOverlaySummary(
    mode: LigaseTouchOverlayMode,
    layoutName: String?,
): String = when {
    mode == LigaseTouchOverlayMode.VIRTUAL_GAMEPAD ->
        stringResource(R.string.ligase_virtual_gamepad)
    mode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD ->
        stringResource(
            R.string.ligase_touch_keyboard_summary,
            layoutName ?: stringResource(R.string.ligase_layout_reselect_required),
        )
    else -> stringResource(R.string.ligase_touch_overlays_none_summary)
}

private fun androidx.compose.foundation.lazy.LazyListScope.deviceItems(
    devices: List<LigaseInputDevice>,
    selectedKey: String?,
    @StringRes emptyText: Int,
    onDeviceSelected: (LigaseInputCategory, String) -> Unit,
) {
    if (devices.isEmpty()) {
        item { InputNoticeCard(stringResource(emptyText), error = false) }
    } else {
        items(count = devices.size, key = { devices[it].stableKey }) { index ->
            val device = devices[index]
            InputDeviceCard(
                device = device,
                selected = device.stableKey == selectedKey,
                onClick = {
                    onDeviceSelected(device.category, device.stableKey)
                },
            )
        }
    }
    if (selectedKey != null && devices.none { it.stableKey == selectedKey }) {
        item {
            InputNoticeCard(
                text = stringResource(R.string.ligase_selected_device_disconnected),
                error = true,
            )
        }
    }
}

@Composable
private fun InputDeviceCard(
    device: LigaseInputDevice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) LigaseSemanticTheme.colors.selected
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        when (device.connection) {
                            LigaseInputConnection.USB_OTG ->
                                R.string.ligase_connection_usb
                            LigaseInputConnection.EXTERNAL ->
                                R.string.ligase_connection_external
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun TouchLayoutCard(
    layout: LigaseTouchLayout,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) LigaseSemanticTheme.colors.selected
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(layout.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.ligase_touch_layout_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun InputModeChoiceCard(
    mode: InputDeviceMode,
    selected: Boolean,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes summary: Int,
    onClick: (InputDeviceMode) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(mode) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = { onClick(mode) })
        }
    }
}

@Composable
private fun InputNoticeCard(text: String, error: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            color = if (error) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InputSectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun deviceSummary(
    devices: List<LigaseInputDevice>,
    selectedKey: String?,
    category: LigaseInputCategory,
): String {
    val matching = devices.firstOrNull {
        it.category == category && it.stableKey == selectedKey
    }
    return when (
        LigaseInputSelection.status(
            selectedKey,
            devices.filter { it.category == category }.mapTo(mutableSetOf()) {
                it.stableKey
            },
        )
    ) {
        LigaseInputSelectionStatus.UNSELECTED ->
            stringResource(R.string.ligase_device_not_selected)
        LigaseInputSelectionStatus.CONNECTED -> matching?.name
            ?: stringResource(R.string.ligase_selected_device_disconnected)
        LigaseInputSelectionStatus.DISCONNECTED ->
            stringResource(R.string.ligase_selected_device_disconnected)
    }
}

@Composable
private fun keyboardMouseSummary(
    devices: List<LigaseInputDevice>,
    keyboardKey: String?,
    mouseKey: String?,
): String {
    val keyboard = deviceSummary(devices, keyboardKey, LigaseInputCategory.KEYBOARD)
    val mouse = deviceSummary(devices, mouseKey, LigaseInputCategory.MOUSE)
    return stringResource(R.string.ligase_keyboard_mouse_status, keyboard, mouse)
}

@DrawableRes
private fun inputIcon(mode: InputDeviceMode): Int = when (mode) {
    InputDeviceMode.GAMEPAD -> R.drawable.ic_ligase_gamepad
    InputDeviceMode.KEYBOARD_MOUSE -> R.drawable.ic_ligase_keyboard_mouse
    InputDeviceMode.TOUCH -> R.drawable.ic_ligase_touch
}

@StringRes
private fun inputModeBehavior(mode: InputDeviceMode): Int = when (mode) {
    InputDeviceMode.GAMEPAD -> R.string.ligase_gamepad_behavior
    InputDeviceMode.KEYBOARD_MOUSE -> R.string.ligase_keyboard_mouse_behavior
    InputDeviceMode.TOUCH -> R.string.ligase_touch_behavior
}
