package com.limelight.ligase.feature.library.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.feature.stream.domain.StreamCapabilityReason
import com.limelight.ligase.feature.stream.domain.StreamDisplayPolicy
import com.limelight.ligase.feature.stream.domain.StreamResolutionPresetId

@Composable
internal fun StreamingResolutionEditor(
    request: StreamingResolutionEditorRequest,
    draft: StreamingResolutionDraft,
    invalid: Boolean,
    onDraftChanged: (StreamingResolutionDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (request.allowUseGlobal) {
                ResolutionChoice(
                    selected = draft.useGlobal,
                    label = stringResource(R.string.ligase_resolution_use_global),
                    onClick = {
                        onDraftChanged(draft.copy(useGlobal = true))
                    },
                )
                ResolutionChoice(
                    selected = !draft.useGlobal,
                    label = stringResource(R.string.ligase_resolution_custom),
                    onClick = {
                        onDraftChanged(draft.copy(useGlobal = false))
                    },
                )
            }
            StreamDisplayPolicy.resolutionPresets(request.deviceCapabilities).forEach { preset ->
                val resolution = preset.resolution
                val available = resolution != null && preset.reason == StreamCapabilityReason.AVAILABLE
                ResolutionChoice(
                    selected = !draft.useGlobal && resolution != null &&
                        draft.widthText == resolution.width.toString() &&
                        draft.heightText == resolution.height.toString(),
                    enabled = available,
                    label = buildString {
                        append(stringResource(preset.id.labelResource()))
                        resolution?.let { append(" · ${it.label}") }
                        if (!available) {
                            append(" · ")
                            append(
                                stringResource(
                                    if (preset.reason == StreamCapabilityReason.EXCEEDS_DEVICE_DISPLAY) {
                                        R.string.ligase_stream_exceeds_display
                                    } else {
                                        R.string.ligase_stream_capability_unknown
                                    },
                                ),
                            )
                        }
                    },
                    onClick = {
                        if (resolution != null) {
                            onDraftChanged(
                                draft.copy(
                                    widthText = resolution.width.toString(),
                                    heightText = resolution.height.toString(),
                                    useGlobal = false,
                                ),
                            )
                        }
                    },
                )
            }
            OutlinedTextField(
                value = draft.widthText,
                onValueChange = { onDraftChanged(draft.copy(widthText = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !request.allowUseGlobal || !draft.useGlobal,
                label = { Text(stringResource(R.string.ligase_resolution_width)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.ligase_resolution_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = draft.heightText,
                onValueChange = { onDraftChanged(draft.copy(heightText = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !request.allowUseGlobal || !draft.useGlobal,
                label = { Text(stringResource(R.string.ligase_resolution_height)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.ligase_resolution_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(onClick = onSave) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun ResolutionChoice(
    selected: Boolean,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
        )
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@StringRes
private fun StreamResolutionPresetId.labelResource(): Int = when (this) {
    StreamResolutionPresetId.DEVICE_MAX -> R.string.ligase_resolution_device_max
    StreamResolutionPresetId.BEST_16_9 -> R.string.ligase_resolution_best_16_9
    StreamResolutionPresetId.HD_720 -> R.string.ligase_resolution_720p
    StreamResolutionPresetId.FULL_HD_1080 -> R.string.ligase_resolution_1080p
    StreamResolutionPresetId.QHD_1440 -> R.string.ligase_resolution_1440p
    StreamResolutionPresetId.UHD_2160 -> R.string.ligase_resolution_2160p
}
