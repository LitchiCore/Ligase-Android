package com.limelight.ligase.library

import com.limelight.ligase.feature.library.data.dto.ManualLibrarySortRequest
import com.limelight.ligase.feature.library.data.dto.ManualLibrarySortResponse
import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.ligase.feature.library.data.repository.ManualLibrarySortCodec
import com.limelight.ligase.feature.library.domain.HostLibraryKind
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.nvstream.http.HostHttpResponseException
import java.io.IOException
import java.util.Locale

enum class ManualLibrarySortError {
    INVALID_ORDER,
    REVISION_CONFLICT,
    PERMISSION_DENIED,
    FAILED,
}

data class ManualLibrarySortActionState(
    val saving: Boolean = false,
    val error: ManualLibrarySortError? = null,
    val appliedRevision: Long? = null,
)

sealed interface ManualLibrarySortResult {
    data class Success(val response: ManualLibrarySortResponse) : ManualLibrarySortResult
    data class RevisionConflict(val currentRevision: Long?) : ManualLibrarySortResult
    data object PermissionDenied : ManualLibrarySortResult
    data object InvalidOrder : ManualLibrarySortResult
    data object Failed : ManualLibrarySortResult
}

object ManualLibrarySortValidator {
    const val MAX_SAFE_REVISION = 9_007_199_254_740_991L

    private val canonicalUuid =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun expectedPublishedAppUuids(snapshot: LigaseSyncSnapshotDto): List<String>? {
        if (!isSafeRevision(snapshot.library.revision)) return null
        val result = ArrayList<String>()
        val unique = HashSet<String>()
        for (item in snapshot.library.items) {
            HostLibraryKind.fromWireValue(item.kind) ?: return null
            if (item.publishedToClients == false) continue
            if (!isCanonicalUuid(item.id) || !unique.add(item.id)) return null
            result += item.id
        }
        return result
    }

    fun validateRequest(
        snapshot: LigaseSyncSnapshotDto,
        orderedAppUuids: List<String>,
    ): ManualLibrarySortRequest? {
        val expected = expectedPublishedAppUuids(snapshot) ?: return null
        if (orderedAppUuids.any { !isCanonicalUuid(it) }) return null
        if (orderedAppUuids.distinct().size != orderedAppUuids.size) return null
        if (orderedAppUuids.size != expected.size) return null
        if (orderedAppUuids.toSet() != expected.toSet()) return null
        return ManualLibrarySortRequest(
            baseRevision = snapshot.library.revision,
            orderedAppUuids = orderedAppUuids.toList(),
        )
    }

    fun isSafeRevision(value: Long): Boolean = value in 1..MAX_SAFE_REVISION

    fun isCanonicalUuid(value: String): Boolean = canonicalUuid.matches(value)
}

class ManualLibrarySortAction {
    fun submit(
        snapshot: LigaseSyncSnapshotDto,
        orderedAppUuids: List<String>,
        writer: (ManualLibrarySortRequest) -> ManualLibrarySortResponse,
    ): ManualLibrarySortResult {
        val request = ManualLibrarySortValidator.validateRequest(snapshot, orderedAppUuids)
            ?: return ManualLibrarySortResult.InvalidOrder
        return try {
            val response = writer(request)
            if (
                response.sortMode != HostSortMode.MANUAL.wireValue ||
                !ManualLibrarySortValidator.isSafeRevision(response.revision) ||
                response.revision <= request.baseRevision ||
                response.orderedAppUuids != request.orderedAppUuids
            ) {
                ManualLibrarySortResult.Failed
            } else {
                ManualLibrarySortResult.Success(response)
            }
        } catch (error: HostHttpResponseException) {
            when (error.errorCode) {
                400 -> ManualLibrarySortResult.InvalidOrder
                403 -> ManualLibrarySortResult.PermissionDenied
                409 -> ManualLibrarySortResult.RevisionConflict(
                    ManualLibrarySortCodec.parseConflictRevision(error.responseBody),
                )
                else -> ManualLibrarySortResult.Failed
            }
        } catch (_: IOException) {
            ManualLibrarySortResult.Failed
        }
    }
}

class ManualLibrarySortCoordinator {
    class Ticket internal constructor(
        internal val hostKey: String,
        internal val generation: Long,
    )

    var state: ManualLibrarySortActionState = ManualLibrarySortActionState()
        private set

    private var hostKey: String? = null
    private var generation = 0L
    private var activeTicket: Ticket? = null

    fun selectHost(hostUniqueId: String?) {
        val normalized = hostUniqueId?.trim()?.lowercase(Locale.ROOT)
        if (normalized == hostKey) return
        hostKey = normalized
        generation++
        activeTicket = null
        state = ManualLibrarySortActionState()
    }

    fun begin(hostUniqueId: String): Ticket? {
        val normalized = hostUniqueId.trim().lowercase(Locale.ROOT)
        if (normalized != hostKey || activeTicket != null) return null
        return Ticket(normalized, ++generation).also {
            activeTicket = it
            state = ManualLibrarySortActionState(saving = true)
        }
    }

    fun accept(ticket: Ticket, result: ManualLibrarySortResult): Boolean {
        if (ticket != activeTicket || ticket.hostKey != hostKey) return false
        activeTicket = null
        state = when (result) {
            is ManualLibrarySortResult.Success -> ManualLibrarySortActionState(
                saving = false,
                appliedRevision = result.response.revision,
            )
            is ManualLibrarySortResult.RevisionConflict -> ManualLibrarySortActionState(
                saving = false,
                error = ManualLibrarySortError.REVISION_CONFLICT,
            )
            ManualLibrarySortResult.PermissionDenied -> ManualLibrarySortActionState(
                saving = false,
                error = ManualLibrarySortError.PERMISSION_DENIED,
            )
            ManualLibrarySortResult.InvalidOrder -> ManualLibrarySortActionState(
                saving = false,
                error = ManualLibrarySortError.INVALID_ORDER,
            )
            ManualLibrarySortResult.Failed -> ManualLibrarySortActionState(
                saving = false,
                error = ManualLibrarySortError.FAILED,
            )
        }
        return true
    }
}
