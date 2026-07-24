package com.limelight.ligase.feature.library.data.repository

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.limelight.ligase.feature.library.data.dto.ManualLibrarySortRequest
import com.limelight.ligase.feature.library.data.dto.ManualLibrarySortResponse
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.library.ManualLibrarySortValidator
import java.io.IOException

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

    private fun parseSafeInteger(element: JsonElement?): Long? {
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
