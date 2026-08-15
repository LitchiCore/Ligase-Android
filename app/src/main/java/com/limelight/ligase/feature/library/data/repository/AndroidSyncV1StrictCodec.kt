package com.limelight.ligase.feature.library.data.repository

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import java.io.IOException

internal object AndroidSyncV1StrictCodec {
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val sha256 = Regex("^[0-9a-f]{64}$")
    private val canonicalPositiveDecimal = Regex("^[1-9][0-9]{0,9}$")
    private val integer = Regex("^-?(?:0|[1-9][0-9]*)$")
    private val timestamp = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})$")

    fun parse(json: String, gson: Gson): LigaseSyncSnapshotDto {
        val root = try {
            JsonParser.parseString(json).requiredObject("snapshot")
        } catch (error: RuntimeException) {
            throw IOException("Host returned malformed Android Sync v1", error)
        }
        validateRoot(root)
        return gson.fromJson(root, LigaseSyncSnapshotDto::class.java)
            ?: throw IOException("Ligase sync returned an empty snapshot")
    }

    private fun validateRoot(root: JsonObject) {
        root.closed("schemaVersion", "capabilities", "library", "streaming")
        root.required("schemaVersion", "capabilities", "library", "streaming")
        root.integer("schemaVersion", 1, 1)
        root.obj("capabilities").apply {
            closed("hdrEncodingSupported")
            required("hdrEncodingSupported")
            bool("hdrEncodingSupported")
        }
        root.obj("library").let(::validateLibrary)
        root.obj("streaming").let(::validateStreaming)
    }

    private fun validateLibrary(value: JsonObject) {
        value.closed("revision", "updatedAt", "sortMode", "items")
        value.required("revision", "updatedAt", "sortMode", "items")
        value.integer("revision", 0, MAX_SAFE_INTEGER)
        value.string("updatedAt", timestamp)
        value.enum("sortMode", SORT_MODES)
        val items = value.array("items")
        val ids = HashSet<String>()
        items.forEach { element ->
            val item = element.requiredObject("library item")
            validateItem(item)
            if (!ids.add(item.stringValue("id"))) fail("duplicate app UUID")
        }
    }

    private fun validateItem(item: JsonObject) {
        item.closed(*ITEM_FIELDS)
        item.required("id", "kind", "name", "system", "publishedToClients", "addedAt", "updatedAt")
        item.string("id", uuid)
        val kind = item.enum("kind", ITEM_KINDS)
        item.string("name", 1, 256)
        item.bool("system")
        item.bool("publishedToClients")
        item.string("addedAt", timestamp)
        item.string("updatedAt", timestamp)
        item.optionalString("lastPlayedAt", timestamp)

        val steamId = item.optionalInteger("steamAppId", 1, UINT32_MAX)
        val identity = item.optionalObject("portableIdentity")
        if (kind == "steam") {
            if (steamId == null || identity == null) fail("Steam identity is missing")
            identity.closed("provider", "id")
            identity.required("provider", "id")
            if (identity.stringValue("provider") != "steam") fail("unsupported portable identity")
            val id = identity.stringValue("id")
            if (!canonicalPositiveDecimal.matches(id) || id.toLongOrNull() !in 1..UINT32_MAX || id != steamId.toString()) {
                fail("invalid portable identity")
            }
        } else if (steamId != null || identity != null) {
            fail("portable identity on a non-Steam item")
        }

        item.optionalObject("layoutBinding")?.apply {
            closed("layoutId", "revision")
            required("layoutId", "revision")
            string("layoutId", uuid)
            integer("revision", 1, MAX_SAFE_INTEGER)
        }

        val coverNames = listOf("coverSha256", "coverSourceKind", "coverSourceId", "coverUsageRights")
        val coverCount = coverNames.count(item::has)
        if (coverCount != 0 && coverCount != coverNames.size) fail("partial cover authority")
        if (coverCount == coverNames.size) {
            item.string("coverSha256", sha256)
            val sourceKind = item.enum("coverSourceKind", COVER_SOURCE_KINDS)
            val sourceId = item.string("coverSourceId", 1, 128)
            val usage = item.enum("coverUsageRights", COVER_USAGE_RIGHTS)
            if (sourceKind == "steamClientLibraryCache" &&
                (kind != "steam" || sourceId != steamId.toString() || usage != "thirdPartyArtworkLocalUseOnlyNoRedistribution")
            ) fail("uncorrelated Steam cover authority")
            if (sourceKind == "gameDbIgdb" && usage != "thirdPartyArtworkLocalCacheOnly") {
                fail("invalid GameDB cover usage")
            }
        }
    }

    private fun validateStreaming(value: JsonObject) {
        value.closed("schemaVersion", "revision", "updatedAt", "globalResolution", "apps")
        value.required("schemaVersion", "revision", "updatedAt", "globalResolution", "apps")
        value.integer("schemaVersion", 1, 1)
        value.integer("revision", 0, MAX_SAFE_INTEGER)
        value.string("updatedAt", timestamp)
        validateResolution(value.obj("globalResolution"))
        value.obj("apps").entrySet().forEach { (key, appValue) ->
            if (!uuid.matches(key)) fail("invalid streaming app UUID")
            val app = appValue.requiredObject("streaming app")
            app.closed("resolution")
            app.optionalObject("resolution")?.let(::validateResolution)
        }
    }

    private fun validateResolution(value: JsonObject) {
        value.closed("width", "height")
        value.required("width", "height")
        value.integer("width", 320, 16384)
        value.integer("height", 240, 16384)
    }

    private fun JsonObject.closed(vararg allowed: String) {
        val extras = keySet() - allowed.toSet()
        if (extras.isNotEmpty()) fail("unknown fields")
    }
    private fun JsonObject.required(vararg names: String) {
        if (names.any { !has(it) || get(it).isJsonNull }) fail("missing required field")
    }
    private fun JsonObject.obj(name: String) = get(name)?.requiredObject(name) ?: fail("missing object")
    private fun JsonObject.optionalObject(name: String): JsonObject? =
        if (!has(name)) null else get(name).requiredObject(name)
    private fun JsonObject.array(name: String) = get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray
        ?: fail("invalid array")
    private fun JsonObject.bool(name: String) {
        val value = get(name)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) fail("invalid boolean")
    }
    private fun JsonObject.integer(name: String, min: Long, max: Long): Long =
        optionalInteger(name, min, max) ?: fail("missing integer")
    private fun JsonObject.optionalInteger(name: String, min: Long, max: Long): Long? {
        if (!has(name)) return null
        val value = get(name)
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber || !integer.matches(value.toString())) fail("invalid integer")
        return value.asLong.takeIf { it in min..max } ?: fail("integer outside contract")
    }
    private fun JsonObject.string(name: String, pattern: Regex): String =
        stringValue(name).also { if (!pattern.matches(it)) fail("invalid string") }
    private fun JsonObject.optionalString(name: String, pattern: Regex): String? =
        if (!has(name)) null else string(name, pattern)
    private fun JsonObject.string(name: String, min: Int, max: Int): String =
        stringValue(name).also { if (it.length !in min..max) fail("string outside contract") }
    private fun JsonObject.stringValue(name: String): String {
        val value = get(name)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) fail("invalid string")
        return value.asString
    }
    private fun JsonObject.enum(name: String, allowed: Set<String>): String =
        stringValue(name).also { if (it !in allowed) fail("unsupported enum") }
    private fun JsonElement.requiredObject(label: String): JsonObject =
        takeIf(JsonElement::isJsonObject)?.asJsonObject ?: fail("invalid $label")
    private fun fail(message: String): Nothing = throw IOException("Host Android Sync v1 rejected: $message")

    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    private const val UINT32_MAX = 4_294_967_295L
    private val SORT_MODES = setOf("nameAscending", "nameDescending", "addedNewest", "addedOldest", "lastPlayedNewest", "manual")
    private val ITEM_KINDS = setOf("desktop", "virtualDesktop", "steam", "executable")
    private val COVER_SOURCE_KINDS = setOf("steamClientLibraryCache", "gameDbIgdb")
    private val COVER_USAGE_RIGHTS = setOf("thirdPartyArtworkLocalUseOnlyNoRedistribution", "thirdPartyArtworkLocalCacheOnly")
    private val ITEM_FIELDS = arrayOf("id", "kind", "name", "steamAppId", "portableIdentity", "layoutBinding", "coverSha256", "coverSourceKind", "coverSourceId", "coverUsageRights", "system", "publishedToClients", "addedAt", "updatedAt", "lastPlayedAt")
}
