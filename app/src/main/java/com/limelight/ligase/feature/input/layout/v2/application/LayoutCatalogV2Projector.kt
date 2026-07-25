package com.limelight.ligase.feature.input.layout.v2.application

import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Codec
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Exception
import com.limelight.ligase.layout.LayoutContractV1Validator
import com.limelight.ligase.layout.LayoutDescriptorV1

object TouchLayoutV2ContentVerifier {
    fun verify(raw: ByteArray): LayoutContentVerificationResult =
        try {
            LayoutContentVerificationResult.Verified(
                VerifiedTouchLayoutV2Content(TouchLayoutV2Codec.decode(raw)),
            )
        } catch (error: TouchLayoutV2Exception) {
            LayoutContentVerificationResult.Rejected(error.code)
        }
}

object LayoutCatalogV2Projector {
    fun contentAligned(
        descriptor: LayoutDescriptorV1,
        content: VerifiedTouchLayoutV2Content,
    ): Boolean {
        if (LayoutContractV1Validator.validateDescriptor(descriptor) != null) return false
        return try {
            TouchLayoutV2Validator.validateDescriptorAlignment(
                content.document,
                descriptorProjection(descriptor),
            )
            true
        } catch (_: TouchLayoutV2Exception) {
            false
        }
    }

    fun project(
        descriptor: LayoutDescriptorV1,
        content: VerifiedTouchLayoutV2Content?,
        local: LayoutCatalogV2LocalState,
        context: LayoutCatalogV2Context?,
        preferredVariantId: String?,
    ): LayoutCatalogV2ProjectionResult {
        if (LayoutContractV1Validator.validateDescriptor(descriptor) != null) {
            return LayoutCatalogV2ProjectionResult.Rejected(
                LayoutCatalogV2ProjectionRejection.INVALID_DESCRIPTOR,
            )
        }
        return LayoutCatalogV2ProjectionResult.Projected(
            projectValidated(descriptor, content, local, context, preferredVariantId),
        )
    }

    private fun projectValidated(
        descriptor: LayoutDescriptorV1,
        content: VerifiedTouchLayoutV2Content?,
        local: LayoutCatalogV2LocalState,
        context: LayoutCatalogV2Context?,
        preferredVariantId: String?,
    ): LayoutCatalogV2Item {
        val publication = when (descriptor.publicationStatus) {
            "draft" -> LayoutPublication.DRAFT
            "published" -> LayoutPublication.PUBLISHED
            "retired" -> LayoutPublication.RETIRED
            else -> error("validated descriptor has unknown publication")
        }
        val base = LayoutCatalogV2Item(
            layoutId = descriptor.layoutId,
            revision = descriptor.revision,
            publication = publication,
            compatibility = descriptor.compatibility,
            portableIdentities = descriptor.portableIdentities.toList(),
            local = local,
            contentHash = content?.document?.contentHash,
            variants = emptyList(),
            preferredVariantId = preferredVariantId,
            selection = LayoutVariantSelection(LayoutVariantSelectionCode.CONTENT_NOT_READY),
        )
        if (local.availability != LayoutLocalAvailability.READY || content == null) return base

        val touchDescriptor = descriptorProjection(descriptor)
        try {
            TouchLayoutV2Validator.validateDescriptorAlignment(content.document, touchDescriptor)
        } catch (_: TouchLayoutV2Exception) {
            return base.copy(
                selection = LayoutVariantSelection(LayoutVariantSelectionCode.INVALID_ALIGNMENT),
            )
        }

        val descriptorById = touchDescriptor.variants.associateBy { it.variantId }
        if (context == null) {
            return base.copy(
                variants = content.document.variants.map { variant ->
                    LayoutCatalogV2VariantSummary(
                        variantId = variant.variantId,
                        descriptorEligible = false,
                        eligible = false,
                        ineligibilityReasons = emptySet(),
                        compatibilityHints = emptySet(),
                        contextState = LayoutCatalogV2ContextState.NO_VIDEO_VIEWPORT,
                    )
                },
                selection = LayoutVariantSelection(
                    LayoutVariantSelectionCode.WAITING_FOR_CONTEXT_VALIDATION,
                ),
            )
        }
        val variants = content.document.variants.map { variant ->
            val authority = descriptorById.getValue(variant.variantId)
            val descriptorEligible =
                context.deviceClass in authority.deviceClasses &&
                    context.orientation in authority.orientations
            val hints = compatibilityHints(variant.recommendation, context)
            val reasons = buildSet {
                if (publication == LayoutPublication.RETIRED) add(LayoutVariantIneligibilityReason.RETIRED)
                if (
                    context.clientContractVersion <
                    descriptor.compatibility.minClientContractVersion
                ) {
                    add(LayoutVariantIneligibilityReason.CLIENT_CONTRACT_VERSION)
                }
                if (
                    context.layoutRuntimeVersion <
                    descriptor.compatibility.minLayoutRuntimeVersion
                ) {
                    add(LayoutVariantIneligibilityReason.LAYOUT_RUNTIME_VERSION)
                }
                if (context.deviceClass !in authority.deviceClasses) {
                    add(LayoutVariantIneligibilityReason.DEVICE_CLASS)
                }
                if (context.orientation !in authority.orientations) {
                    add(LayoutVariantIneligibilityReason.ORIENTATION)
                }
                if (
                    LayoutVariantCompatibilityHint.ASPECT_RATIO_BELOW_MINIMUM in hints ||
                    LayoutVariantCompatibilityHint.ASPECT_RATIO_ABOVE_MAXIMUM in hints
                ) {
                    add(LayoutVariantIneligibilityReason.ASPECT_RATIO)
                }
                if (LayoutVariantCompatibilityHint.SHORTEST_SIDE_BELOW_MINIMUM in hints) {
                    add(LayoutVariantIneligibilityReason.SHORTEST_SIDE)
                }
                if (LayoutVariantCompatibilityHint.TOUCH_TARGET_BELOW_MINIMUM in hints) {
                    add(LayoutVariantIneligibilityReason.TOUCH_TARGET)
                }
            }
            LayoutCatalogV2VariantSummary(
                variantId = variant.variantId,
                descriptorEligible = descriptorEligible,
                eligible = reasons.isEmpty(),
                ineligibilityReasons = reasons,
                compatibilityHints = hints,
            )
        }
        val eligible = variants.filter { it.eligible }
        val preferred = eligible.singleOrNull { it.variantId == preferredVariantId }
        val selection = when {
            preferred != null -> LayoutVariantSelection(
                LayoutVariantSelectionCode.SELECTED,
                preferred.variantId,
                LayoutVariantSelectionSource.EXPLICIT_PREFERENCE,
            )
            eligible.size == 1 -> LayoutVariantSelection(
                LayoutVariantSelectionCode.SELECTED,
                eligible.single().variantId,
                LayoutVariantSelectionSource.ONLY_ELIGIBLE,
            )
            eligible.isEmpty() -> LayoutVariantSelection(LayoutVariantSelectionCode.NO_ELIGIBLE_VARIANT)
            else -> LayoutVariantSelection(LayoutVariantSelectionCode.NEEDS_VARIANT_SELECTION)
        }
        return base.copy(variants = variants, selection = selection)
    }

    private fun compatibilityHints(
        recommendation: LayoutRecommendation,
        context: LayoutCatalogV2Context,
    ): Set<LayoutVariantCompatibilityHint> = buildSet {
        if (compare(context.videoAspectRatio, recommendation.minAspectRatio) < 0) {
            add(LayoutVariantCompatibilityHint.ASPECT_RATIO_BELOW_MINIMUM)
        }
        if (compare(context.videoAspectRatio, recommendation.maxAspectRatio) > 0) {
            add(LayoutVariantCompatibilityHint.ASPECT_RATIO_ABOVE_MAXIMUM)
        }
        if (context.shortestSideDp < recommendation.minShortestSideDp) {
            add(LayoutVariantCompatibilityHint.SHORTEST_SIDE_BELOW_MINIMUM)
        }
        if (context.touchTargetDp < recommendation.minTouchTargetDp) {
            add(LayoutVariantCompatibilityHint.TOUCH_TARGET_BELOW_MINIMUM)
        }
        if (recommendation.referenceDensityDpi != null) {
            add(LayoutVariantCompatibilityHint.REFERENCE_DENSITY_DIAGNOSTIC_ONLY)
        }
        add(LayoutVariantCompatibilityHint.REFERENCE_RESOLUTION_DIAGNOSTIC_ONLY)
    }

    private fun compare(left: AspectRatio, right: AspectRatio): Int =
        (left.numerator.toLong() * right.denominator)
            .compareTo(right.numerator.toLong() * left.denominator)

    private fun deviceClass(value: String): DeviceClass = when (value) {
        "phone" -> DeviceClass.PHONE
        "tablet" -> DeviceClass.TABLET
        else -> error("validated descriptor has unknown device class")
    }

    private fun orientation(value: String): LayoutOrientation = when (value) {
        "portrait" -> LayoutOrientation.PORTRAIT
        "landscape" -> LayoutOrientation.LANDSCAPE
        else -> error("validated descriptor has unknown orientation")
    }

    private fun descriptorProjection(descriptor: LayoutDescriptorV1) =
        LayoutDescriptorProjection(
            descriptor.layoutId,
            descriptor.revision,
            descriptor.variants.filter { it.inputProfile == "touch" }.map { variant ->
                DescriptorVariantProjection(
                    variant.variantId,
                    variant.deviceClasses.map(::deviceClass),
                    variant.orientations.map(::orientation),
                )
            },
        )
}
