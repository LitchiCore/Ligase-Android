package com.limelight.ligase.feature.input.layout.v2.application

import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.layout.LayoutDescriptorV1

object LayoutPreferenceV2Action {
    fun select(
        descriptor: LayoutDescriptorV1,
        content: VerifiedTouchLayoutV2Content?,
        local: LayoutCatalogV2LocalState,
        context: LayoutCatalogV2Context,
        variantId: String,
    ): LayoutPreferenceV2Result {
        val projectionResult = LayoutCatalogV2Projector.project(
            descriptor = descriptor,
            content = content,
            local = local,
            context = context,
            preferredVariantId = null,
        )
        val projection = when (projectionResult) {
            is LayoutCatalogV2ProjectionResult.Projected -> projectionResult.item
            is LayoutCatalogV2ProjectionResult.Rejected ->
                return LayoutPreferenceV2Result.Rejected(
                    LayoutPreferenceV2Rejection.INVALID_DESCRIPTOR,
                )
        }
        return when (projection.selection.code) {
            LayoutVariantSelectionCode.CONTENT_NOT_READY ->
                LayoutPreferenceV2Result.Rejected(LayoutPreferenceV2Rejection.CONTENT_NOT_READY)
            LayoutVariantSelectionCode.INVALID_ALIGNMENT ->
                LayoutPreferenceV2Result.Rejected(LayoutPreferenceV2Rejection.INVALID_ALIGNMENT)
            else -> {
                val variant = projection.variants.singleOrNull { it.variantId == variantId }
                    ?: return LayoutPreferenceV2Result.Rejected(
                        LayoutPreferenceV2Rejection.UNKNOWN_VARIANT,
                    )
                if (!variant.eligible) {
                    LayoutPreferenceV2Result.Rejected(LayoutPreferenceV2Rejection.INELIGIBLE_VARIANT)
                } else {
                    LayoutPreferenceV2Result.Accepted(
                        projection.layoutId,
                        projection.revision,
                        variant.variantId,
                    )
                }
            }
        }
    }
}
