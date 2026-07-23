package com.limelight.ligase.layout

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class LayoutContractV1Test {
    @Test
    fun normativeVectorBytesMatchHostSha256() {
        assertEquals(VECTOR_SHA256, sha256(vectorBytes()))
    }

    @Test
    fun allNormalizationVectorsMatch() {
        val root = vectorRoot()
        val cases = root.getAsJsonArray("normalizationCases")
        assertEquals(5, cases.size())
        cases.forEach { element ->
            val case = element.asJsonObject
            val expected = case.getAsJsonObject("expected")
            when (case.get("kind").asString) {
                "uuid" -> {
                    val actual = LayoutContractV1Validator.normalizeUuid(
                        case.get("input").asString,
                    )
                    assertEquals(
                        case.get("id").asString,
                        expected.get("valid").asBoolean,
                        actual != null,
                    )
                    if (actual != null) {
                        assertEquals(expected.get("normalized").asString, actual)
                    }
                }
                "portable" -> {
                    val input = case.getAsJsonObject("input")
                    val actual = LayoutContractV1Validator.normalizePortableIdentity(
                        PortableGameIdentityV1(
                            provider = input.get("provider").asString,
                            id = input.get("id").asString,
                        ),
                    )
                    assertEquals(
                        case.get("id").asString,
                        expected.get("valid").asBoolean,
                        actual != null,
                    )
                    if (actual != null) {
                        assertEquals(expected.get("provider").asString, actual.provider)
                        assertEquals(expected.get("id").asString, actual.id)
                    }
                }
                else -> error("Unknown normalization vector kind")
            }
        }
    }

    @Test
    fun allResolutionVectorsMatchCanonicalResults() {
        val root = vectorRoot()
        val fixtures = root.getAsJsonObject("descriptorFixtures")
        val cases = root.getAsJsonArray("resolutionCases")
        assertEquals(22, cases.size())
        cases.forEach { element ->
            val case = element.asJsonObject
            val inputJson = case.get("input")?.toString()
                ?: buildRequest(case, fixtures).toString()
            val actual = LayoutContractV1Json.serializeResult(
                LayoutContractV1Json.resolve(inputJson),
            )
            assertEquals(case.get("id").asString, case.get("expected").toString(), actual)
        }
    }

    @Test
    fun strictJsonRejectsTrailingContentAndScalarRoot() {
        assertEquals(
            """{"code":"invalidJson"}""",
            LayoutContractV1Json.serializeResult(
                LayoutContractV1Json.resolve("""{"schemaVersion":1} trailing"""),
            ),
        )
        assertEquals(
            """{"code":"invalidSchema"}""",
            LayoutContractV1Json.serializeResult(LayoutContractV1Json.resolve("[]")),
        )
    }

    @Test
    fun uuidAndPortableNormalizationDoNotCoerceInvalidInputs() {
        assertEquals(
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            LayoutContractV1Validator.normalizeUuid(
                "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA",
            ),
        )
        assertNull(LayoutContractV1Validator.normalizeUuid("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertNull(
            LayoutContractV1Validator.normalizePortableIdentity(
                PortableGameIdentityV1("steam", "01"),
            ),
        )
        assertEquals(
            PortableGameIdentityV1("steam", "4294967295"),
            LayoutContractV1Validator.normalizePortableIdentity(
                PortableGameIdentityV1("steam", "4294967295"),
            ),
        )
        assertNull(
            LayoutContractV1Validator.normalizePortableIdentity(
                PortableGameIdentityV1("steam", "4294967296"),
            ),
        )
    }

    @Test
    fun resultSerializerEmitsOnlyFrozenFieldsInFrozenOrder() {
        val resolved = LayoutResolutionV1(
            code = LayoutContractV1Codes.RESOLVED,
            source = "portable",
            layoutId = "11111111-1111-4111-8111-111111111111",
            revision = 2,
            variantId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2",
        )
        assertEquals(
            """{"code":"resolved","source":"portable","layoutId":"11111111-1111-4111-8111-111111111111","revision":2,"variantId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2"}""",
            LayoutContractV1Json.serializeResult(resolved),
        )
        val error = LayoutContractV1Json.serializeResult(
            LayoutResolutionV1(
                code = LayoutContractV1Codes.INVALID_DESCRIPTOR,
                detail = "duplicateRevision",
            ),
        )
        assertEquals(
            """{"code":"invalidDescriptor","detail":"duplicateRevision"}""",
            error,
        )
        assertFalse(error.contains("source"))
        assertTrue(error.endsWith("\"}"))
    }

    private fun buildRequest(
        case: JsonObject,
        fixtures: JsonObject,
    ): JsonObject {
        val request = case.getAsJsonObject("request")
        val root = JsonObject().apply {
            addProperty("schemaVersion", 1)
            add(
                "instance",
                JsonObject().apply {
                    addProperty("hostUniqueId", request.get("hostUniqueId").asString)
                    addProperty("appUuid", request.get("appUuid").asString)
                },
            )
        }
        copyOptional(request, root, "portableIdentity")
        copyOptional(request, root, "layoutBinding")
        root.add("context", request.get("context").deepCopy())
        root.add(
            "descriptors",
            com.google.gson.JsonArray().apply {
                case.getAsJsonArray("descriptorRefs").forEach { reference ->
                    add(fixtures.get(reference.asString).deepCopy())
                }
            },
        )
        return root
    }

    private fun copyOptional(source: JsonObject, target: JsonObject, property: String) {
        source.get(property)?.let { target.add(property, it.deepCopy()) }
    }

    private fun vectorRoot(): JsonObject =
        JsonParser.parseString(vectorBytes().toString(Charsets.UTF_8)).asJsonObject

    private fun vectorBytes(): ByteArray =
        requireNotNull(
            javaClass.classLoader?.getResourceAsStream(VECTOR_RESOURCE),
        ) { "Normative layout vectors missing" }.use { it.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val VECTOR_RESOURCE = "layout-contract-v1-vectors.json"
        const val VECTOR_SHA256 =
            "b8022f21d37481bc54a869a6be8c70b994cb94266796634356808c8dbe859fcd"
    }
}
