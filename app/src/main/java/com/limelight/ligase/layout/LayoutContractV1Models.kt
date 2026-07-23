package com.limelight.ligase.layout

data class PortableGameIdentityV1(
    val provider: String,
    val id: String,
)

data class LayoutBindingV1(
    val layoutId: String,
    val revision: Long,
)

data class LayoutRevisionV1(
    val layoutId: String,
    val revision: Long,
)

data class LayoutCompatibilityV1(
    val minClientContractVersion: Int,
    val minLayoutRuntimeVersion: Int,
)

data class LayoutVariantV1(
    val variantId: String,
    val inputProfile: String,
    val deviceClasses: List<String>,
    val orientations: List<String>,
)

data class LayoutDescriptorV1(
    val schemaVersion: Int,
    val layoutId: String,
    val revision: Long,
    val portableIdentities: List<PortableGameIdentityV1>,
    val compatibility: LayoutCompatibilityV1,
    val publicationStatus: String,
    val variants: List<LayoutVariantV1>,
)

data class LayoutPreferenceV1(
    val layoutId: String,
    val revision: Long,
    val variantId: String,
)

data class LayoutResolutionContextV1(
    val clientContractVersion: Int,
    val layoutRuntimeVersion: Int,
    val inputProfile: String,
    val deviceClass: String,
    val orientation: String,
    val installedDrafts: Set<LayoutRevisionV1> = emptySet(),
    val preference: LayoutPreferenceV1? = null,
)

data class LayoutResolutionRequestV1(
    val hostUniqueId: String,
    val appUuid: String,
    val portableIdentity: PortableGameIdentityV1?,
    val layoutBinding: LayoutBindingV1?,
    val context: LayoutResolutionContextV1,
    val descriptors: List<LayoutDescriptorV1>,
)

data class LayoutResolutionV1(
    val code: String,
    val source: String? = null,
    val layoutId: String? = null,
    val revision: Long? = null,
    val variantId: String? = null,
    val detail: String? = null,
)

object LayoutContractV1Codes {
    const val RESOLVED = "resolved"
    const val INVALID_JSON = "invalidJson"
    const val INVALID_SCHEMA = "invalidSchema"
    const val UNKNOWN_FIELD = "unknownField"
    const val INVALID_REVISION = "invalidRevision"
    const val INVALID_INSTANCE_IDENTITY = "invalidInstanceIdentity"
    const val INVALID_CONTEXT = "invalidContext"
    const val INVALID_DESCRIPTOR = "invalidDescriptor"
    const val INVALID_BINDING = "invalidBinding"
    const val BINDING_NOT_FOUND = "bindingNotFound"
    const val BINDING_RETIRED = "bindingRetired"
    const val BINDING_DRAFT_NOT_INSTALLED = "bindingDraftNotInstalled"
    const val INCOMPATIBLE_BINDING = "incompatibleBinding"
    const val INVALID_SYNC_PORTABLE_IDENTITY = "invalidSyncPortableIdentity"
    const val INPUT_PROFILE_DOES_NOT_AUTO_MATCH = "inputProfileDoesNotAutoMatch"
    const val NO_MATCH = "noMatch"
    const val NO_COMPATIBLE_REVISION = "noCompatibleRevision"
    const val LAYOUT_CONFLICT = "layoutConflict"
    const val NO_ELIGIBLE_VARIANT = "noEligibleVariant"
    const val NEEDS_VARIANT_SELECTION = "needsVariantSelection"
}
