package com.limelight.ligase.feature.input.layout.v2

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.serialization.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest

class TouchLayoutV2CoreTest {
    @Test
    fun frozenManifestAndPositiveArtifactMatchAndDecode() {
        val manifest = fixture("ligase-touch-layout-v2-sha256.txt").readLines()
        assertEquals(12, manifest.size)
        manifest.forEach { line ->
            val (expected, relative) = line.split("  ", limit = 2)
            assertEquals(relative, expected, sha256(repoRoot().resolve(relative).readBytes()))
        }
        val document = TouchLayoutV2Codec.decode(positiveBytes())
        assertEquals("00000000-0000-0000-0000-000000000001", document.layoutId)
        assertEquals((0..9).toSet(), document.variants.single().elements.map { it.kind.ordinal }.toSet())
        assertEquals("sha256:36d5968ce673b96b936bbf9e96c1d774786231c9509220defb34c1123de9a541",
            "sha256:" + sha256(positiveBytes()))
    }

    @Test
    fun allRawAndJcsVectorsAreMechanical() {
        val vectors = vectors()
        vectors["rawCases"].asJsonArray.forEach { entry ->
            val case = entry.asJsonObject
            val actual = rejectedCode(hex(case["hex"].asString))
            assertEquals(case["id"].asString, case["expected"].asString, actual)
        }
        vectors["jcsCases"].asJsonArray.forEach { entry ->
            val case = entry.asJsonObject
            val actual = TouchLayoutV2Jcs.canonicalBytes(toStrict(case["input"])).toString(Charsets.UTF_8)
            assertEquals(case["id"].asString, case["expected"].asString, actual)
        }
    }

    @Test
    fun everyDocumentAndKindVectorIsRejectedOrAcceptedAsDeclared() {
        val vectors = vectors()
        assertEquals(36, vectors["documentCases"].asJsonArray.size())
        vectors["documentCases"].asJsonArray.forEach { entry ->
            val case = entry.asJsonObject
            if (case["id"].asString == "invalid-unicode-surrogate") {
                val raw = positiveBytes().toString(Charsets.UTF_8).replace(
                    "\"displayName\": \"Built-in all-types conversion fixture\"",
                    "\"displayName\": \"\\ud800\"",
                ).toByteArray()
                assertEquals(case["id"].asString, "invalidString", rejectedCode(raw))
                return@forEach
            }
            val candidate = JsonParser.parseString(positiveBytes().toString(Charsets.UTF_8)).asJsonObject
            case["mutations"]?.asJsonArray?.forEach { applyMutation(candidate, it.asJsonObject) }
            if (case["rehash"]?.asBoolean == true) rehash(candidate)
            assertCase(case, candidate.toString().toByteArray())
        }
        assertEquals(39, vectors["kindCases"].asJsonArray.size())
        vectors["kindCases"].asJsonArray.forEach { entry ->
            val case = entry.asJsonObject
            val candidate = JsonParser.parseString(positiveBytes().toString(Charsets.UTF_8)).asJsonObject
            val payload = candidate["variants"].asJsonArray[0].asJsonObject["elements"].asJsonArray[
                case["elementIndex"].asInt
            ].asJsonObject["payload"].asJsonObject
            applyMutation(payload, case["mutation"].asJsonObject)
            rehash(candidate)
            assertCase(case, candidate.toString().toByteArray())
        }
    }

    @Test
    fun viewportAndDescriptorVectorsAreMechanical() {
        val document = TouchLayoutV2Codec.decode(positiveBytes())
        val vectors = vectors()
        vectors["viewportCases"].asJsonArray.forEach { entry ->
            val case = entry.asJsonObject
            val elementJson = case["element"].asJsonObject
            val element = document.variants.single().elements.first().copy(
                rect = rect(elementJson["rect"].asJsonObject),
                horizontalAnchor = HorizontalAnchor.valueOf(elementJson["horizontalAnchor"].asString.uppercase()),
                verticalAnchor = VerticalAnchor.valueOf(elementJson["verticalAnchor"].asString.uppercase()),
            )
            val policy = when (case["safeAreaPolicy"].asString) {
                "videoContent" -> SafeAreaPolicy.VIDEO_CONTENT
                else -> SafeAreaPolicy.VIDEO_CONTENT_AND_SYSTEM_INSETS
            }
            val result = runCatching {
                TouchLayoutV2ViewportMapper.map(
                    size(case["canvas"].asJsonObject),
                    element,
                    policy,
                    ViewportInput(
                        rect(case["videoContentRect"].asJsonObject),
                        rect(case["systemSafeRect"].asJsonObject),
                    ),
                ).rect
            }
            if (case.has("expectedRect")) {
                assertEquals(case["id"].asString, rect(case["expectedRect"].asJsonObject), result.getOrThrow())
            } else {
                assertEquals(case["id"].asString, case["expectedError"].asString,
                    (result.exceptionOrNull() as TouchLayoutV2Exception).code)
            }
        }
        vectors["descriptorCases"].asJsonArray.forEach { entry ->
            val case = entry.asJsonObject
            val descriptorJson = case["descriptor"].asJsonObject
            val descriptor = LayoutDescriptorProjection(
                descriptorJson["layoutId"].asString,
                descriptorJson["revision"].asLong,
                descriptorJson["variants"].asJsonArray.mapNotNull { raw ->
                    val item = raw.asJsonObject
                    if (item["inputProfile"].asString != "touch") null else DescriptorVariantProjection(
                        item["variantId"].asString,
                        item["deviceClasses"].asJsonArray.map { DeviceClass.valueOf(it.asString.uppercase()) },
                        item["orientations"].asJsonArray.map { LayoutOrientation.valueOf(it.asString.uppercase()) },
                    )
                },
            )
            val actual = runCatching { TouchLayoutV2Validator.validateDescriptorAlignment(document, descriptor) }
                .exceptionOrNull()
                ?.let { (it as TouchLayoutV2Exception).code }
                ?: "accepted"
            assertEquals(case["id"].asString, case["expected"].asString, actual)
        }
    }

    @Test
    fun legacyGenshinIsRejectedAndOnlyDiagnosticReportExists() {
        assertTrue(
            rejectedCode(fixture("touch-layout-v1-genshin-impact-phone.json").readBytes()) != "accepted",
        )
        val report = JsonParser.parseString(
            fixture("ligase-touch-layout-v2-genshin-impact-phone.report.json").readText(),
        ).asJsonObject
        assertEquals("collisionDetected", report["reason"].asString)
        assertEquals("19", report["detail"].asString)
        assertFalse(fixture("ligase-touch-layout-v2-genshin-impact-phone.json").exists())
    }

    private fun assertCase(case: JsonObject, raw: ByteArray) {
        val expected = case["expected"].asString
        val actual = runCatching { TouchLayoutV2Codec.decode(raw) }.exceptionOrNull()
            ?.let { (it as TouchLayoutV2Exception).code }
            ?: "accepted"
        if (expected == "schemaRejected") {
            assertTrue("${case["id"].asString}: $actual", actual != "accepted")
        } else {
            assertEquals(case["id"].asString, expected, actual)
        }
    }

    private fun applyMutation(root: JsonElement, mutation: JsonObject) {
        val operation = mutation["op"]?.asString ?: "replace"
        val path = mutation["path"].asString
        val replacement = when {
            mutation.has("valueFrom") -> pointer(root, mutation["valueFrom"].asString).deepCopy()
            mutation.has("value") -> mutation["value"].deepCopy()
            else -> JsonNull.INSTANCE
        }
        when (operation) {
            "replace" -> pointerSet(root, path, replacement)
            "delete" -> pointerDelete(root, path)
            "append" -> pointer(root, path).asJsonArray.add(replacement)
            "repeatAppend" -> {
                val target = pointer(root, path).asJsonArray
                repeat(mutation["count"].asInt) { index ->
                    val item = replacement.deepCopy()
                    if (item.isJsonObject && item.asJsonObject.has("elementId")) {
                        item.asJsonObject.addProperty("elementId", "00000000-0000-0000-0001-${index.toString(16).padStart(12, '0')}")
                    }
                    if (item.isJsonObject && item.asJsonObject.has("variantId")) {
                        item.asJsonObject.addProperty("variantId", "00000000-0000-0000-0002-${index.toString(16).padStart(12, '0')}")
                    }
                    target.add(item)
                }
            }
            "fillObject" -> pointerSet(root, path, JsonObject().apply {
                repeat(mutation["count"].asInt) { add("x${it.toString().padStart(2, '0')}", replacement.deepCopy()) }
            })
            "fillArray" -> pointerSet(root, path, JsonArray().apply {
                repeat(mutation["count"].asInt) { add(replacement.deepCopy()) }
            })
            else -> fail("Unknown vector operation $operation")
        }
    }

    private fun pointer(root: JsonElement, path: String): JsonElement =
        path.split('/').drop(1).fold(root) { current, token ->
            if (current.isJsonArray) current.asJsonArray[token.toInt()] else current.asJsonObject[token]
        }

    private fun pointerSet(root: JsonElement, path: String, value: JsonElement) {
        val tokens = path.split('/').drop(1)
        val parent = tokens.dropLast(1).fold(root) { current, token ->
            if (current.isJsonArray) current.asJsonArray[token.toInt()] else current.asJsonObject[token]
        }
        val leaf = tokens.last()
        if (parent.isJsonArray) parent.asJsonArray.set(leaf.toInt(), value) else parent.asJsonObject.add(leaf, value)
    }

    private fun pointerDelete(root: JsonElement, path: String) {
        val tokens = path.split('/').drop(1)
        val parent = tokens.dropLast(1).fold(root) { current, token ->
            if (current.isJsonArray) current.asJsonArray[token.toInt()] else current.asJsonObject[token]
        }
        val leaf = tokens.last()
        if (parent.isJsonArray) parent.asJsonArray.remove(leaf.toInt()) else parent.asJsonObject.remove(leaf)
    }

    private fun rehash(candidate: JsonObject) {
        candidate.remove("contentHash")
        val strict = toStrict(candidate).let { it as StrictJsonValue.ObjectValue }
        candidate.addProperty("contentHash", TouchLayoutV2Jcs.contentHash(strict))
    }

    private fun toStrict(value: JsonElement): StrictJsonValue = when {
        value.isJsonNull -> StrictJsonValue.NullValue
        value.isJsonObject -> StrictJsonValue.ObjectValue(linkedMapOf<String, StrictJsonValue>().apply {
            value.asJsonObject.entrySet().forEach { (key, child) -> put(key, toStrict(child)) }
        })
        value.isJsonArray -> StrictJsonValue.ArrayValue(value.asJsonArray.map(::toStrict))
        value.asJsonPrimitive.isBoolean -> StrictJsonValue.BooleanValue(value.asBoolean)
        value.asJsonPrimitive.isString -> StrictJsonValue.StringValue(value.asString)
        else -> {
            val decimal = BigDecimal(value.asJsonPrimitive.asString)
            if (decimal.stripTrailingZeros().scale() > 0) throw TouchLayoutV2Exception("fractionNotAllowed")
            StrictJsonValue.IntegerValue(decimal.longValueExact())
        }
    }

    private fun vectors(): JsonObject =
        JsonParser.parseString(fixture("ligase-touch-layout-v2-negative-vectors.json").readText()).asJsonObject

    private fun positiveBytes() = fixture("ligase-touch-layout-v2-built-in-all-types.json").readBytes()
    private fun fixture(name: String) = repoRoot().resolve("tests/fixtures/$name")
    private fun repoRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (!current.resolve("tests/fixtures").isDirectory) {
            current = current.parentFile ?: error("Repository root not found")
        }
        return current
    }
    private fun rejectedCode(raw: ByteArray): String =
        try {
            TouchLayoutV2Codec.decode(raw)
            "accepted"
        } catch (error: TouchLayoutV2Exception) {
            error.code
        }
    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun sha256(raw: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    private fun rect(value: JsonObject) = IntRect(
        value["x"].asInt, value["y"].asInt, value["width"].asInt, value["height"].asInt,
    )
    private fun size(value: JsonObject) = IntSize(value["width"].asInt, value["height"].asInt)
}
