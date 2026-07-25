package com.limelight.ligase.feature.input.layout.v2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.feature.input.layout.v2.domain.DeviceClass
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2IssueCode
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiItem
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiState
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiVariant
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutLocalOrigin
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutOrientation
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutPreferenceActionState
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutPublication
import com.limelight.ligase.feature.input.layout.v2.presentation.LayoutCatalogV2AvailabilityPresentation
import com.limelight.ligase.feature.input.layout.v2.presentation.canSelectLayoutVariant
import com.limelight.ligase.feature.input.layout.v2.presentation.layoutCatalogV2Columns
import com.limelight.ligase.feature.input.layout.v2.presentation.toPresentation
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutCatalogV2Section(
    state: LayoutCatalogV2UiState,
    onRefresh: () -> Unit,
    onPreferredVariant: (String, Long, String) -> Unit,
    onClearPreference: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ligase_layout_v2_catalog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.ligase_layout_v2_catalog_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.refreshing) {
                CircularProgressIndicator()
            } else {
                OutlinedButton(onClick = onRefresh) {
                    Text(stringResource(R.string.ligase_retry))
                }
            }
        }
        preferenceActionMessage(state.preferenceAction)?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.issues.isNotEmpty()) {
            val firstIssue = state.issues.first()
            Text(
                text = stringResource(
                    R.string.ligase_layout_v2_issue_summary,
                    state.issues.size,
                    issueLabel(firstIssue.code),
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.items.isEmpty() && !state.refreshing) {
            Text(
                text = stringResource(R.string.ligase_layout_v2_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = layoutCatalogV2Columns(maxWidth.value.roundToInt())
                val gap = 14.dp
                val cardWidth = (maxWidth - gap * (columns - 1)) / columns
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    maxItemsInEachRow = columns,
                ) {
                    state.items.forEach { item ->
                        LayoutCatalogV2Card(
                            item = item,
                            onPreferredVariant = onPreferredVariant,
                            onClearPreference = onClearPreference,
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutCatalogV2Card(
    item: LayoutCatalogV2UiItem,
    onPreferredVariant: (String, Long, String) -> Unit,
    onClearPreference: (String) -> Unit,
    modifier: Modifier,
) {
    val presentation = item.toPresentation()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.ligase_layout_v2_revision,
                    presentation.revision,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.ligase_layout_v2_runtime_compatibility,
                    item.compatibility.minClientContractVersion,
                    item.compatibility.minLayoutRuntimeVersion,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CatalogTag(publicationLabel(item.publication))
                CatalogTag(originLabel(item.local.origin))
                CatalogTag(availabilityLabel(presentation.availability))
            }
            if (item.portableIdentities.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.ligase_layout_v2_portable_identity_count,
                        item.portableIdentities.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (presentation.retired) {
                Text(
                    text = stringResource(R.string.ligase_layout_v2_retired_message),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (presentation.needsVariantSelection) {
                Text(
                    text = stringResource(R.string.ligase_layout_v2_choose_variant),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item.variants.forEach { variant ->
                LayoutVariantRow(
                    item = item,
                    variant = variant,
                    selected = presentation.selectedVariantId == variant.summary.variantId,
                    onSelect = onPreferredVariant,
                )
            }
            if (item.preferredVariantId != null) {
                OutlinedButton(
                    onClick = { onClearPreference(item.layoutId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ligase_layout_v2_clear_variant))
                }
            }
            Text(
                text = stringResource(R.string.ligase_layout_v2_catalog_only),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CatalogTag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun LayoutVariantRow(
    item: LayoutCatalogV2UiItem,
    variant: LayoutCatalogV2UiVariant,
    selected: Boolean,
    onSelect: (String, Long, String) -> Unit,
) {
    val enabled = canSelectLayoutVariant(item, variant)
    val summary = variantSummary(variant)
    val accessibility = stringResource(
        if (selected) {
            R.string.ligase_layout_v2_variant_accessibility_selected
        } else {
            R.string.ligase_layout_v2_variant_accessibility
        },
        variant.summary.variantId,
        summary,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibility
                this.selected = selected
            },
        onClick = {
            if (enabled) {
                onSelect(item.layoutId, item.revision, variant.summary.variantId)
            }
        },
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Column(Modifier.clearAndSetSemantics {}) {
                Text(variant.summary.variantId, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall)
                if (!variant.summary.eligible) {
                    Text(
                        text = stringResource(R.string.ligase_layout_v2_variant_ineligible),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun variantSummary(variant: LayoutCatalogV2UiVariant): String {
    val phone = stringResource(R.string.ligase_layout_v2_phone)
    val tablet = stringResource(R.string.ligase_layout_v2_tablet)
    val portrait = stringResource(R.string.ligase_layout_v2_portrait)
    val landscape = stringResource(R.string.ligase_layout_v2_landscape)
    val devices = variant.deviceClasses.joinToString("/") {
        if (it == DeviceClass.PHONE) phone else tablet
    }
    val orientations = variant.orientations.joinToString("/") {
        if (it == LayoutOrientation.PORTRAIT) portrait else landscape
    }
    val details = variant.compatibility
    val reference = details.designReferenceHint
    val referenceText = when {
        reference.resolution != null && reference.densityDpi != null ->
            stringResource(
                R.string.ligase_layout_v2_design_reference_full,
                reference.resolution.width,
                reference.resolution.height,
                reference.densityDpi,
            )
        reference.resolution != null ->
            stringResource(
                R.string.ligase_layout_v2_design_reference_resolution,
                reference.resolution.width,
                reference.resolution.height,
            )
        reference.densityDpi != null ->
            stringResource(
                R.string.ligase_layout_v2_design_reference_density,
                reference.densityDpi,
            )
        else -> stringResource(R.string.ligase_layout_v2_no_design_reference)
    }
    return stringResource(
        R.string.ligase_layout_v2_variant_summary,
        devices,
        orientations,
        details.canvas.width,
        details.canvas.height,
        details.minShortestSideDp,
        details.minTouchTargetDp,
        if (details.safeAreaPolicy ==
            com.limelight.ligase.feature.input.layout.v2.domain.SafeAreaPolicy.VIDEO_CONTENT
        ) {
            stringResource(R.string.ligase_layout_v2_safe_area_video)
        } else {
            stringResource(R.string.ligase_layout_v2_safe_area_system)
        },
        referenceText,
    )
}

@Composable
private fun publicationLabel(value: LayoutPublication): String = stringResource(
    when (value) {
        LayoutPublication.DRAFT -> R.string.ligase_layout_v2_publication_draft
        LayoutPublication.PUBLISHED -> R.string.ligase_layout_v2_publication_published
        LayoutPublication.RETIRED -> R.string.ligase_layout_v2_publication_retired
    },
)

@Composable
private fun originLabel(value: LayoutLocalOrigin): String = stringResource(
    when (value) {
        LayoutLocalOrigin.PACKAGED_BUILT_IN -> R.string.ligase_layout_v2_origin_built_in
        LayoutLocalOrigin.LOCAL_COPY -> R.string.ligase_layout_v2_origin_local
        LayoutLocalOrigin.HOST_CATALOG -> R.string.ligase_layout_v2_origin_host
    },
)

@Composable
private fun availabilityLabel(
    value: LayoutCatalogV2AvailabilityPresentation,
): String = stringResource(
    when (value) {
        LayoutCatalogV2AvailabilityPresentation.READY ->
            R.string.ligase_layout_v2_availability_ready
        LayoutCatalogV2AvailabilityPresentation.VERIFYING ->
            R.string.ligase_layout_v2_availability_verifying
        LayoutCatalogV2AvailabilityPresentation.NOT_LOCAL ->
            R.string.ligase_layout_v2_availability_not_local
        LayoutCatalogV2AvailabilityPresentation.INVALID ->
            R.string.ligase_layout_v2_availability_invalid
    },
)

@Composable
private fun issueLabel(value: LayoutCatalogV2IssueCode): String = stringResource(
    when (value) {
        LayoutCatalogV2IssueCode.INVALID_DESCRIPTOR ->
            R.string.ligase_layout_v2_issue_invalid_descriptor
        LayoutCatalogV2IssueCode.CONTENT_MISSING ->
            R.string.ligase_layout_v2_issue_content_missing
        LayoutCatalogV2IssueCode.CONTENT_CORRUPT ->
            R.string.ligase_layout_v2_issue_content_corrupt
        LayoutCatalogV2IssueCode.CONTENT_HASH_MISMATCH ->
            R.string.ligase_layout_v2_issue_hash_mismatch
        LayoutCatalogV2IssueCode.CONTENT_ALIGNMENT_INVALID ->
            R.string.ligase_layout_v2_issue_alignment
        LayoutCatalogV2IssueCode.INVALID_STORED_PREFERENCE ->
            R.string.ligase_layout_v2_issue_preference_invalid
        LayoutCatalogV2IssueCode.STALE_STORED_PREFERENCE ->
            R.string.ligase_layout_v2_issue_preference_stale
        LayoutCatalogV2IssueCode.STORAGE_FAILURE ->
            R.string.ligase_layout_v2_issue_storage
    },
)

@Composable
private fun preferenceActionMessage(state: LayoutPreferenceActionState): String? =
    when (state) {
        LayoutPreferenceActionState.Idle -> null
        is LayoutPreferenceActionState.Saved ->
            stringResource(R.string.ligase_layout_v2_preference_saved)
        is LayoutPreferenceActionState.Cleared ->
            stringResource(R.string.ligase_layout_v2_preference_cleared)
        is LayoutPreferenceActionState.Failed ->
            stringResource(R.string.ligase_layout_v2_preference_failed)
    }
