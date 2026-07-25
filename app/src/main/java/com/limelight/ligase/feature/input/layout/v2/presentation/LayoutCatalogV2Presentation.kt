package com.limelight.ligase.feature.input.layout.v2.presentation

import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiItem
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiVariant
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutLocalAvailability
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutPublication
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutVariantSelectionCode

enum class LayoutCatalogV2AvailabilityPresentation {
    READY,
    VERIFYING,
    NOT_LOCAL,
    INVALID,
}

data class LayoutCatalogV2ItemPresentation(
    val title: String,
    val revision: Long,
    val availability: LayoutCatalogV2AvailabilityPresentation,
    val selectedVariantId: String?,
    val needsVariantSelection: Boolean,
    val retired: Boolean,
)

fun LayoutCatalogV2UiItem.toPresentation(): LayoutCatalogV2ItemPresentation =
    LayoutCatalogV2ItemPresentation(
        title = displayName?.takeIf(String::isNotBlank) ?: layoutId,
        revision = revision,
        availability = when (local.availability) {
            LayoutLocalAvailability.READY -> LayoutCatalogV2AvailabilityPresentation.READY
            LayoutLocalAvailability.VERIFYING ->
                LayoutCatalogV2AvailabilityPresentation.VERIFYING
            LayoutLocalAvailability.NOT_LOCAL ->
                LayoutCatalogV2AvailabilityPresentation.NOT_LOCAL
            LayoutLocalAvailability.INVALID ->
                LayoutCatalogV2AvailabilityPresentation.INVALID
        },
        selectedVariantId = preferredVariantId ?: selection.variantId,
        needsVariantSelection =
            selection.code == LayoutVariantSelectionCode.NEEDS_VARIANT_SELECTION,
        retired = publication == LayoutPublication.RETIRED,
    )

fun canSelectLayoutVariant(
    item: LayoutCatalogV2UiItem,
    variant: LayoutCatalogV2UiVariant,
): Boolean =
    item.contentVerified &&
        item.local.availability == LayoutLocalAvailability.READY &&
        item.publication != LayoutPublication.RETIRED &&
        variant.summary.eligible

fun layoutCatalogV2Columns(availableWidthDp: Int): Int = when {
    availableWidthDp >= 1_200 -> 3
    availableWidthDp >= 720 -> 2
    else -> 1
}

