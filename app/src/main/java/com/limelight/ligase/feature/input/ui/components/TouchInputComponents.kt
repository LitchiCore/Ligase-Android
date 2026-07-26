package com.limelight.ligase.feature.input.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
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
import com.limelight.ligase.input.LigaseTouchOverlayMode

fun LazyListScope.touchInputItems(
    touchOverlayMode: LigaseTouchOverlayMode,
    onTouchOverlayModeChanged: (LigaseTouchOverlayMode) -> Unit,
) {
    item { InputSectionTitle(R.string.ligase_touch_overlays) }
    item {
        TouchOverlaySelector(
            mode = touchOverlayMode,
            onChanged = onTouchOverlayModeChanged,
        )
    }
}

@Composable
private fun TouchOverlaySelector(
    mode: LigaseTouchOverlayMode,
    onChanged: (LigaseTouchOverlayMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.ligase_touch_overlays_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TouchOverlayOptionCard(
            title = R.string.ligase_virtual_gamepad,
            summary = R.string.ligase_virtual_gamepad_option_summary,
            selected = mode == LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
            onClick = { onChanged(LigaseTouchOverlayMode.VIRTUAL_GAMEPAD) },
        )
        TouchOverlayOptionCard(
            title = R.string.ligase_no_screen_controls,
            summary = R.string.ligase_no_screen_controls_summary,
            selected = mode == LigaseTouchOverlayMode.GESTURES_ONLY,
            onClick = { onChanged(LigaseTouchOverlayMode.GESTURES_ONLY) },
        )
    }
}

@Composable
private fun TouchOverlayOptionCard(
    @StringRes title: Int,
    @StringRes summary: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                LigaseSemanticTheme.colors.selected
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
