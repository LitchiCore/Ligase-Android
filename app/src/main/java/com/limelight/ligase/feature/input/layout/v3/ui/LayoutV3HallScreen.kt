package com.limelight.ligase.feature.input.layout.v3.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.LigasePageScaffold
import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3CommittedLayoutSummary
import com.limelight.ligase.feature.input.layout.v3.editor.RecoverableDraftSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun LayoutV3HallScreen(
    onBack: () -> Unit,
    recoverableV3Drafts: List<RecoverableDraftSummary> = emptyList(),
    committedV3Layouts: List<LayoutV3CommittedLayoutSummary> = emptyList(),
    onV3CreateBlank: (String?) -> Unit = {},
    onV3ResumeRecovery: (String) -> Unit = {},
    onV3DiscardRecovery: (String) -> Unit = {},
    onV3OpenCommitted: (String, Long, String) -> Unit = { _, _, _ -> },
) {
    BackHandler(onBack = onBack)
    LigasePageScaffold(
        title = stringResource(R.string.ligase_layout_hall_title),
        onBack = onBack,
    ) { pageModifier ->
        LazyColumn(
            modifier = pageModifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                LayoutV3CreatorSection(
                    recoverableDrafts = recoverableV3Drafts,
                    committedLayouts = committedV3Layouts,
                    onCreateBlank = onV3CreateBlank,
                    onResumeRecovery = onV3ResumeRecovery,
                    onDiscardRecovery = onV3DiscardRecovery,
                    onOpenCommitted = onV3OpenCommitted,
                )
            }
        }
    }
}

@Composable
private fun LayoutV3CreatorSection(
    recoverableDrafts: List<RecoverableDraftSummary>,
    committedLayouts: List<LayoutV3CommittedLayoutSummary>,
    onCreateBlank: (String?) -> Unit,
    onResumeRecovery: (String) -> Unit,
    onDiscardRecovery: (String) -> Unit,
    onOpenCommitted: (String, Long, String) -> Unit,
) {
    var discardTarget by remember { mutableStateOf<RecoverableDraftSummary?>(null) }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.ligase_layout_v3_creator_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.ligase_layout_v3_creator_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onCreateBlank(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ligase_layout_v3_create_blank))
            }
            if (recoverableDrafts.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.ligase_layout_v3_recovery_title),
                    fontWeight = FontWeight.SemiBold,
                )
                recoverableDrafts.forEach { draft ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(draft.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT,
                            ).format(Date(draft.updatedAtEpochMillis)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onResumeRecovery(draft.draftId) }) {
                                Text(stringResource(R.string.ligase_layout_v3_resume))
                            }
                            OutlinedButton(onClick = { discardTarget = draft }) {
                                Text(stringResource(R.string.ligase_layout_v3_discard))
                            }
                        }
                    }
                }
            }
            if (committedLayouts.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.ligase_layout_v3_local_copies_title),
                    fontWeight = FontWeight.SemiBold,
                )
                committedLayouts.forEach { layout ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(layout.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(
                                R.string.ligase_layout_v3_local_copy_verified,
                                layout.revision,
                                layout.variants.sumOf { it.controlCount },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.ligase_layout_v3_runtime_not_ready),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        layout.variants.firstOrNull()?.let { variant ->
                            OutlinedButton(
                                onClick = {
                                    onOpenCommitted(
                                        layout.layoutId,
                                        layout.revision,
                                        variant.variantId,
                                    )
                                },
                            ) {
                                Text(stringResource(R.string.ligase_layout_v3_continue_editing))
                            }
                        }
                    }
                }
            }
        }
    }
    discardTarget?.let { draft ->
        AlertDialog(
            onDismissRequest = { discardTarget = null },
            title = { Text(stringResource(R.string.ligase_layout_v3_discard_recovery_title)) },
            text = { Text(stringResource(R.string.ligase_layout_v3_discard_recovery_message)) },
            confirmButton = {
                TextButton(onClick = {
                    discardTarget = null
                    onDiscardRecovery(draft.draftId)
                }) { Text(stringResource(R.string.ligase_layout_v3_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { discardTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
