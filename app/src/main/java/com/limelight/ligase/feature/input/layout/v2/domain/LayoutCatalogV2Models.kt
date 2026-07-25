package com.limelight.ligase.feature.input.layout.v2.domain

import com.limelight.ligase.layout.LayoutCompatibilityV1
import com.limelight.ligase.layout.PortableGameIdentityV1

enum class LayoutPublication { DRAFT, PUBLISHED, RETIRED }
enum class LayoutLocalOrigin { PACKAGED_BUILT_IN, LOCAL_COPY, HOST_CATALOG }
enum class LayoutLocalAvailability { NOT_LOCAL, VERIFYING, READY, INVALID }
enum class LayoutWorkspaceState { NONE, DRAFT }

data class LayoutCatalogV2LocalState(
    val origin: LayoutLocalOrigin,
    val availability: LayoutLocalAvailability,
    val workspace: LayoutWorkspaceState,
)

data class LayoutCatalogV2Context(
    val clientContractVersion: Int,
    val layoutRuntimeVersion: Int,
    val deviceClass: DeviceClass,
    val orientation: LayoutOrientation,
    val videoAspectRatio: AspectRatio,
    val shortestSideDp: Int,
    val touchTargetDp: Int,
)

enum class LayoutVariantCompatibilityHint {
    ASPECT_RATIO_BELOW_MINIMUM,
    ASPECT_RATIO_ABOVE_MAXIMUM,
    SHORTEST_SIDE_BELOW_MINIMUM,
    TOUCH_TARGET_BELOW_MINIMUM,
    REFERENCE_DENSITY_DIAGNOSTIC_ONLY,
    REFERENCE_RESOLUTION_DIAGNOSTIC_ONLY,
}

enum class LayoutVariantIneligibilityReason {
    RETIRED,
    CLIENT_CONTRACT_VERSION,
    LAYOUT_RUNTIME_VERSION,
    DEVICE_CLASS,
    ORIENTATION,
    ASPECT_RATIO,
    SHORTEST_SIDE,
    TOUCH_TARGET,
}

data class LayoutCatalogV2VariantSummary(
    val variantId: String,
    val descriptorEligible: Boolean,
    val eligible: Boolean,
    val ineligibilityReasons: Set<LayoutVariantIneligibilityReason>,
    val compatibilityHints: Set<LayoutVariantCompatibilityHint>,
)

enum class LayoutVariantSelectionSource { EXPLICIT_PREFERENCE, ONLY_ELIGIBLE }
enum class LayoutVariantSelectionCode {
    SELECTED,
    NEEDS_VARIANT_SELECTION,
    NO_ELIGIBLE_VARIANT,
    CONTENT_NOT_READY,
    INVALID_ALIGNMENT,
}

data class LayoutVariantSelection(
    val code: LayoutVariantSelectionCode,
    val variantId: String? = null,
    val source: LayoutVariantSelectionSource? = null,
)

data class LayoutCatalogV2Item(
    val layoutId: String,
    val revision: Long,
    val publication: LayoutPublication,
    val compatibility: LayoutCompatibilityV1,
    val portableIdentities: List<PortableGameIdentityV1>,
    val local: LayoutCatalogV2LocalState,
    val contentHash: String?,
    val variants: List<LayoutCatalogV2VariantSummary>,
    val preferredVariantId: String?,
    val selection: LayoutVariantSelection,
)

sealed interface LayoutCatalogV2ProjectionResult {
    data class Projected(val item: LayoutCatalogV2Item) : LayoutCatalogV2ProjectionResult
    data class Rejected(val code: LayoutCatalogV2ProjectionRejection) :
        LayoutCatalogV2ProjectionResult
}

enum class LayoutCatalogV2ProjectionRejection { INVALID_DESCRIPTOR }

class VerifiedTouchLayoutV2Content internal constructor(
    val document: TouchLayoutV2Document,
)

sealed interface LayoutContentVerificationResult {
    data class Verified(val content: VerifiedTouchLayoutV2Content) : LayoutContentVerificationResult
    data class Rejected(val code: String) : LayoutContentVerificationResult
}

sealed interface LayoutPreferenceV2Result {
    data class Accepted(
        val layoutId: String,
        val revision: Long,
        val variantId: String,
    ) : LayoutPreferenceV2Result

    data class Rejected(val code: LayoutPreferenceV2Rejection) : LayoutPreferenceV2Result
}

enum class LayoutPreferenceV2Rejection {
    INVALID_DESCRIPTOR,
    CONTENT_NOT_READY,
    INVALID_ALIGNMENT,
    UNKNOWN_VARIANT,
    INELIGIBLE_VARIANT,
}
