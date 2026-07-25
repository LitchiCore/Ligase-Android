package com.limelight.ligase.feature.input.layout.v2.application

import com.limelight.ligase.feature.input.layout.v2.data.LayoutCatalogV2PackagedSnapshot
import com.limelight.ligase.feature.input.layout.v2.data.LayoutCatalogV2PackagedSource
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.layout.LayoutContractV1Validator
import com.limelight.ligase.layout.LayoutDescriptorV1

class LayoutCatalogV2RegisteredRecord(
    val descriptor: LayoutDescriptorV1,
    val origin: LayoutLocalOrigin,
    val workspace: LayoutWorkspaceState,
    internal val committedArtifact: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is LayoutCatalogV2RegisteredRecord &&
            descriptor == other.descriptor &&
            origin == other.origin &&
            workspace == other.workspace &&
            when {
                committedArtifact == null -> other.committedArtifact == null
                other.committedArtifact == null -> false
                else -> committedArtifact.contentEquals(other.committedArtifact)
            }

    override fun hashCode(): Int =
        31 * (31 * (31 * descriptor.hashCode() + origin.hashCode()) + workspace.hashCode()) +
            (committedArtifact?.contentHashCode() ?: 0)

    override fun toString(): String =
        "LayoutCatalogV2RegisteredRecord(layoutId=${descriptor.layoutId}," +
            "revision=${descriptor.revision},origin=$origin,workspace=$workspace," +
            "content=${if (committedArtifact == null) "absent" else "redacted"})"
}

data class LayoutCatalogV2HostSnapshot(
    val state: LayoutHostCatalogState,
    val descriptors: List<LayoutDescriptorV1> = emptyList(),
)

@JvmInline
value class LayoutCatalogV2RefreshTicket internal constructor(internal val generation: Long)

class LayoutCatalogV2SourceRegistry(
    private val catalog: LayoutCatalogV2Catalog,
    private val packagedSource: LayoutCatalogV2PackagedSource,
    private val onStateChanged: (LayoutCatalogV2UiState) -> Unit = {},
) {
    var state: LayoutCatalogV2UiState = LayoutCatalogV2UiState()
        private set

    private var generation = 0L
    private var closed = false
    private var lastSuccess: LayoutCatalogV2UiState? = null

    fun beginRefresh(): LayoutCatalogV2RefreshTicket {
        val ticket = LayoutCatalogV2RefreshTicket(++generation)
        if (closed) return ticket
        val hasLoaded = state.lifecycle.hasLoaded
        publish(
            state.copy(
                refreshing = true,
                lifecycle = LayoutCatalogLifecycle(
                    initialLoading = !hasLoaded,
                    refreshing = true,
                    hasLoaded = hasLoaded,
                    stale = false,
                ),
                refreshOutcome = LayoutCatalogRefreshOutcome.IN_PROGRESS,
            ),
        )
        return ticket
    }

    fun completeRefresh(
        ticket: LayoutCatalogV2RefreshTicket,
        registeredRecords: List<LayoutCatalogV2RegisteredRecord>,
        hostSnapshot: LayoutCatalogV2HostSnapshot,
        context: LayoutCatalogV2Context?,
    ): Boolean {
        if (closed || ticket.generation != generation) return false

        val packaged = packagedSource.read()
        val localRecords = registeredRecords.filter {
            it.origin == LayoutLocalOrigin.LOCAL_COPY
        }
        val candidates = localRecords.map {
            LayoutCatalogV2Source(
                it.descriptor,
                it.origin,
                it.workspace,
                registeredContent = it.committedArtifact,
            )
        } + hostSnapshot.descriptors.map {
            LayoutCatalogV2Source(
                it,
                LayoutLocalOrigin.HOST_CATALOG,
                LayoutWorkspaceState.NONE,
            )
        }
        val reconciled = reconcile(candidates)
        val sources = reconciled.sources
        val projected = catalog.refresh(sources, context)
        val summaries = sourceSummaries(
            packaged,
            localRecords,
            hostSnapshot,
            projected,
            reconciled.issue,
        )
        if (hostSnapshot.state == LayoutHostCatalogState.AUTHORIZED_LOADING) {
            publish(
                state.copy(
                    refreshing = true,
                    lifecycle = LayoutCatalogLifecycle(
                        initialLoading = lastSuccess == null,
                        refreshing = true,
                        hasLoaded = lastSuccess != null,
                        stale = false,
                    ),
                    refreshOutcome = LayoutCatalogRefreshOutcome.IN_PROGRESS,
                    sourceSummaries = summaries,
                    hostCatalogState = hostSnapshot.state,
                ),
            )
            return true
        }
        val failed = summaries.any { it.phase == LayoutCatalogSourcePhase.FAILED }
        val usable = projected.items.isNotEmpty()
        val authorized = localRecords.isNotEmpty() ||
            hostSnapshot.state !in setOf(
                LayoutHostCatalogState.NOT_CONNECTED,
                LayoutHostCatalogState.PERMISSION_DENIED,
                LayoutHostCatalogState.FAILED,
            )

        if (failed && !usable) {
            val retained = lastSuccess
            val next = if (retained != null) {
                retained.copy(
                    refreshing = false,
                    lifecycle = LayoutCatalogLifecycle(
                        initialLoading = false,
                        refreshing = false,
                        hasLoaded = true,
                        stale = true,
                    ),
                    refreshOutcome = LayoutCatalogRefreshOutcome.FAILED_USING_LAST_SUCCESS,
                    sourceSummaries = summaries.map { it.copy(stale = true) },
                )
            } else {
                projected.copy(
                    refreshing = false,
                    lifecycle = LayoutCatalogLifecycle(hasLoaded = true),
                    refreshOutcome = LayoutCatalogRefreshOutcome.FAILED_NO_CACHE,
                    emptyReason = LayoutCatalogEmptyReason.REFRESH_FAILED_WITHOUT_CACHE,
                    sourceSummaries = summaries,
                    hostCatalogState = hostSnapshot.state,
                )
            }
            publish(next)
            return true
        }

        val emptyReason = when {
            usable -> null
            !authorized -> LayoutCatalogEmptyReason.NO_AUTHORIZED_SOURCES
            projected.issues.isNotEmpty() -> LayoutCatalogEmptyReason.ALL_ENTRIES_REJECTED
            else -> LayoutCatalogEmptyReason.SOURCES_EMPTY
        }
        val outcome = if (failed || projected.issues.isNotEmpty()) {
            LayoutCatalogRefreshOutcome.PARTIAL_SUCCESS
        } else {
            LayoutCatalogRefreshOutcome.SUCCESS
        }
        val next = projected.copy(
            refreshing = false,
            lifecycle = LayoutCatalogLifecycle(hasLoaded = true),
            refreshOutcome = outcome,
            emptyReason = emptyReason,
            sourceSummaries = summaries,
            revisionSets = revisionSets(projected),
            hostCatalogState = hostSnapshot.state,
        )
        lastSuccess = next
        publish(next)
        return true
    }

    fun refresh(
        registeredRecords: List<LayoutCatalogV2RegisteredRecord> = emptyList(),
        hostSnapshot: LayoutCatalogV2HostSnapshot =
            LayoutCatalogV2HostSnapshot(LayoutHostCatalogState.NOT_CONNECTED),
        context: LayoutCatalogV2Context? = null,
    ): LayoutCatalogV2UiState {
        val ticket = beginRefresh()
        completeRefresh(ticket, registeredRecords, hostSnapshot, context)
        return state
    }

    fun selectPreferredVariant(
        layoutId: String,
        revision: Long,
        variantId: String,
    ): LayoutPreferredVariantWriteResult {
        val result = catalog.selectPreferredVariant(layoutId, revision, variantId)
        publish(state.copy(preferenceAction = catalog.state.preferenceAction))
        return result
    }

    fun clearPreferredVariant(layoutId: String): LayoutPreferredVariantWriteResult {
        val result = catalog.clearPreferredVariant(layoutId)
        publish(state.copy(preferenceAction = catalog.state.preferenceAction))
        return result
    }

    fun close() {
        closed = true
        generation++
    }

    private fun sourceSummaries(
        packaged: LayoutCatalogV2PackagedSnapshot,
        registered: List<LayoutCatalogV2RegisteredRecord>,
        host: LayoutCatalogV2HostSnapshot,
        projected: LayoutCatalogV2UiState,
        reconciliationIssue: LayoutCatalogSourceIssueCode?,
    ): List<LayoutCatalogSourceSummary> {
        val packagedSummary = when (packaged) {
            LayoutCatalogV2PackagedSnapshot.Unavailable -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.PACKAGED_BUILT_IN,
                LayoutCatalogSourcePhase.UNAVAILABLE,
                0,
                false,
                LayoutCatalogSourceIssueCode.SOURCE_UNAVAILABLE,
            )
            LayoutCatalogV2PackagedSnapshot.InvalidManifest -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.PACKAGED_BUILT_IN,
                LayoutCatalogSourcePhase.FAILED,
                0,
                false,
                LayoutCatalogSourceIssueCode.INVALID_MANIFEST,
            )
        }
        val localCount = registered.count { it.origin == LayoutLocalOrigin.LOCAL_COPY }
        val localReady = projected.items.count {
            it.local.origin == LayoutLocalOrigin.LOCAL_COPY &&
                it.local.availability == LayoutLocalAvailability.READY
        }
        val localSummary = when {
            reconciliationIssue != null && localCount > 0 -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.LOCAL_COPY,
                LayoutCatalogSourcePhase.FAILED,
                0,
                false,
                reconciliationIssue,
            )
            localCount == 0 -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.LOCAL_COPY,
                LayoutCatalogSourcePhase.UNAVAILABLE,
                0,
                false,
            )
            localReady == localCount -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.LOCAL_COPY,
                LayoutCatalogSourcePhase.READY,
                localReady,
                false,
            )
            localReady > 0 -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.LOCAL_COPY,
                LayoutCatalogSourcePhase.PARTIAL,
                localReady,
                false,
            )
            else -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.LOCAL_COPY,
                LayoutCatalogSourcePhase.FAILED,
                0,
                false,
                LayoutCatalogSourceIssueCode.CONTENT_REJECTED,
            )
        }
        val hostSummary = if (reconciliationIssue != null && host.descriptors.isNotEmpty()) {
            LayoutCatalogSourceSummary(
                LayoutLocalOrigin.HOST_CATALOG,
                LayoutCatalogSourcePhase.FAILED,
                0,
                false,
                reconciliationIssue,
            )
        } else {
            hostSummary(host)
        }
        return listOf(packagedSummary, localSummary, hostSummary)
    }

    private fun hostSummary(host: LayoutCatalogV2HostSnapshot): LayoutCatalogSourceSummary =
        when (host.state) {
            LayoutHostCatalogState.NOT_CONNECTED -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.HOST_CATALOG,
                LayoutCatalogSourcePhase.UNAVAILABLE,
                0,
                false,
                LayoutCatalogSourceIssueCode.HOST_DISCONNECTED,
            )
            LayoutHostCatalogState.AUTHORIZED_LOADING -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.HOST_CATALOG,
                LayoutCatalogSourcePhase.LOADING,
                0,
                false,
            )
            LayoutHostCatalogState.AUTHORIZED_EMPTY -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.HOST_CATALOG,
                LayoutCatalogSourcePhase.READY,
                0,
                false,
                LayoutCatalogSourceIssueCode.HOST_AUTHORIZED_EMPTY,
            )
            LayoutHostCatalogState.AUTHORIZED_METADATA_READY -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.HOST_CATALOG,
                when {
                    validHostDescriptorCount(host) == 0 &&
                        host.descriptors.isNotEmpty() -> LayoutCatalogSourcePhase.FAILED
                    validHostDescriptorCount(host) < host.descriptors.size ->
                        LayoutCatalogSourcePhase.PARTIAL
                    else -> LayoutCatalogSourcePhase.READY
                },
                validHostDescriptorCount(host),
                false,
                if (
                    validHostDescriptorCount(host) < host.descriptors.size
                ) LayoutCatalogSourceIssueCode.INVALID_DESCRIPTOR else null,
            )
            LayoutHostCatalogState.PERMISSION_DENIED -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.HOST_CATALOG,
                LayoutCatalogSourcePhase.FAILED,
                0,
                false,
                LayoutCatalogSourceIssueCode.PERMISSION_DENIED,
            )
            LayoutHostCatalogState.FAILED -> LayoutCatalogSourceSummary(
                LayoutLocalOrigin.HOST_CATALOG,
                LayoutCatalogSourcePhase.FAILED,
                0,
                false,
                LayoutCatalogSourceIssueCode.HOST_SOURCE_FAILED,
            )
        }

    private fun validHostDescriptorCount(host: LayoutCatalogV2HostSnapshot): Int =
        host.descriptors.count {
            LayoutContractV1Validator.validateDescriptor(it) == null
        }

    private fun reconcile(sources: List<LayoutCatalogV2Source>): Reconciled {
        val result = mutableListOf<LayoutCatalogV2Source>()
        var issue: LayoutCatalogSourceIssueCode? = null
        sources.groupBy { it.descriptor.layoutId to it.descriptor.revision }
            .toSortedMap(compareBy<Pair<String, Long>>({ it.first }, { it.second }))
            .values
            .forEach { group ->
                val descriptors = group.map { it.descriptor }.distinct()
                if (descriptors.size != 1) {
                    issue = LayoutCatalogSourceIssueCode.CROSS_SOURCE_DESCRIPTOR_CONFLICT
                    return@forEach
                }
                val locals = group.filter { it.origin == LayoutLocalOrigin.LOCAL_COPY }
                val hosts = group.filter { it.origin == LayoutLocalOrigin.HOST_CATALOG }
                when {
                    locals.size > 1 -> {
                        issue = LayoutCatalogSourceIssueCode.DUPLICATE_SOURCE_RECORD
                    }
                    locals.size == 1 -> result += locals.single()
                    hosts.isNotEmpty() -> result += hosts.first()
                }
            }
        return Reconciled(result, issue)
    }

    private fun revisionSets(projected: LayoutCatalogV2UiState): List<LayoutCatalogRevisionSet> =
        projected.items.groupBy { it.layoutId }.map { (layoutId, items) ->
            val validity = when {
                items.any {
                    it.selection.code ==
                        LayoutVariantSelectionCode.WAITING_FOR_CONTEXT_VALIDATION &&
                        it.preferredVariantId != null
                } -> LayoutStoredPreferenceValidity.WAITING_FOR_CONTEXT_VALIDATION
                items.any { it.preferredVariantId != null } -> LayoutStoredPreferenceValidity.VALID
                projected.issues.any {
                    it.layoutId == layoutId &&
                        it.code == LayoutCatalogV2IssueCode.STALE_STORED_PREFERENCE
                } -> LayoutStoredPreferenceValidity.STALE
                projected.issues.any {
                    it.layoutId == layoutId &&
                        it.code == LayoutCatalogV2IssueCode.INVALID_STORED_PREFERENCE
                } -> LayoutStoredPreferenceValidity.INVALID
                else -> LayoutStoredPreferenceValidity.ABSENT
            }
            LayoutCatalogRevisionSet(
                layoutId,
                items.map { it.revision }.distinct().sorted(),
                validity,
            )
        }.sortedBy { it.layoutId }

    private fun publish(next: LayoutCatalogV2UiState) {
        state = next
        onStateChanged(next)
    }

    private data class Reconciled(
        val sources: List<LayoutCatalogV2Source>,
        val issue: LayoutCatalogSourceIssueCode?,
    )
}
