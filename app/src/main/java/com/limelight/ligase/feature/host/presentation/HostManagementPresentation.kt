package com.limelight.ligase.feature.host.presentation

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager

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

fun hostManagementStatus(host: ComputerDetails): HostManagementStatus = when {
    host.state == ComputerDetails.State.UNKNOWN -> HostManagementStatus.CHECKING
    host.state == ComputerDetails.State.OFFLINE -> HostManagementStatus.OFFLINE
    host.pairState != PairingManager.PairState.PAIRED -> HostManagementStatus.PAIR_REQUIRED
    else -> HostManagementStatus.ONLINE
}
