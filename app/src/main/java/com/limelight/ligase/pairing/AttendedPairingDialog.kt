package com.limelight.ligase.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.limelight.R

@Composable
fun AttendedPairingDialog(
    state: AttendedPairingUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        AttendedPairingUiState.Idle,
        AttendedPairingUiState.Completed,
        -> Unit
        AttendedPairingUiState.Creating -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.ligase_pair_secure_connecting)) },
            text = { Text(stringResource(R.string.ligase_pair_secure_connecting_summary)) },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        is AttendedPairingUiState.Waiting -> PairingCodeDialog(
            deviceName = state.deviceName,
            safetyCode = state.safetyCode,
            finishing = false,
            onCancel = onCancel,
        )
        is AttendedPairingUiState.Finishing -> PairingCodeDialog(
            deviceName = null,
            safetyCode = state.safetyCode,
            finishing = true,
            onCancel = onCancel,
        )
        is AttendedPairingUiState.Stopped -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.ligase_pair_not_completed)) },
            text = {
                Text(
                    stringResource(
                        when (state.reason) {
                            StopReason.REJECTED -> R.string.ligase_pair_rejected
                            StopReason.CANCELLED -> R.string.ligase_pair_cancelled
                            StopReason.EXPIRED -> R.string.ligase_pair_expired
                            StopReason.FAILED -> R.string.ligase_pair_failed
                            StopReason.NETWORK -> R.string.ligase_pair_network
                            StopReason.PROTOCOL -> R.string.ligase_pair_protocol
                            StopReason.UNKNOWN -> R.string.ligase_pair_unknown
                        },
                    ),
                )
            },
            confirmButton = {
                Button(onClick = onDismiss) { Text(stringResource(R.string.ligase_pair_acknowledge)) }
            },
        )
    }
}

@Composable
private fun PairingCodeDialog(
    deviceName: String?,
    safetyCode: String,
    finishing: Boolean,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    if (finishing) R.string.ligase_pair_approved
                    else R.string.ligase_pair_confirm_on_host,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (deviceName != null) {
                    Text(deviceName, fontWeight = FontWeight.Medium)
                }
                Text(
                    safetyCode,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Text(
                    if (finishing) {
                        stringResource(R.string.ligase_pair_finishing)
                    } else {
                        stringResource(R.string.ligase_pair_sas_help)
                    },
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.ligase_pair_cancel))
            }
        },
    )
}
