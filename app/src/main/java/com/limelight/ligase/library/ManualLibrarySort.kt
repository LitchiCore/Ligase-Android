package com.limelight.ligase.library

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.limelight.nvstream.http.HostHttpResponseException
import java.io.IOException
import java.util.Locale

data class ManualLibrarySortRequest(
    val baseRevision: Long,
    val orderedAppUuids: List<String>,
)

data class ManualLibrarySortResponse(
    val revision: Long,
    val sortMode: String,
    val orderedAppUuids: List<String>,
)

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

internal object ManualLibrarySortCodec {
    private val responseFields = setOf("revision", "sortMode", "orderedAppUuids")

    fun encodeRequest(request: ManualLibrarySortRequest, gson: Gson): String =
        gson.toJson(request)

    fun parseResponse(
        json: String,
        expectedOrder: List<String>,
    ): ManualLibrarySortResponse {
        val root = try {
            JsonParser.parseString(json)
        } catch (error: RuntimeException) {
            throw IOException("Invalid manual library order response", error)
        }
        if (!root.isJsonObject) throw IOException("Manual library order response is not an object")
        val objectValue = root.asJsonObject
        if (objectValue.keySet() != responseFields) {
            throw IOException("Manual library order response has unexpected fields")
        }
        val revision = parseSafeInteger(objectValue.get("revision"))
            ?: throw IOException("Manual library order response has an invalid revision")
        val sortMode = objectValue.get("sortMode")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        if (sortMode != HostSortMode.MANUAL.wireValue) {
            throw IOException("Manual library order response has an invalid sort mode")
        }
        val orderElement = objectValue.get("orderedAppUuids")
        if (orderElement == null || !orderElement.isJsonArray) {
            throw IOException("Manual library order response has no UUID sequence")
        }
        val order = orderElement.asJsonArray.map { element ->
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
                throw IOException("Manual library order response contains a non-string UUID")
            }
            element.asString
        }
        if (
            order.any { !ManualLibrarySortValidator.isCanonicalUuid(it) } ||
            order.distinct().size != order.size ||
            order != expectedOrder
        ) {
            throw IOException("Manual library order response does not match the accepted request")
        }
        return ManualLibrarySortResponse(
            revision = revision,
            sortMode = sortMode,
            orderedAppUuids = order,
        )
    }

    fun parseConflictRevision(json: String?): Long? {
        if (json.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return null
        if (root.keySet() != setOf("error", "currentRevision")) return null
        val error = root.get("error")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        if (error != "revisionConflict") return null
        return parseSafeInteger(root.get("currentRevision"))
    }

    private fun parseSafeInteger(element: com.google.gson.JsonElement?): Long? {
        if (
            element == null ||
            !element.isJsonPrimitive ||
            !element.asJsonPrimitive.isNumber
        ) {
            return null
        }
        val token = element.asString
        if (!token.matches(Regex("^[1-9][0-9]*$"))) return null
        return token.toLongOrNull()?.takeIf(ManualLibrarySortValidator::isSafeRevision)
    }
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
