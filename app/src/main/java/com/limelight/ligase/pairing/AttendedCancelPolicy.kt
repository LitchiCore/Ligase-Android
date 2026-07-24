package com.limelight.ligase.pairing

internal enum class AttendedCancelOutcome {
    CANCELLED,
    PAIRED,
    REJECTED,
    EXPIRED,
    FAILED,
    UNKNOWN,
    PROTOCOL_ERROR,
}

internal object AttendedCancelPolicy {
    fun fromHttp(status: Int, body: ByteArray?, requestId: String): AttendedCancelOutcome {
        if (status in setOf(401, 404, 410)) return AttendedCancelOutcome.UNKNOWN
        if (status != 200 || body == null) return AttendedCancelOutcome.PROTOCOL_ERROR
        val fields = try {
            AttendedPairingJson.requireObject(
                AttendedPairingJson.parse(body),
                setOf("requestId", "state", "expiresAt", "failure"),
            )
        } catch (_: RuntimeException) {
            return AttendedCancelOutcome.PROTOCOL_ERROR
        }
        if (runCatching {
                AttendedPairingJson.requireString(fields.getValue("requestId"))
            }.getOrNull() != requestId
        ) return AttendedCancelOutcome.PROTOCOL_ERROR
        return when (runCatching {
            AttendedPairingJson.requireString(fields.getValue("state"))
        }.getOrNull()) {
            "cancelled" -> AttendedCancelOutcome.CANCELLED
            "paired" -> AttendedCancelOutcome.PAIRED
            "rejected" -> AttendedCancelOutcome.REJECTED
            "expired" -> AttendedCancelOutcome.EXPIRED
            "failed" -> AttendedCancelOutcome.FAILED
            "pending", "approved" -> AttendedCancelOutcome.PROTOCOL_ERROR
            else -> AttendedCancelOutcome.PROTOCOL_ERROR
        }
    }

    fun transportFailure(): AttendedCancelOutcome = AttendedCancelOutcome.UNKNOWN
}
