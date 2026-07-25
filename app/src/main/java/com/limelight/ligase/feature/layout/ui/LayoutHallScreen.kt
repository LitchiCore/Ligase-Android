package com.limelight.ligase.feature.layout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.LigasePageScaffold
import com.limelight.ligase.feature.layout.domain.LayoutCatalogItem
import com.limelight.ligase.feature.layout.domain.LayoutCatalogSource
import com.limelight.ligase.feature.layout.domain.LayoutCatalogUiState
import com.limelight.ligase.feature.layout.domain.LayoutEditorError
import com.limelight.ligase.feature.layout.presentation.layoutHallColumns
import com.limelight.ligase.feature.input.layout.v3.editor.RecoverableDraftSummary as RecoverableV3DraftSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun LayoutHallScreen(
    state: LayoutCatalogUiState,
    actionError: LayoutEditorError? = null,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onPreview: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCreateCopy: (String) -> Unit,
    recoverableV3Drafts: List<RecoverableV3DraftSummary> = emptyList(),
    onV3CreateBlank: (String?) -> Unit = {},
    onV3ResumeRecovery: (String) -> Unit = {},
    onV3DiscardRecovery: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    LigasePageScaffold(
        title = stringResource(R.string.ligase_layout_hall_title),
        onBack = onBack,
    ) { pageModifier ->
        BoxWithConstraints(pageModifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            when {
                state.loading && state.items.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.items.isEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item {
                            LayoutV3CreatorSection(
                                recoverableDrafts = recoverableV3Drafts,
                                onCreateBlank = onV3CreateBlank,
                                onResumeRecovery = onV3ResumeRecovery,
                                onDiscardRecovery = onV3DiscardRecovery,
                            )
                        }
                        item {
                            LayoutHallEmptyState(
                                error = state.error,
                                onRefresh = onRefresh,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                wide -> {
                    val columns = layoutHallColumns(maxWidth.value)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LayoutV3CreatorSection(
                                recoverableDrafts = recoverableV3Drafts,
                                onCreateBlank = onV3CreateBlank,
                                onResumeRecovery = onV3ResumeRecovery,
                                onDiscardRecovery = onV3DiscardRecovery,
                            )
                        }
                        actionError?.let { error ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LayoutErrorBanner(error)
                            }
                        }
                        items(state.items, key = LayoutCatalogItem::layoutId) { layout ->
                            LayoutCatalogCard(
                                layout = layout,
                                selected = layout.layoutId == state.selectedLayoutId,
                                onSelect = { onSelect(layout.layoutId) },
                                onPreview = { onPreview(layout.layoutId) },
                                onEdit = { onEdit(layout.layoutId) },
                                onCreateCopy = { onCreateCopy(layout.layoutId) },
                                modifier = Modifier.widthIn(max = 560.dp),
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            LayoutV3CreatorSection(
                                recoverableDrafts = recoverableV3Drafts,
                                onCreateBlank = onV3CreateBlank,
                                onResumeRecovery = onV3ResumeRecovery,
                                onDiscardRecovery = onV3DiscardRecovery,
                            )
                        }
                        actionError?.let { error ->
                            item { LayoutErrorBanner(error) }
                        }
                        items(
                            count = state.items.size,
                            key = { state.items[it].layoutId },
                        ) { index ->
                            val layout = state.items[index]
                            LayoutCatalogCard(
                                layout = layout,
                                selected = layout.layoutId == state.selectedLayoutId,
                                onSelect = { onSelect(layout.layoutId) },
                                onPreview = { onPreview(layout.layoutId) },
                                onEdit = { onEdit(layout.layoutId) },
                                onCreateCopy = { onCreateCopy(layout.layoutId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutV3CreatorSection(
    recoverableDrafts: List<RecoverableV3DraftSummary>,
    onCreateBlank: (String?) -> Unit,
    onResumeRecovery: (String) -> Unit,
    onDiscardRecovery: (String) -> Unit,
) {
    var discardTarget by remember { mutableStateOf<RecoverableV3DraftSummary?>(null) }
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

@Composable
private fun LayoutErrorBanner(error: LayoutEditorError) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = layoutErrorMessage(error),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun LayoutCatalogCard(
    layout: LayoutCatalogItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onCreateCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(layout.previewAspectRatio.coerceIn(0.75f, 2.4f)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = layout.controlCount.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.ligase_layout_control_count))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = layout.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            if (layout.source == LayoutCatalogSource.BUILT_IN) {
                                R.string.ligase_layout_source_built_in
                            } else {
                                R.string.ligase_layout_source_local
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Text(
                        text = stringResource(R.string.ligase_layout_selected),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            OutlinedButton(
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ligase_layout_preview))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = if (layout.editable) onEdit else onCreateCopy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (layout.editable) {
                                R.string.ligase_layout_edit
                            } else {
                                R.string.ligase_layout_create_copy
                            },
                        ),
                    )
                }
                Button(
                    onClick = onSelect,
                    enabled = !selected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (selected) {
                                R.string.ligase_layout_in_use
                            } else {
                                R.string.ligase_layout_use
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutHallEmptyState(
    error: LayoutEditorError?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.ligase_layout_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (error == null) {
                stringResource(R.string.ligase_layout_empty_message)
            } else {
                layoutErrorMessage(error)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRefresh) {
            Text(stringResource(R.string.ligase_retry))
        }
    }
}

@Composable
internal fun layoutErrorMessage(error: LayoutEditorError): String = stringResource(
    when (error) {
        LayoutEditorError.LAYOUT_NOT_FOUND -> R.string.ligase_layout_error_not_found
        LayoutEditorError.INVALID_LAYOUT_ID -> R.string.ligase_layout_error_invalid_id
        LayoutEditorError.UNSUPPORTED_VALUE_TYPE -> R.string.ligase_layout_error_unsupported
        LayoutEditorError.MALFORMED_LAYOUT -> R.string.ligase_layout_error_malformed
        LayoutEditorError.UNKNOWN_CONTROL_MUTATION -> R.string.ligase_layout_error_unknown
        LayoutEditorError.INVALID_BOUNDS -> R.string.ligase_layout_error_bounds
        LayoutEditorError.DUPLICATE_ELEMENT_ID -> R.string.ligase_layout_error_duplicate
        LayoutEditorError.SAVE_FAILED -> R.string.ligase_layout_error_save
        LayoutEditorError.NO_ACTIVE_DRAFT -> R.string.ligase_layout_error_no_draft
    },
)
