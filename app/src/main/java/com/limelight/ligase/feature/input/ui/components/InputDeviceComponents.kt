package com.limelight.ligase.feature.input.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.LigaseSemanticTheme
import com.limelight.ligase.feature.input.ui.presentation.InputDeviceSelectionPresentation
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputConnection
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputSelectionStatus

fun LazyListScope.inputDeviceItems(
    selection: InputDeviceSelectionPresentation,
    @StringRes emptyText: Int,
    enabled: Boolean = true,
    onDeviceSelected: (LigaseInputCategory, String) -> Unit,
) {
    if (selection.devices.isEmpty()) {
        item { InputNoticeCard(stringResource(emptyText), error = false) }
    } else {
        items(
            items = selection.devices,
            key = { it.stableKey },
        ) { device ->
            InputDeviceCard(
                device = device,
                selected = device.stableKey == selection.selectedKey,
                enabled = enabled,
                onClick = {
                    onDeviceSelected(device.category, device.stableKey)
                },
            )
        }
    }
    if (selection.status == LigaseInputSelectionStatus.DISCONNECTED) {
        item {
            InputNoticeCard(
                text = stringResource(R.string.ligase_selected_device_disconnected),
                error = true,
            )
        }
    }
}

@Composable
private fun InputDeviceCard(
    device: LigaseInputDevice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) LigaseSemanticTheme.colors.selected
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        when (device.connection) {
                            LigaseInputConnection.USB_OTG ->
                                R.string.ligase_connection_usb
                            LigaseInputConnection.EXTERNAL ->
                                R.string.ligase_connection_external
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        }
    }
}
