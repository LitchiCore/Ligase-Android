package com.limelight.ligase.pairing

import java.util.Locale
import java.util.UUID

enum class LigaseClientAccessMode {
    OPERATE,
    OBSERVE;

    companion object {
        fun fromWire(value: String?): LigaseClientAccessMode =
            if (value == "operate") OPERATE else OBSERVE
    }
}

object LigaseAccessUiPolicy {
    fun canOperate(accessMode: String?): Boolean = accessMode == "operate"

    fun canConfigureInput(
        hasSelectedHost: Boolean,
        paired: Boolean,
        accessMode: String?,
    ): Boolean = !hasSelectedHost || !paired || canOperate(accessMode)
}

data class AttendedPairingCapability(
    val version: Int,
    val path: String?,
) {
    val supported: Boolean get() = version == 1 && !path.isNullOrBlank()
}

internal data class AttendedCreateRequest(
    val requestId: String,
    val deviceName: String,
    val clientEphemeralKey: String,
    val clientNonce: String,
    val clientCertificateSha256: String,
) {
    init {
        require(requestId == canonicalUuid(requestId))
        require(deviceName == deviceName.trim() && deviceName.codePointCount(0, deviceName.length) in 1..80)
        require(deviceName.none { Character.isSurrogate(it) || Character.isISOControl(it) })
    }

    fun json(): StrictJsonValue.Obj = AttendedPairingJson.objectOf(
        "version" to AttendedPairingJson.number(1),
        "requestId" to AttendedPairingJson.string(requestId),
        "device" to AttendedPairingJson.objectOf(
            "name" to AttendedPairingJson.string(deviceName),
            "platform" to AttendedPairingJson.string("android"),
        ),
        "clientEphemeralKey" to AttendedPairingJson.string(clientEphemeralKey),
        "clientNonce" to AttendedPairingJson.string(clientNonce),
        "clientCertificateSha256" to AttendedPairingJson.string(clientCertificateSha256),
    )
}

internal data class AttendedCreateResponse(
    val requestId: String,
    val requestToken: String,
    val hostUniqueId: String,
    val hostCertificateSha256: String,
    val hostEphemeralKey: String,
    val hostNonce: String,
    val expiresAt: String,
) {
    fun withoutTokenJson(): StrictJsonValue.Obj = AttendedPairingJson.objectOf(
        "version" to AttendedPairingJson.number(1),
        "requestId" to AttendedPairingJson.string(requestId),
        "hostUniqueId" to AttendedPairingJson.string(hostUniqueId),
        "hostCertificateSha256" to AttendedPairingJson.string(hostCertificateSha256),
        "hostEphemeralKey" to AttendedPairingJson.string(hostEphemeralKey),
        "hostNonce" to AttendedPairingJson.string(hostNonce),
        "expiresAt" to AttendedPairingJson.string(expiresAt),
    )
}

internal data class AttendedStatus(
    val requestId: String,
    val state: State,
    val expiresAt: String,
    val failure: String?,
) {
    enum class State { PENDING, APPROVED, PAIRED, REJECTED, CANCELLED, EXPIRED, FAILED }
}

internal data class AttendedPairingMaterial(
    val request: AttendedCreateRequest,
    val response: AttendedCreateResponse,
    val transcriptHash: ByteArray,
    val pairingKey: ByteArray,
    val safetyCode: String,
)

sealed interface AttendedPairingUiState {
    data object Idle : AttendedPairingUiState
    data object Creating : AttendedPairingUiState
    data class Waiting(val deviceName: String, val safetyCode: String) : AttendedPairingUiState
    data class Finishing(val safetyCode: String) : AttendedPairingUiState
    data object Completed : AttendedPairingUiState
    data class Stopped(val reason: StopReason) : AttendedPairingUiState
}

enum class StopReason { REJECTED, CANCELLED, EXPIRED, FAILED, NETWORK, PROTOCOL, UNKNOWN }

internal data class AttendedPairingAudit(
    val requestId: String,
    val terminalState: TerminalState,
    val completedAtElapsedMs: Long,
) {
    enum class TerminalState {
        PAIRED,
        REJECTED,
        CANCELLED,
        EXPIRED,
        FAILED,
        NETWORK,
        PROTOCOL,
        UNKNOWN,
    }
}

internal fun canonicalUuid(value: String): String {
    val parsed = try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("invalidUuid")
    }
    val canonical = parsed.toString().lowercase(Locale.ROOT)
    require(value.lowercase(Locale.ROOT) == canonical) { "invalidUuid" }
    return canonical
}
