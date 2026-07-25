package com.limelight.ligase.feature.input.layout.v2.application

import com.limelight.ligase.feature.input.layout.v2.data.*
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.layout.LayoutContractV1Validator
import com.limelight.ligase.layout.LayoutDescriptorV1

class LayoutCatalogV2Source(
    val descriptor: LayoutDescriptorV1,
    val origin: LayoutLocalOrigin,
    val workspace: LayoutWorkspaceState,
    val packagedContent: ByteArray? = null,
) {
    override fun toString(): String =
        "LayoutCatalogV2Source(layoutId=${descriptor.layoutId}," +
            "revision=${descriptor.revision},origin=$origin,workspace=$workspace," +
            "content=${if (packagedContent == null) "absent" else "redacted"})"
}

class LayoutCatalogV2Catalog(
    private val localRepository: LayoutCatalogV2LocalRepository,
    private val preferenceRepository: LayoutPreferredVariantV2Repository,
    private val onStateChanged: (LayoutCatalogV2UiState) -> Unit = {},
) {
    var state: LayoutCatalogV2UiState = LayoutCatalogV2UiState()
        private set
    private var sources: List<LayoutCatalogV2Source> = emptyList()
    private var context: LayoutCatalogV2Context? = null
    private var entries: Map<String, Entry> = emptyMap()

    fun refresh(
        sources: List<LayoutCatalogV2Source>,
        context: LayoutCatalogV2Context,
    ): LayoutCatalogV2UiState {
        this.sources = sources.toList()
        this.context = context
        return rebuild(LayoutPreferenceActionState.Idle)
    }

    fun saveLocalCopy(
        descriptor: LayoutDescriptorV1,
        raw: ByteArray,
    ): LayoutCatalogV2WriteResult {
        if (LayoutContractV1Validator.validateDescriptor(descriptor) != null) {
            return LayoutCatalogV2WriteResult(LayoutCatalogV2WriteCode.INVALID_DESCRIPTOR)
        }
        val verified = when (val result = TouchLayoutV2ContentVerifier.verify(raw)) {
            is LayoutContentVerificationResult.Verified -> result.content
            is LayoutContentVerificationResult.Rejected ->
                return LayoutCatalogV2WriteResult(LayoutCatalogV2WriteCode.CONTENT_REJECTED)
        }
        if (!LayoutCatalogV2Projector.contentAligned(descriptor, verified)) {
            return LayoutCatalogV2WriteResult(
                LayoutCatalogV2WriteCode.CONTENT_ALIGNMENT_INVALID,
            )
        }
        val code = when (
            localRepository.write(descriptor.layoutId, descriptor.revision, raw)
        ) {
            LayoutCatalogV2LocalWriteResult.SAVED -> LayoutCatalogV2WriteCode.SAVED
            LayoutCatalogV2LocalWriteResult.INVALID_IDENTITY ->
                LayoutCatalogV2WriteCode.INVALID_DESCRIPTOR
            LayoutCatalogV2LocalWriteResult.WRITE_FAILED ->
                LayoutCatalogV2WriteCode.WRITE_FAILED
            LayoutCatalogV2LocalWriteResult.READBACK_FAILED ->
                LayoutCatalogV2WriteCode.READBACK_FAILED
            LayoutCatalogV2LocalWriteResult.ROLLBACK_FAILED ->
                LayoutCatalogV2WriteCode.ROLLBACK_FAILED
        }
        return LayoutCatalogV2WriteResult(code)
    }

    fun selectPreferredVariant(
        layoutId: String,
        revision: Long,
        variantId: String,
    ): LayoutPreferredVariantWriteResult {
        if (
            LayoutContractV1Validator.normalizeUuid(layoutId) != layoutId ||
            !LayoutContractV1Validator.isValidRevision(revision) ||
            LayoutContractV1Validator.normalizeUuid(variantId) != variantId
        ) {
            return preferenceFailure(LayoutPreferredVariantWriteCode.INVALID_IDENTITY)
        }
        val entry = entries[layoutId]
            ?: return preferenceFailure(LayoutPreferredVariantWriteCode.CONTENT_NOT_READY)
        if (entry.descriptor.revision != revision) {
            return preferenceFailure(LayoutPreferredVariantWriteCode.STALE_REVISION)
        }
        val activeContext = context
            ?: return preferenceFailure(LayoutPreferredVariantWriteCode.CONTENT_NOT_READY)
        val decision = LayoutPreferenceV2Action.select(
            entry.descriptor,
            entry.content,
            entry.local,
            activeContext,
            variantId,
        )
        if (decision is LayoutPreferenceV2Result.Rejected) {
            return preferenceFailure(
                when (decision.code) {
                    LayoutPreferenceV2Rejection.INVALID_DESCRIPTOR ->
                        LayoutPreferredVariantWriteCode.INVALID_IDENTITY
                    LayoutPreferenceV2Rejection.CONTENT_NOT_READY ->
                        LayoutPreferredVariantWriteCode.CONTENT_NOT_READY
                    LayoutPreferenceV2Rejection.INVALID_ALIGNMENT ->
                        LayoutPreferredVariantWriteCode.INVALID_ALIGNMENT
                    LayoutPreferenceV2Rejection.UNKNOWN_VARIANT ->
                        LayoutPreferredVariantWriteCode.UNKNOWN_VARIANT
                    LayoutPreferenceV2Rejection.INELIGIBLE_VARIANT ->
                        LayoutPreferredVariantWriteCode.INELIGIBLE_VARIANT
                },
            )
        }
        if (!preferenceRepository.write(layoutId, revision, variantId)) {
            return preferenceFailure(LayoutPreferredVariantWriteCode.WRITE_FAILED)
        }
        rebuild(LayoutPreferenceActionState.Saved(layoutId, revision, variantId))
        return LayoutPreferredVariantWriteResult(LayoutPreferredVariantWriteCode.SAVED)
    }

    fun clearPreferredVariant(layoutId: String): LayoutPreferredVariantWriteResult {
        if (LayoutContractV1Validator.normalizeUuid(layoutId) != layoutId) {
            return preferenceFailure(LayoutPreferredVariantWriteCode.INVALID_IDENTITY)
        }
        if (!preferenceRepository.clear(layoutId)) {
            return preferenceFailure(LayoutPreferredVariantWriteCode.WRITE_FAILED)
        }
        rebuild(LayoutPreferenceActionState.Cleared(layoutId))
        return LayoutPreferredVariantWriteResult(LayoutPreferredVariantWriteCode.CLEARED)
    }

    private fun rebuild(action: LayoutPreferenceActionState): LayoutCatalogV2UiState {
        val activeContext = context ?: return publish(
            LayoutCatalogV2UiState(preferenceAction = action),
        )
        val issues = mutableListOf<LayoutCatalogV2Issue>()
        val nextEntries = linkedMapOf<String, Entry>()
        val items = sources.mapNotNull { source ->
            val descriptor = source.descriptor
            if (LayoutContractV1Validator.validateDescriptor(descriptor) != null) {
                issues += issue(descriptor, LayoutCatalogV2IssueCode.INVALID_DESCRIPTOR)
                return@mapNotNull null
            }
            val loaded = load(source, issues)
            var preferred = when (
                val stored = preferenceRepository.read(descriptor.layoutId, descriptor.revision)
            ) {
                StoredLayoutVariantPreference.Absent -> null
                is StoredLayoutVariantPreference.Valid -> stored.variantId
                is StoredLayoutVariantPreference.Stale -> {
                    issues += issue(
                        descriptor,
                        LayoutCatalogV2IssueCode.STALE_STORED_PREFERENCE,
                    )
                    null
                }
                StoredLayoutVariantPreference.Invalid -> {
                    issues += issue(
                        descriptor,
                        LayoutCatalogV2IssueCode.INVALID_STORED_PREFERENCE,
                    )
                    null
                }
            }
            if (preferred != null && loaded.content != null) {
                val decision = LayoutPreferenceV2Action.select(
                    descriptor,
                    loaded.content,
                    loaded.local,
                    activeContext,
                    preferred,
                )
                if (decision !is LayoutPreferenceV2Result.Accepted) {
                    issues += issue(
                        descriptor,
                        LayoutCatalogV2IssueCode.INVALID_STORED_PREFERENCE,
                    )
                    preferred = null
                }
            }
            val projection = LayoutCatalogV2Projector.project(
                descriptor,
                loaded.content,
                loaded.local,
                activeContext,
                preferred,
            ) as? LayoutCatalogV2ProjectionResult.Projected ?: return@mapNotNull null
            val item = projection.item
            nextEntries[descriptor.layoutId] = Entry(descriptor, loaded.content, loaded.local)
            uiItem(item, loaded.content)
        }.sortedWith(compareBy({ it.layoutId }, { it.revision }))
        entries = nextEntries.toMap()
        return publish(LayoutCatalogV2UiState(items = items, issues = issues, preferenceAction = action))
    }

    private fun load(
        source: LayoutCatalogV2Source,
        issues: MutableList<LayoutCatalogV2Issue>,
    ): Loaded {
        val descriptor = source.descriptor
        val verification = when (source.origin) {
            LayoutLocalOrigin.PACKAGED_BUILT_IN -> source.packagedContent?.let {
                when (val verified = TouchLayoutV2ContentVerifier.verify(it)) {
                    is LayoutContentVerificationResult.Verified ->
                        LayoutCatalogV2LocalReadResult.Ready(verified.content, it)
                    is LayoutContentVerificationResult.Rejected ->
                        LayoutCatalogV2LocalReadResult.Rejected(verified.code)
                }
            } ?: LayoutCatalogV2LocalReadResult.Missing
            LayoutLocalOrigin.LOCAL_COPY ->
                localRepository.read(descriptor.layoutId, descriptor.revision)
            LayoutLocalOrigin.HOST_CATALOG -> LayoutCatalogV2LocalReadResult.Missing
        }
        val base = LayoutCatalogV2LocalState(
            source.origin,
            LayoutLocalAvailability.NOT_LOCAL,
            source.workspace,
        )
        return when (verification) {
            is LayoutCatalogV2LocalReadResult.Ready -> {
                if (!LayoutCatalogV2Projector.contentAligned(descriptor, verification.content)) {
                    issues += issue(
                        descriptor,
                        LayoutCatalogV2IssueCode.CONTENT_ALIGNMENT_INVALID,
                    )
                    Loaded(null, base.copy(availability = LayoutLocalAvailability.INVALID))
                } else {
                    Loaded(
                        verification.content,
                        base.copy(availability = LayoutLocalAvailability.READY),
                    )
                }
            }
            LayoutCatalogV2LocalReadResult.Missing -> {
                if (source.origin != LayoutLocalOrigin.HOST_CATALOG) {
                    issues += issue(descriptor, LayoutCatalogV2IssueCode.CONTENT_MISSING)
                }
                Loaded(null, base)
            }
            is LayoutCatalogV2LocalReadResult.Rejected -> {
                issues += issue(
                    descriptor,
                    if (verification.code == "contentHashMismatch") {
                        LayoutCatalogV2IssueCode.CONTENT_HASH_MISMATCH
                    } else {
                        LayoutCatalogV2IssueCode.CONTENT_CORRUPT
                    },
                )
                Loaded(null, base.copy(availability = LayoutLocalAvailability.INVALID))
            }
            LayoutCatalogV2LocalReadResult.StorageFailure -> {
                issues += issue(descriptor, LayoutCatalogV2IssueCode.STORAGE_FAILURE)
                Loaded(null, base.copy(availability = LayoutLocalAvailability.INVALID))
            }
        }
    }

    private fun uiItem(
        item: LayoutCatalogV2Item,
        content: VerifiedTouchLayoutV2Content?,
    ): LayoutCatalogV2UiItem {
        val contentById = content?.document?.variants?.associateBy { it.variantId }.orEmpty()
        return LayoutCatalogV2UiItem(
            layoutId = item.layoutId,
            revision = item.revision,
            displayName = content?.document?.displayName,
            publication = item.publication,
            compatibility = item.compatibility,
            portableIdentities = item.portableIdentities.map {
                LayoutCatalogV2PortableIdentity(it.provider, it.id)
            },
            local = item.local,
            contentVerified = content != null && item.local.availability == LayoutLocalAvailability.READY,
            variants = item.variants.map { summary ->
                val variant = contentById.getValue(summary.variantId)
                val compatibility = variant.recommendation
                LayoutCatalogV2UiVariant(
                    summary,
                    variant.deviceClasses,
                    variant.orientations,
                    LayoutCatalogV2CompatibilityDetails(
                        compatibility.preferredAspectRatio,
                        compatibility.minAspectRatio,
                        compatibility.maxAspectRatio,
                        compatibility.minShortestSideDp,
                        compatibility.minTouchTargetDp,
                        compatibility.safeAreaPolicy,
                        variant.canvas,
                        LayoutCatalogV2DesignReferenceHint(
                            compatibility.referenceDensityDpi,
                            compatibility.referenceResolution,
                        ),
                    ),
                )
            },
            preferredVariantId = item.preferredVariantId,
            selection = item.selection,
        )
    }

    private fun preferenceFailure(
        code: LayoutPreferredVariantWriteCode,
    ): LayoutPreferredVariantWriteResult {
        publish(state.copy(preferenceAction = LayoutPreferenceActionState.Failed(code)))
        return LayoutPreferredVariantWriteResult(code)
    }

    private fun publish(next: LayoutCatalogV2UiState): LayoutCatalogV2UiState {
        state = next
        onStateChanged(next)
        return next
    }

    private fun issue(descriptor: LayoutDescriptorV1, code: LayoutCatalogV2IssueCode) =
        LayoutCatalogV2Issue(descriptor.layoutId, descriptor.revision, code)

    private data class Loaded(
        val content: VerifiedTouchLayoutV2Content?,
        val local: LayoutCatalogV2LocalState,
    )

    private data class Entry(
        val descriptor: LayoutDescriptorV1,
        val content: VerifiedTouchLayoutV2Content?,
        val local: LayoutCatalogV2LocalState,
    )
}
