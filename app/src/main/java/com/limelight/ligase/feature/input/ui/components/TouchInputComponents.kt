package com.limelight.ligase.feature.input.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
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
import com.limelight.ligase.feature.input.ui.presentation.InputPresentation
import com.limelight.ligase.input.LigaseTouchLayout
import com.limelight.ligase.input.LigaseTouchOverlayMode

fun LazyListScope.touchInputItems(
    presentation: InputPresentation,
    touchLayouts: List<LigaseTouchLayout>,
    touchOverlayMode: LigaseTouchOverlayMode,
    selectedTouchLayoutId: String?,
    selectedTouchLayoutEditable: Boolean,
    onTouchLayoutSelected: (String) -> Unit,
    onTouchOverlayModeChanged: (LigaseTouchOverlayMode) -> Unit,
    onBrowseLayouts: () -> Unit,
    onEditTouchLayout: () -> Unit,
) {
    item { InputSectionTitle(R.string.ligase_touch_overlays) }
    item {
        TouchOverlaySelector(
            mode = touchOverlayMode,
            onChanged = onTouchOverlayModeChanged,
        )
    }
    if (touchOverlayMode != LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD) return

    item { InputSectionTitle(R.string.ligase_global_touch_layout) }
    if (presentation.touchLayoutMissing) {
        item {
            InputNoticeCard(
                text = stringResource(
                    R.string.ligase_touch_layout_missing,
                    selectedTouchLayoutId.orEmpty(),
                ),
                error = true,
            )
        }
    }
    if (touchLayouts.isEmpty()) {
        item {
            InputNoticeCard(
                text = stringResource(R.string.ligase_no_touch_layout),
                error = true,
            )
        }
    } else {
        items(
            items = touchLayouts,
            key = { it.id },
        ) { layout ->
            TouchLayoutCard(
                layout = layout,
                selected = layout.id == selectedTouchLayoutId,
                onClick = { onTouchLayoutSelected(layout.id) },
            )
        }
    }
    item {
        Text(
            text = stringResource(R.string.ligase_global_touch_layout_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onBrowseLayouts,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.ligase_layout_browse))
        }
        OutlinedButton(
            onClick = onEditTouchLayout,
            enabled = presentation.canEditTouchLayout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (selectedTouchLayoutEditable) {
                        R.string.ligase_layout_edit_current
                    } else {
                        R.string.ligase_layout_copy_current
                    },
                ),
            )
        }
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
            title = R.string.ligase_virtual_keyboard,
            summary = R.string.ligase_virtual_keyboard_option_summary,
            selected = mode == LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
            onClick = { onChanged(LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD) },
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

@Composable
private fun TouchLayoutCard(
    layout: LigaseTouchLayout,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Text(layout.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.ligase_touch_layout_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
