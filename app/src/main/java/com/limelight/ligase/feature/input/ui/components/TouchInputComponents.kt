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
import com.limelight.ligase.input.LigaseCloudTouchMode
import com.limelight.ligase.input.EffectiveStreamingTouchMode

fun LazyListScope.touchInputItems(
    touchOverlayMode: LigaseTouchOverlayMode,
    cloudTouchMode: LigaseCloudTouchMode,
    writable: Boolean,
    effectiveStreamingTouchMode: EffectiveStreamingTouchMode,
    onTouchOverlayModeChanged: (LigaseTouchOverlayMode) -> Unit,
    onCloudTouchModeChanged: (LigaseCloudTouchMode) -> Unit,
) {
    item { InputSectionTitle(R.string.ligase_touch_overlays) }
    item {
        InputNoticeCard(
            text = stringResource(
                when (effectiveStreamingTouchMode) {
                    EffectiveStreamingTouchMode.DIRECT_TOUCH -> R.string.ligase_effective_touch_direct
                    EffectiveStreamingTouchMode.ABSOLUTE_POINTER -> R.string.ligase_effective_touch_absolute
                    EffectiveStreamingTouchMode.TRACKPAD -> R.string.ligase_effective_touch_trackpad
                },
            ),
            error = false,
        )
    }
    item {
        TouchOverlaySelector(
            mode = touchOverlayMode,
            onChanged = onTouchOverlayModeChanged,
            writable = writable,
        )
    }
    if (touchOverlayMode == LigaseTouchOverlayMode.CLOUD_CONTROLS) {
        item { InputSectionTitle(R.string.ligase_cloud_touch_mode) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LigaseCloudTouchMode.entries.forEach { option ->
                    TouchOverlayOptionCard(
                        title = when (option) {
                            LigaseCloudTouchMode.SINGLE_TOUCH -> R.string.mouse_mode_absolute_touch
                            LigaseCloudTouchMode.MULTI_TOUCH -> R.string.mouse_mode_multi_touch
                            LigaseCloudTouchMode.TRACKPAD -> R.string.title_checkbox_touchscreen_trackpad
                        },
                        summary = R.string.ligase_cloud_touch_mode_summary,
                        selected = cloudTouchMode == option,
                        enabled = writable,
                        onClick = { onCloudTouchModeChanged(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchOverlaySelector(
    mode: LigaseTouchOverlayMode,
    onChanged: (LigaseTouchOverlayMode) -> Unit,
    writable: Boolean,
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
            enabled = writable,
        )
        TouchOverlayOptionCard(
            title = R.string.ligase_cloud_controls,
            summary = R.string.ligase_cloud_controls_summary,
            selected = mode == LigaseTouchOverlayMode.CLOUD_CONTROLS,
            onClick = { onChanged(LigaseTouchOverlayMode.CLOUD_CONTROLS) },
            enabled = writable,
        )
        TouchOverlayOptionCard(
            title = R.string.ligase_no_screen_controls,
            summary = R.string.ligase_no_screen_controls_summary,
            selected = mode == LigaseTouchOverlayMode.HIDDEN,
            onClick = { onChanged(LigaseTouchOverlayMode.HIDDEN) },
            enabled = writable,
        )
    }
}

@Composable
private fun TouchOverlayOptionCard(
    @StringRes title: Int,
    @StringRes summary: Int,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
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
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}
