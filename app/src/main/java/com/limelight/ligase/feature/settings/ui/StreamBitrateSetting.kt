package com.limelight.ligase.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.feature.settings.presentation.StreamBitrateDraftValidation
import com.limelight.ligase.feature.settings.presentation.StreamBitrateSaveNavigation
import com.limelight.ligase.feature.settings.presentation.streamBitrateSaveNavigation
import com.limelight.ligase.feature.settings.presentation.streamBitratePresetPresentation
import com.limelight.ligase.feature.settings.presentation.validateStreamBitrateDraft
import com.limelight.ligase.feature.stream.application.StreamBitrateSaveError
import com.limelight.ligase.feature.stream.application.StreamBitrateUiState
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StreamBitrateSetting(
    state: StreamBitrateUiState,
    dialogState: StreamBitrateDialogState,
    onPresetSelected: (StreamBitratePresetId) -> Unit,
    onCustomSubmitted: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogState.open(state) },
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.ligase_stream_bitrate),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.ligase_stream_bitrate_current,
                    state.currentDisplayMbps,
                ),
            )
            Text(
                text = stringResource(
                    R.string.ligase_stream_bitrate_recommended,
                    state.recommendedPreset.displayMbps,
                ),
            )
        }
    }

    if (!dialogState.visible) return

    val selectedPresetId =
        dialogState.selectedPresetName?.let(StreamBitratePresetId::valueOf)
    val validation =
        validateStreamBitrateDraft(state, selectedPresetId, dialogState.customMbps)
    val errorText = when {
        state.error == StreamBitrateSaveError.WRITE_FAILED ->
            stringResource(R.string.ligase_stream_bitrate_write_failed)
        state.error == StreamBitrateSaveError.EMPTY ->
            stringResource(R.string.ligase_stream_bitrate_empty)
        state.error == StreamBitrateSaveError.NOT_CANONICAL_NUMBER ->
            stringResource(R.string.ligase_stream_bitrate_invalid_number)
        state.error == StreamBitrateSaveError.OUT_OF_RANGE ->
            stringResource(
                R.string.ligase_stream_bitrate_out_of_range,
                state.customRangeKbps.first / 1000f,
                state.customRangeKbps.last / 1000,
            )
        validation == StreamBitrateDraftValidation.Empty ->
            stringResource(R.string.ligase_stream_bitrate_empty)
        validation == StreamBitrateDraftValidation.NotCanonicalNumber ->
            stringResource(R.string.ligase_stream_bitrate_invalid_number)
        validation == StreamBitrateDraftValidation.OutOfRange ->
            stringResource(
                R.string.ligase_stream_bitrate_out_of_range,
                state.customRangeKbps.first / 1000f,
                state.customRangeKbps.last / 1000,
            )
        else -> null
    }

    LaunchedEffect(state.currentKbps, state.saving, state.error, dialogState.pendingKbps) {
        when (streamBitrateSaveNavigation(dialogState.pendingKbps, state)) {
            StreamBitrateSaveNavigation.CLOSE -> {
                dialogState.close()
            }
            StreamBitrateSaveNavigation.KEEP_OPEN -> dialogState.pendingKbps = null
            StreamBitrateSaveNavigation.IDLE,
            StreamBitrateSaveNavigation.WAITING,
            -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.ligase_stream_bitrate)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.ligase_stream_bitrate_presets))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.presets.forEach { preset ->
                        val selected =
                            !dialogState.customExpanded && selectedPresetId == preset.id
                        val presetPresentation =
                            streamBitratePresetPresentation(preset.id) ?: return@forEach
                        val description = stringResource(presetPresentation.description)
                        val recommended = preset.id == state.recommendedPreset.id
                        val accessibilityLabel = if (recommended) {
                            stringResource(
                                R.string.ligase_stream_bitrate_preset_accessibility_recommended,
                                preset.displayMbps,
                                description,
                            )
                        } else {
                            stringResource(
                                R.string.ligase_stream_bitrate_preset_accessibility,
                                preset.displayMbps,
                                description,
                            )
                        }
                        Card(
                            modifier = Modifier
                                .widthIn(min = 136.dp)
                                .selectable(
                                    selected = selected,
                                    enabled = !state.saving,
                                    role = Role.RadioButton,
                                ) {
                                    dialogState.selectedPresetName = preset.id.name
                                    dialogState.customExpanded = false
                                }
                                .semantics(mergeDescendants = true) {
                                    contentDescription = accessibilityLabel
                                },
                            colors = CardDefaults.cardColors(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                                Column(Modifier.clearAndSetSemantics {}) {
                                    Text("${preset.displayMbps} Mbps")
                                    Text(
                                        text = description,
                                        style = androidx.compose.material3.MaterialTheme.typography
                                            .labelMedium,
                                    )
                                    if (recommended) {
                                        Text(stringResource(R.string.ligase_stream_bitrate_recommended_short))
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.ligase_stream_bitrate_reference_notice),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = {
                        dialogState.customExpanded = true
                        dialogState.selectedPresetName = null
                    },
                    enabled = !state.saving,
                ) {
                    Text(stringResource(R.string.ligase_stream_bitrate_custom))
                }
                if (dialogState.customExpanded) {
                    val semanticsModifier = if (errorText != null) {
                        Modifier.semantics { error(errorText) }
                    } else {
                        Modifier
                    }
                    OutlinedTextField(
                        value = dialogState.customMbps,
                        onValueChange = { dialogState.customMbps = it },
                        modifier = semanticsModifier.fillMaxWidth(),
                        enabled = !state.saving,
                        label = { Text(stringResource(R.string.ligase_stream_bitrate_custom)) },
                        suffix = { Text("Mbps") },
                        supportingText = {
                            Text(
                                errorText ?: stringResource(
                                    R.string.ligase_stream_bitrate_custom_hint,
                                ),
                            )
                        },
                        isError = errorText != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                } else if (errorText != null) {
                    Box(Modifier.semantics { error(errorText) }) {
                        Text(errorText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation is StreamBitrateDraftValidation.Ready && !state.saving,
                onClick = {
                    val target = validation as? StreamBitrateDraftValidation.Ready
                        ?: return@TextButton
                    val samePreset = selectedPresetId != null &&
                        selectedPresetId == state.selectedPreset?.id
                    val sameCustom = selectedPresetId == null &&
                        state.selectedPreset == null &&
                        target.kbps == state.currentKbps
                    if (samePreset || sameCustom) {
                        dialogState.close()
                        return@TextButton
                    }
                    dialogState.pendingKbps = target.kbps
                    if (selectedPresetId != null) {
                        onPresetSelected(selectedPresetId)
                    } else {
                        onCustomSubmitted(dialogState.customMbps)
                    }
                },
            ) {
                Text(stringResource(R.string.ligase_save))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !state.saving,
                onClick = {
                    dialogState.close()
                },
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        modifier = Modifier.widthIn(max = 720.dp),
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

@Stable
class StreamBitrateDialogState internal constructor(
    visible: Boolean,
    selectedPresetName: String?,
    customMbps: String,
    customExpanded: Boolean,
    pendingKbps: Int?,
) {
    var visible by mutableStateOf(visible)
    var selectedPresetName by mutableStateOf(selectedPresetName)
    var customMbps by mutableStateOf(customMbps)
    var customExpanded by mutableStateOf(customExpanded)
    var pendingKbps by mutableStateOf(pendingKbps)

    fun open(state: StreamBitrateUiState) {
        selectedPresetName = state.selectedPreset?.id?.name
        customMbps = state.currentDisplayMbps
        customExpanded = state.selectedPreset == null
        pendingKbps = null
        visible = true
    }

    fun close() {
        visible = false
        pendingKbps = null
    }

    companion object {
        val Saver = listSaver<StreamBitrateDialogState, Any>(
            save = {
                listOf(
                    it.visible,
                    it.selectedPresetName.orEmpty(),
                    it.customMbps,
                    it.customExpanded,
                    it.pendingKbps ?: -1,
                )
            },
            restore = {
                StreamBitrateDialogState(
                    visible = it[0] as Boolean,
                    selectedPresetName = (it[1] as String).ifEmpty { null },
                    customMbps = it[2] as String,
                    customExpanded = it[3] as Boolean,
                    pendingKbps = (it[4] as Int).takeIf { value -> value >= 0 },
                )
            },
        )
    }
}

@Composable
internal fun rememberStreamBitrateDialogState(): StreamBitrateDialogState =
    rememberSaveable(saver = StreamBitrateDialogState.Saver) {
        StreamBitrateDialogState(
            visible = false,
            selectedPresetName = null,
            customMbps = "",
            customExpanded = false,
            pendingKbps = null,
        )
    }
