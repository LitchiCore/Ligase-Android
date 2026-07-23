package com.limelight.ligase.layout

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

object LayoutContractV1Json {
    fun resolve(json: String): LayoutResolutionV1 {
        val root = try {
            val reader = JsonReader(StringReader(json)).apply {
                strictness = Strictness.STRICT
            }
            JsonParser.parseReader(reader).also {
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw IllegalArgumentException("Trailing JSON content")
                }
            }
        } catch (_: Exception) {
            return LayoutResolutionV1(LayoutContractV1Codes.INVALID_JSON)
        }
        if (!root.isJsonObject) {
            return LayoutResolutionV1(LayoutContractV1Codes.INVALID_SCHEMA)
        }

        val unknownFields = mutableListOf<String>()
        collectUnknownFields(root, "", Shape.REQUEST, unknownFields)
        if (unknownFields.isNotEmpty()) {
            return LayoutResolutionV1(
                code = LayoutContractV1Codes.UNKNOWN_FIELD,
                detail = unknownFields.minWithOrNull(::compareCodePoints),
            )
        }
        if (hasInvalidRevisionToken(root.asJsonObject)) {
            return LayoutResolutionV1(LayoutContractV1Codes.INVALID_REVISION)
        }
        val request = parseRequest(root.asJsonObject)
            ?: return LayoutResolutionV1(LayoutContractV1Codes.INVALID_SCHEMA)
        return LayoutContractV1Resolver.resolve(request)
    }

    fun serializeResult(result: LayoutResolutionV1): String {
        val json = JsonObject()
        json.addProperty("code", result.code)
        result.source?.let { json.addProperty("source", it) }
        result.layoutId?.let { json.addProperty("layoutId", it) }
        result.revision?.let { json.addProperty("revision", it) }
        result.variantId?.let { json.addProperty("variantId", it) }
        result.detail?.let { json.addProperty("detail", it) }
        return json.toString()
    }

    private fun parseRequest(root: JsonObject): LayoutResolutionRequestV1? {
        val schemaVersion = readInt32(root, "schemaVersion") ?: return null
        if (schemaVersion != 1) return null
        val instance = getObject(root, "instance") ?: return null
        val hostUniqueId = readString(instance, "hostUniqueId") ?: return null
        val appUuid = readString(instance, "appUuid") ?: return null
        val context = parseContext(getObject(root, "context") ?: return null) ?: return null
        val descriptorElements = getArray(root, "descriptors") ?: return null

        val portableIdentity = when (val element = root.get("portableIdentity")) {
            null, is JsonNull -> null
            else -> parsePortableIdentity(element) ?: return null
        }
        val binding = when (val element = root.get("layoutBinding")) {
            null, is JsonNull -> null
            else -> parseBinding(element) ?: return null
        }
        val descriptors = descriptorElements.map { element ->
            parseDescriptor(element) ?: return null
        }
        return LayoutResolutionRequestV1(
            hostUniqueId = hostUniqueId,
            appUuid = appUuid,
            portableIdentity = portableIdentity,
            layoutBinding = binding,
            context = context,
            descriptors = descriptors,
        )
    }

    private fun parseContext(element: JsonObject): LayoutResolutionContextV1? {
        val clientVersion = readInt32(element, "clientContractVersion") ?: return null
        val runtimeVersion = readInt32(element, "layoutRuntimeVersion") ?: return null
        val inputProfile = readString(element, "inputProfile") ?: return null
        val deviceClass = readString(element, "deviceClass") ?: return null
        val orientation = readString(element, "orientation") ?: return null

        val installedDrafts = mutableSetOf<LayoutRevisionV1>()
        element.get("installedDrafts")?.let { drafts ->
            if (!drafts.isJsonArray) return null
            drafts.asJsonArray.forEach { draftElement ->
                if (!draftElement.isJsonObject) return null
                val draft = draftElement.asJsonObject
                val layoutId = readString(draft, "layoutId") ?: return null
                val revision = readRevision(draft, "revision") ?: return null
                installedDrafts += LayoutRevisionV1(layoutId, revision)
            }
        }

        val preference = when (val preferred = element.get("preferredVariant")) {
            null, is JsonNull -> null
            else -> {
                if (!preferred.isJsonObject) return null
                val value = preferred.asJsonObject
                LayoutPreferenceV1(
                    layoutId = readString(value, "layoutId") ?: return null,
                    revision = readRevision(value, "revision") ?: return null,
                    variantId = readString(value, "variantId") ?: return null,
                )
            }
        }
        return LayoutResolutionContextV1(
            clientContractVersion = clientVersion,
            layoutRuntimeVersion = runtimeVersion,
            inputProfile = inputProfile,
            deviceClass = deviceClass,
            orientation = orientation,
            installedDrafts = installedDrafts,
            preference = preference,
        )
    }

    private fun parseDescriptor(element: JsonElement): LayoutDescriptorV1? {
        if (!element.isJsonObject) return null
        val value = element.asJsonObject
        val schemaVersion = readInt32(value, "schemaVersion") ?: return null
        val layoutId = readString(value, "layoutId") ?: return null
        val revision = readRevision(value, "revision") ?: return null
        val identityElements = getArray(value, "portableIdentities") ?: return null
        val compatibilityElement = getObject(value, "compatibility") ?: return null
        val minClientVersion =
            readInt32(compatibilityElement, "minClientContractVersion") ?: return null
        val minRuntimeVersion =
            readInt32(compatibilityElement, "minLayoutRuntimeVersion") ?: return null
        val publicationStatus = readString(value, "publicationStatus") ?: return null
        val variantElements = getArray(value, "variants") ?: return null

        val identities = identityElements.map { identity ->
            parsePortableIdentity(identity) ?: return null
        }
        val variants = variantElements.map { variantElement ->
            if (!variantElement.isJsonObject) return null
            val variant = variantElement.asJsonObject
            LayoutVariantV1(
                variantId = readString(variant, "variantId") ?: return null,
                inputProfile = readString(variant, "inputProfile") ?: return null,
                deviceClasses = readStringArray(variant, "deviceClasses") ?: return null,
                orientations = readStringArray(variant, "orientations") ?: return null,
            )
        }
        return LayoutDescriptorV1(
            schemaVersion = schemaVersion,
            layoutId = layoutId,
            revision = revision,
            portableIdentities = identities,
            compatibility = LayoutCompatibilityV1(minClientVersion, minRuntimeVersion),
            publicationStatus = publicationStatus,
            variants = variants,
        )
    }

    private fun parsePortableIdentity(element: JsonElement): PortableGameIdentityV1? {
        if (!element.isJsonObject) return null
        val value = element.asJsonObject
        return PortableGameIdentityV1(
            provider = readString(value, "provider") ?: return null,
            id = readString(value, "id") ?: return null,
        )
    }

    private fun parseBinding(element: JsonElement): LayoutBindingV1? {
        if (!element.isJsonObject) return null
        val value = element.asJsonObject
        return LayoutBindingV1(
            layoutId = readString(value, "layoutId") ?: return null,
            revision = readRevision(value, "revision") ?: return null,
        )
    }

    private fun hasInvalidRevisionToken(root: JsonObject): Boolean {
        val revisions = mutableListOf<JsonElement>()
        collectRevisionTokens(root, Shape.REQUEST, revisions)
        return revisions.any { element ->
            !element.isJsonPrimitive ||
                !element.asJsonPrimitive.isNumber ||
                !isIntegerToken(element.toString()) ||
                element.toString().toLongOrNull()?.let(
                    LayoutContractV1Validator::isValidRevision,
                ) != true
        }
    }

    private fun collectRevisionTokens(
        element: JsonElement,
        shape: Shape,
        revisions: MutableList<JsonElement>,
    ) {
        if (!element.isJsonObject) return
        val value = element.asJsonObject
        if (
            shape in setOf(Shape.BINDING, Shape.PREFERENCE, Shape.DRAFT, Shape.DESCRIPTOR)
        ) {
            value.get("revision")?.let(revisions::add)
        }
        value.entrySet().forEach { (property, child) ->
            val childShape = childShape(shape, property)
            if (childShape == Shape.NONE) return@forEach
            if (childShape.isArrayShape) {
                if (!child.isJsonArray) return@forEach
                val itemShape = arrayItemShape(childShape)
                child.asJsonArray.forEach { item ->
                    collectRevisionTokens(item, itemShape, revisions)
                }
            } else {
                collectRevisionTokens(child, childShape, revisions)
            }
        }
    }

    private fun collectUnknownFields(
        element: JsonElement,
        pointer: String,
        shape: Shape,
        unknownFields: MutableList<String>,
    ) {
        if (!element.isJsonObject) return
        val allowed = allowedFields(shape)
        element.asJsonObject.entrySet().forEach { (property, child) ->
            val propertyPointer = "$pointer/${escapePointer(property)}"
            if (property !in allowed) {
                unknownFields += propertyPointer
                return@forEach
            }
            val childShape = childShape(shape, property)
            if (childShape == Shape.NONE) return@forEach
            if (childShape.isArrayShape) {
                if (!child.isJsonArray) return@forEach
                val itemShape = arrayItemShape(childShape)
                child.asJsonArray.forEachIndexed { index, item ->
                    collectUnknownFields(item, "$propertyPointer/$index", itemShape, unknownFields)
                }
            } else {
                collectUnknownFields(child, propertyPointer, childShape, unknownFields)
            }
        }
    }

    private fun allowedFields(shape: Shape): Set<String> = when (shape) {
        Shape.REQUEST -> setOf(
            "schemaVersion",
            "instance",
            "portableIdentity",
            "layoutBinding",
            "context",
            "descriptors",
        )
        Shape.INSTANCE -> setOf("hostUniqueId", "appUuid")
        Shape.IDENTITY -> setOf("provider", "id")
        Shape.BINDING, Shape.DRAFT -> setOf("layoutId", "revision")
        Shape.CONTEXT -> setOf(
            "clientContractVersion",
            "layoutRuntimeVersion",
            "inputProfile",
            "deviceClass",
            "orientation",
            "installedDrafts",
            "preferredVariant",
        )
        Shape.PREFERENCE -> setOf("layoutId", "revision", "variantId")
        Shape.DESCRIPTOR -> setOf(
            "schemaVersion",
            "layoutId",
            "revision",
            "portableIdentities",
            "compatibility",
            "publicationStatus",
            "variants",
        )
        Shape.COMPATIBILITY -> setOf(
            "minClientContractVersion",
            "minLayoutRuntimeVersion",
        )
        Shape.VARIANT -> setOf(
            "variantId",
            "inputProfile",
            "deviceClasses",
            "orientations",
        )
        else -> emptySet()
    }

    private fun childShape(shape: Shape, property: String): Shape = when {
        shape == Shape.REQUEST && property == "instance" -> Shape.INSTANCE
        shape == Shape.REQUEST && property == "portableIdentity" -> Shape.IDENTITY
        shape == Shape.REQUEST && property == "layoutBinding" -> Shape.BINDING
        shape == Shape.REQUEST && property == "context" -> Shape.CONTEXT
        shape == Shape.REQUEST && property == "descriptors" -> Shape.DESCRIPTOR_ARRAY
        shape == Shape.CONTEXT && property == "installedDrafts" -> Shape.DRAFT_ARRAY
        shape == Shape.CONTEXT && property == "preferredVariant" -> Shape.PREFERENCE
        shape == Shape.DESCRIPTOR && property == "portableIdentities" ->
            Shape.IDENTITY_ARRAY
        shape == Shape.DESCRIPTOR && property == "compatibility" -> Shape.COMPATIBILITY
        shape == Shape.DESCRIPTOR && property == "variants" -> Shape.VARIANT_ARRAY
        else -> Shape.NONE
    }

    private fun arrayItemShape(shape: Shape): Shape = when (shape) {
        Shape.DESCRIPTOR_ARRAY -> Shape.DESCRIPTOR
        Shape.VARIANT_ARRAY -> Shape.VARIANT
        Shape.IDENTITY_ARRAY -> Shape.IDENTITY
        Shape.DRAFT_ARRAY -> Shape.DRAFT
        else -> Shape.NONE
    }

    private fun escapePointer(value: String): String =
        value.replace("~", "~0").replace("/", "~1")

    private fun compareCodePoints(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            val leftCodePoint = Character.codePointAt(left, leftIndex)
            val rightCodePoint = Character.codePointAt(right, rightIndex)
            if (leftCodePoint != rightCodePoint) {
                return leftCodePoint.compareTo(rightCodePoint)
            }
            leftIndex += Character.charCount(leftCodePoint)
            rightIndex += Character.charCount(rightCodePoint)
        }
        return (left.length - leftIndex).compareTo(right.length - rightIndex)
    }

    private fun readString(element: JsonObject, property: String): String? {
        val value = element.get(property) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            value.asString
        } else {
            null
        }
    }

    private fun readInt32(element: JsonObject, property: String): Int? {
        val value = element.get(property) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        val token = value.toString()
        if (!isIntegerToken(token)) return null
        return token.toIntOrNull()
    }

    private fun readRevision(element: JsonObject, property: String): Long? {
        val value = element.get(property) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        return value.toString().toLongOrNull()
    }

    private fun getObject(element: JsonObject, property: String): JsonObject? {
        val value = element.get(property) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
    }

    private fun getArray(element: JsonObject, property: String): JsonArray? {
        val value = element.get(property) ?: return null
        return if (value.isJsonArray) value.asJsonArray else null
    }

    private fun readStringArray(element: JsonObject, property: String): List<String>? {
        val values = getArray(element, property) ?: return null
        return values.map { value ->
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
            value.asString
        }
    }

    private fun isIntegerToken(token: String): Boolean =
        token.isNotEmpty() &&
            '.' !in token &&
            'e' !in token &&
            'E' !in token &&
            token.toLongOrNull() != null

    private enum class Shape(val isArrayShape: Boolean = false) {
        NONE,
        REQUEST,
        INSTANCE,
        IDENTITY,
        BINDING,
        CONTEXT,
        PREFERENCE,
        DRAFT,
        DESCRIPTOR,
        COMPATIBILITY,
        VARIANT,
        DESCRIPTOR_ARRAY(true),
        VARIANT_ARRAY(true),
        IDENTITY_ARRAY(true),
        DRAFT_ARRAY(true),
    }
}
