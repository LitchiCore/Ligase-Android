package com.limelight.ligase.pairing

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class AttendedPairingFixtureTest {
    @Test
    fun authorityFilesAreCopiedByteForByte() {
        val fixture = resource("attended-pairing-v1-vectors.json")
        val schema = resource("attended-pairing-v1-vectors.schema.json")
        assertEquals(
            "7A924B4FFB9C813F9D9450D85428FE3148F3317C789715D1DE815D07CC98B3D3",
            sha256Hex(fixture),
        )
        assertEquals(
            "53F08A996123F77CAC5A459437A2F24DE2338E3172B1A7C8DB4A9CED9E000B81",
            sha256Hex(schema),
        )
        val root = JsonParser.parseString(fixture.decodeToString()).asJsonObject
        val required = root.getAsJsonObject("coverageManifest").getAsJsonArray("requiredIds")
            .map { it.asString }
        val cases = root.getAsJsonArray("cases").map { it.asJsonObject }
        assertEquals(119, cases.size)
        assertEquals(required, cases.map { it.get("id").asString })
        assertEquals(required.size, required.toSet().size)
    }

    @Test
    fun fullLigaseCryptoPositiveMatchesAuthority() {
        val positive = cases().single { it.get("id").asString == "ligase-positive-v1" }
        val crypto = AttendedPairingCrypto()
        val request = AttendedCreateRequest(
            requestId = "9dbbb480-9ef1-4e9e-bb1f-0c1d42dff8e4",
            deviceName = "Phone",
            clientEphemeralKey = positive.string("clientPublicKeyBase64url"),
            clientNonce = positive.string("clientNonceBase64url"),
            clientCertificateSha256 = "gYgku8m7j1ioMTT5_lG4xKn0ejHuvIJbsQtMNe6wQkw",
        )
        val response = AttendedCreateResponse(
            requestId = request.requestId,
            requestToken = positive.string("requestTokenBase64url"),
            hostUniqueId = "53beb7ec-9788-cc23-461a-061f153029a5",
            hostCertificateSha256 = "3X8jcLJm2GwA3kuhP96R-lZwRcXTMFcvKsm7nHP-1S4",
            hostEphemeralKey = positive.string("hostPublicKeyBase64url"),
            hostNonce = positive.string("hostNonceBase64url"),
            expiresAt = "2026-07-23T07:30:00Z",
        )
        val material = crypto.derive(
            request,
            response,
            decode(positive.string("clientPrivateKeyBase64url")),
            decode(positive.string("clientNonceBase64url")),
        )
        assertEquals(
            positive.string("createRequestJcsBase64url"),
            encode(AttendedPairingJson.canonicalBytes(request.json())),
        )
        assertEquals(
            positive.string("createResponseWithoutTokenJcsBase64url"),
            encode(AttendedPairingJson.canonicalBytes(response.withoutTokenJson())),
        )
        assertArrayEquals(decode(positive.string("transcriptHashBase64url")), material.transcriptHash)
        assertArrayEquals(decode(positive.string("pairingKeyBase64url")), material.pairingKey)
        assertEquals(positive.string("safetyCode"), material.safetyCode)
        val envelope = crypto.createEnvelope(
            "1234".toCharArray(),
            material.pairingKey,
            material.transcriptHash,
            decode(positive.string("envelopeNonceBase64url")),
        )
        assertEquals(
            positive.string("envelopeJcsBase64url"),
            encode(AttendedPairingJson.canonicalBytes(envelope)),
        )
    }

    @Test
    fun allAndroidCancelVectorsMatchClientPolicy() {
        val androidCases = cases().filter { it.get("operation").asString == "androidCancel" }
        assertEquals(12, androidCases.size)
        androidCases.forEach { case ->
            val probe = case.getAsJsonObject("statusProbeExpected")
            val actual = if (probe.get("kind").asString == "transportError") {
                AttendedCancelPolicy.transportFailure()
            } else {
                val body = decode(probe.get("body").asString)
                AttendedCancelPolicy.fromHttp(
                    probe.get("status").asInt,
                    body,
                    "9dbbb480-9ef1-4e9e-bb1f-0c1d42dff8e4",
                )
            }
            val expected = when (case.get("expectedAndroidOutcome").asString) {
                "cancelled" -> AttendedCancelOutcome.CANCELLED
                "paired" -> AttendedCancelOutcome.PAIRED
                "rejected" -> AttendedCancelOutcome.REJECTED
                "expired" -> AttendedCancelOutcome.EXPIRED
                "failed" -> AttendedCancelOutcome.FAILED
                "unknown" -> AttendedCancelOutcome.UNKNOWN
                "protocolError" -> AttendedCancelOutcome.PROTOCOL_ERROR
                else -> error("Unknown fixture outcome")
            }
            assertEquals(case.get("id").asString, expected, actual)
        }
    }

    @Test
    fun strictParserRejectsDuplicateUnknownAndNonCanonicalBinary() {
        val duplicate = """{"version":1,"version":1}""".encodeToByteArray()
        assertTrue(runCatching { AttendedPairingJson.parse(duplicate) }.isFailure)
        assertTrue(runCatching {
            AttendedPairingJson.requireObject(
                AttendedPairingJson.parse("""{"known":1,"extra":2}""".encodeToByteArray()),
                setOf("known"),
            )
        }.isFailure)
        assertTrue(runCatching {
            AttendedPairingJson.decodeBase64Url("AA==", 1)
        }.isFailure)
        assertTrue(runCatching {
            AttendedPairingJson.parse(byteArrayOf(0xc3.toByte(), 0x28))
        }.isFailure)
    }

    private fun cases(): List<JsonObject> {
        val root = JsonParser.parseString(
            resource("attended-pairing-v1-vectors.json").decodeToString(),
        ).asJsonObject
        return root.getAsJsonArray("cases").map { it.asJsonObject }
    }

    private fun JsonObject.string(name: String) = get(name).asString

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)).use { it.readBytes() }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it) }

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
    private fun encode(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}
