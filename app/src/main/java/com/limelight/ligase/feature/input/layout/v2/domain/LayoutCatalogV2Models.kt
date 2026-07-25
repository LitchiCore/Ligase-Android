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

data class LayoutCatalogV2DesignReferenceHint(
    val densityDpi: Int?,
    val resolution: IntSize?,
)

data class LayoutCatalogV2CompatibilityDetails(
    val preferredAspectRatio: AspectRatio,
    val minAspectRatio: AspectRatio,
    val maxAspectRatio: AspectRatio,
    val minShortestSideDp: Int,
    val minTouchTargetDp: Int,
    val safeAreaPolicy: SafeAreaPolicy,
    val canvas: IntSize,
    val designReferenceHint: LayoutCatalogV2DesignReferenceHint,
)

data class LayoutCatalogV2UiVariant(
    val summary: LayoutCatalogV2VariantSummary,
    val deviceClasses: List<DeviceClass>,
    val orientations: List<LayoutOrientation>,
    val compatibility: LayoutCatalogV2CompatibilityDetails,
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

data class LayoutCatalogV2PortableIdentity(
    val provider: String,
    val id: String,
)

data class LayoutCatalogV2UiItem(
    val layoutId: String,
    val revision: Long,
    val displayName: String?,
    val publication: LayoutPublication,
    val compatibility: LayoutCompatibilityV1,
    val portableIdentities: List<LayoutCatalogV2PortableIdentity>,
    val local: LayoutCatalogV2LocalState,
    val contentVerified: Boolean,
    val variants: List<LayoutCatalogV2UiVariant>,
    val preferredVariantId: String?,
    val selection: LayoutVariantSelection,
)

data class LayoutCatalogV2Issue(
    val layoutId: String?,
    val revision: Long?,
    val code: LayoutCatalogV2IssueCode,
)

enum class LayoutCatalogV2IssueCode {
    INVALID_DESCRIPTOR,
    CONTENT_MISSING,
    CONTENT_CORRUPT,
    CONTENT_HASH_MISMATCH,
    CONTENT_ALIGNMENT_INVALID,
    INVALID_STORED_PREFERENCE,
    STALE_STORED_PREFERENCE,
    STORAGE_FAILURE,
}

sealed interface LayoutPreferenceActionState {
    data object Idle : LayoutPreferenceActionState
    data class Saved(
        val layoutId: String,
        val revision: Long,
        val variantId: String,
    ) : LayoutPreferenceActionState
    data class Cleared(val layoutId: String) : LayoutPreferenceActionState
    data class Failed(val code: LayoutPreferredVariantWriteCode) :
        LayoutPreferenceActionState
}

data class LayoutCatalogV2UiState(
    val refreshing: Boolean = false,
    val items: List<LayoutCatalogV2UiItem> = emptyList(),
    val issues: List<LayoutCatalogV2Issue> = emptyList(),
    val preferenceAction: LayoutPreferenceActionState = LayoutPreferenceActionState.Idle,
)

enum class LayoutPreferredVariantWriteCode {
    SAVED,
    CLEARED,
    INVALID_IDENTITY,
    STALE_REVISION,
    CONTENT_NOT_READY,
    INVALID_ALIGNMENT,
    UNKNOWN_VARIANT,
    INELIGIBLE_VARIANT,
    WRITE_FAILED,
}

data class LayoutPreferredVariantWriteResult(
    val code: LayoutPreferredVariantWriteCode,
)

enum class LayoutCatalogV2WriteCode {
    SAVED,
    INVALID_DESCRIPTOR,
    INVALID_ORIGIN,
    CONTENT_REJECTED,
    CONTENT_ALIGNMENT_INVALID,
    WRITE_FAILED,
    READBACK_FAILED,
    ROLLBACK_FAILED,
}

data class LayoutCatalogV2WriteResult(
    val code: LayoutCatalogV2WriteCode,
)

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
