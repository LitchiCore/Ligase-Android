package com.limelight.ligase.layout

object LayoutContractV1Resolver {
    fun resolve(request: LayoutResolutionRequestV1): LayoutResolutionV1 {
        if (
            LayoutContractV1Validator.normalizeUuid(request.hostUniqueId) == null ||
            LayoutContractV1Validator.normalizeUuid(request.appUuid) == null
        ) {
            return error(LayoutContractV1Codes.INVALID_INSTANCE_IDENTITY)
        }

        request.descriptors
            .sortedWith(compareBy<LayoutDescriptorV1>({ it.layoutId }, { it.revision }))
            .forEach { descriptor ->
                if (LayoutContractV1Validator.validateDescriptor(descriptor) != null) {
                    return error(LayoutContractV1Codes.INVALID_DESCRIPTOR)
                }
            }

        if (
            request.descriptors
                .groupingBy { it.layoutId to it.revision }
                .eachCount()
                .any { it.value > 1 }
        ) {
            return error(
                LayoutContractV1Codes.INVALID_DESCRIPTOR,
                detail = "duplicateRevision",
            )
        }

        if (LayoutContractV1Validator.validateContext(request.context) != null) {
            return error(LayoutContractV1Codes.INVALID_CONTEXT)
        }

        request.layoutBinding?.let { binding ->
            return resolveBinding(binding, request.context, request.descriptors)
        }

        if (
            request.portableIdentity != null &&
            LayoutContractV1Validator.normalizePortableIdentity(request.portableIdentity) == null
        ) {
            return error(LayoutContractV1Codes.INVALID_SYNC_PORTABLE_IDENTITY)
        }
        if (request.context.inputProfile != "touch") {
            return error(LayoutContractV1Codes.INPUT_PROFILE_DOES_NOT_AUTO_MATCH)
        }
        val identity = LayoutContractV1Validator.normalizePortableIdentity(
            request.portableIdentity,
        ) ?: return error(LayoutContractV1Codes.NO_MATCH)
        return resolvePortable(identity, request.context, request.descriptors)
    }

    private fun resolveBinding(
        binding: LayoutBindingV1,
        context: LayoutResolutionContextV1,
        descriptors: List<LayoutDescriptorV1>,
    ): LayoutResolutionV1 {
        val layoutId = LayoutContractV1Validator.normalizeUuid(binding.layoutId)
        if (
            layoutId == null ||
            layoutId != binding.layoutId ||
            !LayoutContractV1Validator.isValidRevision(binding.revision)
        ) {
            return error(LayoutContractV1Codes.INVALID_BINDING)
        }

        val descriptor = descriptors.firstOrNull {
            it.layoutId == layoutId && it.revision == binding.revision
        } ?: return error(LayoutContractV1Codes.BINDING_NOT_FOUND)
        if (descriptor.publicationStatus == "retired") {
            return error(LayoutContractV1Codes.BINDING_RETIRED)
        }
        if (
            descriptor.publicationStatus == "draft" &&
            LayoutRevisionV1(layoutId, binding.revision) !in context.installedDrafts
        ) {
            return error(LayoutContractV1Codes.BINDING_DRAFT_NOT_INSTALLED)
        }
        if (!isVersionCompatible(descriptor, context)) {
            return error(LayoutContractV1Codes.INCOMPATIBLE_BINDING)
        }
        return resolveVariant(descriptor, context, source = "binding")
    }

    private fun resolvePortable(
        identity: PortableGameIdentityV1,
        context: LayoutResolutionContextV1,
        descriptors: List<LayoutDescriptorV1>,
    ): LayoutResolutionV1 {
        val exactMatches = descriptors.filter { descriptor ->
            descriptor.publicationStatus == "published" &&
                descriptor.portableIdentities.any { it == identity }
        }
        val layoutIds = exactMatches.map { it.layoutId }.distinct().sorted()
        if (layoutIds.isEmpty()) return error(LayoutContractV1Codes.NO_MATCH)
        if (layoutIds.size > 1) return error(LayoutContractV1Codes.LAYOUT_CONFLICT)

        val compatible = exactMatches
            .filter { isVersionCompatible(it, context) }
            .sortedByDescending { it.revision }
        if (compatible.isEmpty()) {
            return error(LayoutContractV1Codes.NO_COMPATIBLE_REVISION)
        }
        val descriptor = compatible.firstOrNull { eligibleVariants(it, context).isNotEmpty() }
            ?: return error(LayoutContractV1Codes.NO_ELIGIBLE_VARIANT)
        return resolveVariant(descriptor, context, source = "portable")
    }

    private fun resolveVariant(
        descriptor: LayoutDescriptorV1,
        context: LayoutResolutionContextV1,
        source: String,
    ): LayoutResolutionV1 {
        val variants = eligibleVariants(descriptor, context)
        if (variants.isEmpty()) return error(LayoutContractV1Codes.NO_ELIGIBLE_VARIANT)

        val selected = when {
            variants.size == 1 -> variants[0]
            context.preference?.layoutId == descriptor.layoutId &&
                context.preference.revision == descriptor.revision ->
                variants.singleOrNull {
                    it.variantId == context.preference.variantId
                }
            else -> null
        } ?: return error(LayoutContractV1Codes.NEEDS_VARIANT_SELECTION)

        return LayoutResolutionV1(
            code = LayoutContractV1Codes.RESOLVED,
            source = source,
            layoutId = descriptor.layoutId,
            revision = descriptor.revision,
            variantId = selected.variantId,
        )
    }

    private fun eligibleVariants(
        descriptor: LayoutDescriptorV1,
        context: LayoutResolutionContextV1,
    ): List<LayoutVariantV1> = descriptor.variants
        .filter { variant ->
            variant.inputProfile == context.inputProfile &&
                context.deviceClass in variant.deviceClasses &&
                context.orientation in variant.orientations
        }
        .sortedBy { it.variantId }

    private fun isVersionCompatible(
        descriptor: LayoutDescriptorV1,
        context: LayoutResolutionContextV1,
    ): Boolean =
        descriptor.compatibility.minClientContractVersion <= context.clientContractVersion &&
            descriptor.compatibility.minLayoutRuntimeVersion <= context.layoutRuntimeVersion

    private fun error(code: String, detail: String? = null) =
        LayoutResolutionV1(code = code, detail = detail)
}
