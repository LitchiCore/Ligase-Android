package com.limelight.ligase.feature.library.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.library.ManualLibraryOrderDraft

@Composable
fun ManualLibraryEditBar(
    draft: ManualLibraryOrderDraft,
    saving: Boolean,
    editingEnabled: Boolean,
    errorMessage: Int?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val compactLandscape = LocalConfiguration.current.screenWidthDp >= 600 &&
        LocalConfiguration.current.screenHeightDp < 600
    val statusText = stringResource(
        if (draft.isDirty) {
            R.string.ligase_manual_sort_unsaved
        } else {
            R.string.ligase_manual_sort_summary
        },
    )
    val actions = manualLibraryEditActions(
        isDirty = draft.isDirty,
        editingEnabled = editingEnabled,
        saving = saving,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        if (compactLandscape && errorMessage == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.weight(1f),
                    color = if (draft.isDirty) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (draft.isDirty) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onCancel,
                    enabled = actions.cancelEnabled,
                ) {
                    Text(stringResource(R.string.ligase_manual_sort_cancel))
                }
                androidx.compose.material3.Button(
                    onClick = onSave,
                    enabled = actions.saveEnabled,
                ) {
                    Text(
                        stringResource(
                            if (saving) {
                                R.string.ligase_manual_sort_saving
                            } else {
                                R.string.ligase_manual_sort_save
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = statusText,
                    color = if (draft.isDirty) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (draft.isDirty) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                )
                if (errorMessage != null) {
                    Text(
                        text = stringResource(errorMessage),
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onCancel,
                        enabled = actions.cancelEnabled,
                        modifier = Modifier.weight(0.8f),
                    ) {
                        Text(stringResource(R.string.ligase_manual_sort_cancel))
                    }
                    androidx.compose.material3.Button(
                        onClick = onSave,
                        enabled = actions.saveEnabled,
                        modifier = Modifier.weight(1.4f),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(
                            stringResource(
                                if (saving) {
                                    R.string.ligase_manual_sort_saving
                                } else {
                                    R.string.ligase_manual_sort_save
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
