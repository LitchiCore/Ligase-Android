package com.limelight.ligase.pairing

import com.limelight.nvstream.http.NvHTTP
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal interface AttendedPairingTransport {
    fun create(path: String, request: AttendedCreateRequest): AttendedCreateResponse
    fun putEnvelope(path: String, requestId: String, token: String, envelope: StrictJsonValue.Obj)
    fun status(path: String, requestId: String, token: String): AttendedStatus
    fun cancel(path: String, requestId: String, token: String): Boolean
    fun cancelInFlight()
}

internal class AttendedPairingRepository(
    private val http: NvHTTP,
) : AttendedPairingTransport {
    data class HttpFailure(val status: Int, val code: String?) :
        IOException("Attended pairing request failed")

    override fun create(path: String, request: AttendedCreateRequest): AttendedCreateResponse {
        val response = http.executeAttendedPairingRequest(
            path,
            "POST",
            AttendedPairingJson.canonicalBytes(request.json()),
            null,
            5_000,
        )
        if (response.statusCode !in setOf(200, 201)) throw failure(response)
        return parseCreateResponse(response.body, request)
    }

    override fun putEnvelope(
        path: String,
        requestId: String,
        token: String,
        envelope: StrictJsonValue.Obj,
    ) {
        val response = http.executeAttendedPairingRequest(
            "$path/$requestId/envelope",
            "PUT",
            AttendedPairingJson.canonicalBytes(envelope),
            token,
            5_000,
        )
        if (response.statusCode != 204 || response.body.isNotEmpty()) throw failure(response)
    }

    override fun status(path: String, requestId: String, token: String): AttendedStatus {
        val response = http.executeAttendedPairingRequest(
            "$path/$requestId",
            "GET",
            null,
            token,
            3_000,
        )
        if (response.statusCode != 200) throw failure(response)
        return parseStatus(response.body, requestId)
    }

    override fun cancel(path: String, requestId: String, token: String): Boolean {
        val response = http.executeAttendedPairingRequest(
            "$path/$requestId",
            "DELETE",
            null,
            token,
            3_000,
        )
        return response.statusCode == 204 && response.body.isEmpty()
    }

    override fun cancelInFlight() = http.cancelActiveAttendedPairingCall()

    private fun parseCreateResponse(
        bytes: ByteArray,
        request: AttendedCreateRequest,
    ): AttendedCreateResponse {
        val fields = AttendedPairingJson.requireObject(
            AttendedPairingJson.parse(bytes),
            setOf(
                "version",
                "requestId",
                "requestToken",
                "hostUniqueId",
                "hostCertificateSha256",
                "hostEphemeralKey",
                "hostNonce",
                "expiresAt",
            ),
        )
        require(AttendedPairingJson.requireLong(fields.getValue("version")) == 1L) { "invalidVersion" }
        val response = AttendedCreateResponse(
            requestId = AttendedPairingJson.requireString(fields.getValue("requestId")),
            requestToken = AttendedPairingJson.requireString(fields.getValue("requestToken")),
            hostUniqueId = AttendedPairingJson.requireString(fields.getValue("hostUniqueId")),
            hostCertificateSha256 =
                AttendedPairingJson.requireString(fields.getValue("hostCertificateSha256")),
            hostEphemeralKey =
                AttendedPairingJson.requireString(fields.getValue("hostEphemeralKey")),
            hostNonce = AttendedPairingJson.requireString(fields.getValue("hostNonce")),
            expiresAt = AttendedPairingJson.requireString(fields.getValue("expiresAt")),
        )
        require(response.requestId == request.requestId) { "requestIdMismatch" }
        canonicalUuid(response.hostUniqueId)
        AttendedPairingJson.decodeBase64Url(response.requestToken, 32).fill(0)
        AttendedPairingJson.decodeBase64Url(response.hostCertificateSha256, 32).fill(0)
        AttendedPairingJson.decodeBase64Url(response.hostEphemeralKey, 32).fill(0)
        AttendedPairingJson.decodeBase64Url(response.hostNonce, 32).fill(0)
        require(validUtcSecond(response.expiresAt)) { "invalidExpiresAt" }
        return response
    }

    private fun parseStatus(bytes: ByteArray, requestId: String): AttendedStatus {
        val fields = AttendedPairingJson.requireObject(
            AttendedPairingJson.parse(bytes),
            setOf("requestId", "state", "expiresAt", "failure"),
        )
        val responseId = AttendedPairingJson.requireString(fields.getValue("requestId"))
        require(responseId == requestId) { "requestIdMismatch" }
        val state = when (AttendedPairingJson.requireString(fields.getValue("state"))) {
            "pending" -> AttendedStatus.State.PENDING
            "approved" -> AttendedStatus.State.APPROVED
            "paired" -> AttendedStatus.State.PAIRED
            "rejected" -> AttendedStatus.State.REJECTED
            "cancelled" -> AttendedStatus.State.CANCELLED
            "expired" -> AttendedStatus.State.EXPIRED
            "failed" -> AttendedStatus.State.FAILED
            else -> throw IllegalArgumentException("invalidState")
        }
        val expiresAt = AttendedPairingJson.requireString(fields.getValue("expiresAt"))
        require(validUtcSecond(expiresAt)) { "invalidExpiresAt" }
        val failureValue = fields.getValue("failure")
        val failure = when (failureValue) {
            StrictJsonValue.Null -> null
            is StrictJsonValue.Str -> failureValue.value
            else -> throw IllegalArgumentException("invalidFailure")
        }
        if (state == AttendedStatus.State.FAILED) {
            require(failure in setOf(
                "certificateMismatch",
                "pairSessionMissing",
                "cryptoFailure",
                "legacyPairingFailed",
            )) { "invalidFailure" }
        } else {
            require(failure == null) { "invalidFailure" }
        }
        return AttendedStatus(responseId, state, expiresAt, failure)
    }

    private fun failure(response: NvHTTP.AttendedHttpResponse): HttpFailure {
        val code = try {
            val value = AttendedPairingJson.parse(response.body)
            val fields = (value as? StrictJsonValue.Obj)?.values
            fields?.get("code")?.let(AttendedPairingJson::requireString)
        } catch (_: RuntimeException) {
            null
        }
        return HttpFailure(response.statusCode, code)
    }

    companion object {
        private val UTC_SECONDS =
            Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")

        private fun validUtcSecond(value: String): Boolean {
            if (!UTC_SECONDS.matches(value)) return false
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            formatter.isLenient = false
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = runCatching { formatter.parse(value) }.getOrNull() ?: return false
            return formatter.format(parsed) == value
        }
    }
}
