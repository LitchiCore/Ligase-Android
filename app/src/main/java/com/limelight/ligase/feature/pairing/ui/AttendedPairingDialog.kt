package com.limelight.ligase.feature.pairing.ui

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
import com.limelight.ligase.feature.pairing.presentation.AttendedPairingPresentation
import com.limelight.ligase.feature.pairing.presentation.AttendedPairingPresentationKind
import com.limelight.ligase.feature.pairing.presentation.attendedPairingPresentation
import com.limelight.ligase.pairing.AttendedPairingUiState

@Composable
fun AttendedPairingDialog(
    state: AttendedPairingUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val presentation = attendedPairingPresentation(state)
    when (presentation.kind) {
        AttendedPairingPresentationKind.HIDDEN -> Unit
        AttendedPairingPresentationKind.CREATING -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(requireNotNull(presentation.title))) },
            text = { Text(stringResource(requireNotNull(presentation.message))) },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        AttendedPairingPresentationKind.WAITING,
        AttendedPairingPresentationKind.FINISHING,
        -> PairingCodeDialog(
            presentation = presentation,
            onCancel = onCancel,
        )
        AttendedPairingPresentationKind.STOPPED -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(requireNotNull(presentation.title))) },
            text = { Text(stringResource(requireNotNull(presentation.message))) },
            confirmButton = {
                Button(onClick = onDismiss) { Text(stringResource(R.string.ligase_pair_acknowledge)) }
            },
        )
    }
}

@Composable
private fun PairingCodeDialog(
    presentation: AttendedPairingPresentation,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(requireNotNull(presentation.title)),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (presentation.deviceName != null) {
                    Text(presentation.deviceName, fontWeight = FontWeight.Medium)
                }
                Text(
                    requireNotNull(presentation.safetyCode).revealForDisplay(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Text(
                    stringResource(requireNotNull(presentation.message)),
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
