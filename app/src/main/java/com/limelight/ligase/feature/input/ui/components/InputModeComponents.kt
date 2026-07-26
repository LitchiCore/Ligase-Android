package com.limelight.ligase.feature.input.ui.components

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.limelight.ligase.feature.input.ui.presentation.InputDeviceSelectionPresentation
import com.limelight.ligase.feature.input.ui.presentation.InputPresentation
import com.limelight.ligase.input.LigaseInputSelectionStatus
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.inputTitle

@Composable
fun InputOnboarding(
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
fun CurrentInputSummaryCard(
    presentation: InputPresentation,
    touchOverlayMode: LigaseTouchOverlayMode,
    onChange: () -> Unit,
) {
    val mode = presentation.mode
    val detail = when (mode) {
        InputDeviceMode.GAMEPAD -> deviceSummary(presentation.gamepads)
        InputDeviceMode.KEYBOARD_MOUSE -> stringResource(
            R.string.ligase_keyboard_mouse_status,
            deviceSummary(presentation.keyboards),
            deviceSummary(presentation.mice),
        )
        InputDeviceMode.TOUCH -> touchOverlaySummary(
            mode = touchOverlayMode,
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
fun InputModePickerDialog(
    mode: InputDeviceMode,
    onDismiss: () -> Unit,
    onInputSelected: (InputDeviceMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ligase_input_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modeChoices.forEach { choice ->
                    InputModeChoiceCard(
                        mode = choice.mode,
                        selected = mode == choice.mode,
                        icon = choice.icon,
                        title = choice.title,
                        summary = choice.summary,
                    ) {
                        onInputSelected(it)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
fun InputNoticeCard(text: String, error: Boolean) {
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
fun InputSectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )
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
private fun deviceSummary(selection: InputDeviceSelectionPresentation): String = when (
    selection.status
) {
    LigaseInputSelectionStatus.UNSELECTED ->
        stringResource(R.string.ligase_device_not_selected)
    LigaseInputSelectionStatus.CONNECTED -> selection.selectedDeviceName
        ?: stringResource(R.string.ligase_selected_device_disconnected)
    LigaseInputSelectionStatus.DISCONNECTED ->
        stringResource(R.string.ligase_selected_device_disconnected)
}

@Composable
private fun touchOverlaySummary(
    mode: LigaseTouchOverlayMode,
): String = when {
    mode == LigaseTouchOverlayMode.VIRTUAL_GAMEPAD ->
        stringResource(R.string.ligase_virtual_gamepad)
    mode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD ->
        stringResource(R.string.ligase_touch_layout_runtime_unavailable)
    else -> stringResource(R.string.ligase_touch_overlays_none_summary)
}

private data class InputModeChoice(
    val mode: InputDeviceMode,
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val summary: Int,
)

private val modeChoices = listOf(
    InputModeChoice(
        InputDeviceMode.GAMEPAD,
        R.drawable.ic_ligase_gamepad,
        R.string.ligase_input_gamepad,
        R.string.ligase_input_gamepad_summary,
    ),
    InputModeChoice(
        InputDeviceMode.KEYBOARD_MOUSE,
        R.drawable.ic_ligase_keyboard_mouse,
        R.string.ligase_input_keyboard_mouse,
        R.string.ligase_input_keyboard_mouse_summary,
    ),
    InputModeChoice(
        InputDeviceMode.TOUCH,
        R.drawable.ic_ligase_touch,
        R.string.ligase_input_touch,
        R.string.ligase_input_touch_summary,
    ),
)

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
