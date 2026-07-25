package com.limelight.ligase.feature.pairing.presentation

import androidx.annotation.StringRes
import com.limelight.R
import com.limelight.ligase.pairing.AttendedPairingUiState
import com.limelight.ligase.pairing.StopReason

enum class AttendedPairingPresentationKind {
    HIDDEN,
    CREATING,
    WAITING,
    FINISHING,
    STOPPED,
}

class PairingSafetyCode private constructor(private val value: String) {
    fun revealForDisplay(): String = value

    override fun toString(): String = "[redacted safety code]"

    companion object {
        fun displayed(value: String): PairingSafetyCode = PairingSafetyCode(value)
    }
}

data class AttendedPairingPresentation(
    val kind: AttendedPairingPresentationKind,
    @param:StringRes val title: Int? = null,
    @param:StringRes val message: Int? = null,
    val deviceName: String? = null,
    val safetyCode: PairingSafetyCode? = null,
    val remainingSeconds: Int? = null,
    val cancelEnabled: Boolean = false,
    val dismissEnabled: Boolean = false,
) {
    override fun toString(): String =
        "AttendedPairingPresentation(" +
            "kind=$kind, title=$title, message=$message, deviceName=$deviceName, " +
            "cancelEnabled=$cancelEnabled, dismissEnabled=$dismissEnabled)"
}

fun attendedPairingPresentation(
    state: AttendedPairingUiState,
): AttendedPairingPresentation = when (state) {
    AttendedPairingUiState.Idle,
    AttendedPairingUiState.Completed,
    -> AttendedPairingPresentation(AttendedPairingPresentationKind.HIDDEN)

    AttendedPairingUiState.Creating -> AttendedPairingPresentation(
        kind = AttendedPairingPresentationKind.CREATING,
        title = R.string.ligase_pair_secure_connecting,
        message = R.string.ligase_pair_secure_connecting_summary,
        cancelEnabled = true,
    )

    is AttendedPairingUiState.Waiting -> AttendedPairingPresentation(
        kind = AttendedPairingPresentationKind.WAITING,
        title = R.string.ligase_pair_confirm_on_host,
        message = R.string.ligase_pair_sas_help,
        deviceName = state.deviceName,
        safetyCode = PairingSafetyCode.displayed(state.safetyCode),
        remainingSeconds = state.countdown.remainingSeconds,
        cancelEnabled = true,
    )

    is AttendedPairingUiState.Finishing -> AttendedPairingPresentation(
        kind = AttendedPairingPresentationKind.FINISHING,
        title = R.string.ligase_pair_approved,
        message = R.string.ligase_pair_finishing,
        safetyCode = PairingSafetyCode.displayed(state.safetyCode),
        remainingSeconds = state.countdown.remainingSeconds,
        cancelEnabled = true,
    )

    is AttendedPairingUiState.Stopped -> AttendedPairingPresentation(
        kind = AttendedPairingPresentationKind.STOPPED,
        title = R.string.ligase_pair_not_completed,
        message = stoppedMessage(state.reason),
        dismissEnabled = true,
    )
}

@StringRes
private fun stoppedMessage(reason: StopReason): Int = when (reason) {
    StopReason.REJECTED -> R.string.ligase_pair_rejected
    StopReason.CANCELLED -> R.string.ligase_pair_cancelled
    StopReason.EXPIRED -> R.string.ligase_pair_expired
    StopReason.FAILED -> R.string.ligase_pair_failed
    StopReason.NETWORK -> R.string.ligase_pair_network
    StopReason.PROTOCOL -> R.string.ligase_pair_protocol
    StopReason.UNKNOWN -> R.string.ligase_pair_unknown
}
