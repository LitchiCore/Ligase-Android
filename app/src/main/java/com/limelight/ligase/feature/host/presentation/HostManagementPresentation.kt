package com.limelight.ligase.feature.host.presentation

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import java.security.MessageDigest
import java.util.UUID

enum class HostManagementStatus {
    CHECKING,
    OFFLINE,
    PAIR_REQUIRED,
    ONLINE,
}

class HostStableUiKey private constructor(private val value: String) {
    fun revealForComposeKey(): String = value

    override fun toString(): String = "[redacted host key]"

    companion object {
        fun fromHost(host: ComputerDetails): HostStableUiKey =
            HostStableUiKey(host.uuid ?: host.name)
    }
}

data class HostManagementRowPresentation(
    val sourceIndex: Int,
    val name: String,
    val status: HostManagementStatus,
    val selected: Boolean,
    val stableKey: HostStableUiKey,
)

data class HostManagementPresentation(
    val rows: List<HostManagementRowPresentation>,
    val empty: Boolean = rows.isEmpty(),
)

data class LanPairingCandidatePresentation(
    val sourceIndex: Int,
    val name: String,
    val stableKey: HostStableUiKey,
)

fun hostManagementPresentation(
    hosts: List<ComputerDetails>,
    selectedHostUuid: String?,
): HostManagementPresentation = HostManagementPresentation(
    rows = hosts.mapIndexed { index, host ->
        HostManagementRowPresentation(
            sourceIndex = index,
            name = host.name,
            status = hostManagementStatus(host),
            selected = host.uuid.equals(selectedHostUuid, ignoreCase = true),
            stableKey = HostStableUiKey.fromHost(host),
        )
    },
)

/**
 * Projects only verified server-info identities discovered on the LAN.
 *
 * Address tuples are deliberately excluded from identity. A Host discovered on
 * IPv4 and IPv6 therefore remains one card, while malformed/missing UUIDs fail
 * closed instead of becoming name-based pairing identities.
 */
fun lanPairingCandidates(
    hosts: List<ComputerDetails>,
): List<LanPairingCandidatePresentation> {
    val seenUuids = mutableSetOf<String>()
    val seenCertificates = mutableSetOf<String>()
    return hosts.mapIndexedNotNull { index, host ->
        if (
            host.state != ComputerDetails.State.ONLINE ||
            host.pairState != PairingManager.PairState.NOT_PAIRED
        ) {
            return@mapIndexedNotNull null
        }
        val canonicalUuid = runCatching { UUID.fromString(host.uuid).toString() }.getOrNull()
            ?: return@mapIndexedNotNull null
        val certificateKey = runCatching {
            host.serverCert?.encoded?.let { encoded ->
                MessageDigest.getInstance("SHA-256").digest(encoded).toHex()
            }
        }.getOrNull()
        if (!seenUuids.add(canonicalUuid)) return@mapIndexedNotNull null
        if (certificateKey != null && !seenCertificates.add(certificateKey)) {
            return@mapIndexedNotNull null
        }
        LanPairingCandidatePresentation(
            sourceIndex = index,
            name = host.name,
            stableKey = HostStableUiKey.fromHost(host),
        )
    }
}

fun hostManagementStatus(host: ComputerDetails): HostManagementStatus = when {
    host.state == ComputerDetails.State.UNKNOWN -> HostManagementStatus.CHECKING
    host.state == ComputerDetails.State.OFFLINE -> HostManagementStatus.OFFLINE
    host.pairState != PairingManager.PairState.PAIRED -> HostManagementStatus.PAIR_REQUIRED
    else -> HostManagementStatus.ONLINE
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
