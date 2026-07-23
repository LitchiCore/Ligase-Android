package com.limelight.ligase.layout

import java.util.Locale
import java.util.UUID

object LayoutContractV1Validator {
    const val MAX_REVISION = 9_007_199_254_740_991L

    val inputProfiles = setOf("touch", "gamepad", "keyboardMouse", "none")
    val deviceClasses = setOf("phone", "tablet")
    val orientations = setOf("portrait", "landscape")
    val publicationStatuses = setOf("draft", "published", "retired")

    private val dFormatUuid =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun normalizeUuid(value: String?): String? {
        if (value == null || !dFormatUuid.matches(value)) return null
        return try {
            UUID.fromString(value).toString().lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun normalizePortableIdentity(
        identity: PortableGameIdentityV1?,
    ): PortableGameIdentityV1? {
        if (
            identity == null ||
            identity.provider != "steam" ||
            identity.id.isEmpty() ||
            identity.id.length > 10 ||
            identity.id.any { it !in '0'..'9' } ||
            identity.id[0] == '0'
        ) {
            return null
        }
        val appId = identity.id.toLongOrNull() ?: return null
        if (appId !in 1..0xffff_ffffL || appId.toString() != identity.id) return null
        return PortableGameIdentityV1("steam", identity.id)
    }

    fun validateDescriptor(descriptor: LayoutDescriptorV1): String? {
        if (descriptor.schemaVersion != 1) return "descriptor.schemaVersion"
        val layoutId = normalizeUuid(descriptor.layoutId)
        if (layoutId == null || layoutId != descriptor.layoutId) {
            return "descriptor.layoutId"
        }
        if (!isValidRevision(descriptor.revision)) return "descriptor.revision"
        if (
            descriptor.compatibility.minClientContractVersion <= 0 ||
            descriptor.compatibility.minLayoutRuntimeVersion <= 0
        ) {
            return "descriptor.compatibility"
        }
        if (descriptor.publicationStatus !in publicationStatuses) {
            return "descriptor.publicationStatus"
        }

        val identities = mutableSetOf<String>()
        for (identity in descriptor.portableIdentities) {
            val normalized = normalizePortableIdentity(identity)
                ?: return "descriptor.portableIdentity"
            if (!identities.add("${normalized.provider}:${normalized.id}")) {
                return "descriptor.portableIdentityDuplicate"
            }
        }

        if (descriptor.variants.isEmpty()) return "descriptor.variants"
        val variantIds = mutableSetOf<String>()
        for (variant in descriptor.variants) {
            val variantId = normalizeUuid(variant.variantId)
            if (variantId == null || variantId != variant.variantId) {
                return "descriptor.variantId"
            }
            if (!variantIds.add(variantId)) return "descriptor.variantIdDuplicate"
            if (variant.inputProfile !in inputProfiles) return "descriptor.inputProfile"
            if (!isValidSet(variant.deviceClasses, deviceClasses)) {
                return "descriptor.deviceClasses"
            }
            if (!isValidSet(variant.orientations, orientations)) {
                return "descriptor.orientations"
            }
        }
        return null
    }

    fun validateContext(context: LayoutResolutionContextV1): String? {
        if (
            context.clientContractVersion <= 0 ||
            context.layoutRuntimeVersion <= 0 ||
            context.inputProfile !in inputProfiles ||
            context.deviceClass !in deviceClasses ||
            context.orientation !in orientations
        ) {
            return "context.invalid"
        }

        context.preference?.let { preference ->
            val layoutId = normalizeUuid(preference.layoutId)
            val variantId = normalizeUuid(preference.variantId)
            if (
                layoutId == null ||
                layoutId != preference.layoutId ||
                !isValidRevision(preference.revision) ||
                variantId == null ||
                variantId != preference.variantId
            ) {
                return "context.preference"
            }
        }

        if (context.installedDrafts.any { draft ->
                val layoutId = normalizeUuid(draft.layoutId)
                layoutId == null ||
                    layoutId != draft.layoutId ||
                    !isValidRevision(draft.revision)
            }
        ) {
            return "context.installedDrafts"
        }
        return null
    }

    fun isValidRevision(revision: Long): Boolean = revision in 1..MAX_REVISION

    private fun isValidSet(values: List<String>, allowlist: Set<String>): Boolean =
        values.isNotEmpty() &&
            values.all { it in allowlist } &&
            values.size == values.toSet().size
}
