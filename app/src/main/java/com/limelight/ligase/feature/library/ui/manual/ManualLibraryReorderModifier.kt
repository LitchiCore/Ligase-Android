package com.limelight.ligase.feature.library.ui.manual

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.library.ManualLibraryOrderDraft
import com.limelight.ligase.library.ManualLibraryOrderEntry

@Composable
internal fun manualLibraryReorderModifier(
    item: LigaseLibraryItem,
    draft: ManualLibraryOrderDraft?,
    enabled: Boolean,
    onMove: (movingUuid: String, targetUuid: String) -> Unit,
): Modifier {
    val uuid = item.hostAppUuid ?: return Modifier
    if (draft == null) return Modifier
    var dragOffset by remember(uuid) { mutableFloatStateOf(0f) }
    val moveThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val previousUuid = draft.adjacentMovableUuid(uuid, -1)
    val nextUuid = draft.adjacentMovableUuid(uuid, 1)
    val moveUpLabel = stringResource(R.string.ligase_manual_sort_move_up)
    val moveDownLabel = stringResource(R.string.ligase_manual_sort_move_down)

    return Modifier
        .graphicsLayer { translationY = dragOffset }
        .then(
            if (dragOffset != 0f) {
                Modifier.shadow(8.dp, RoundedCornerShape(22.dp))
            } else {
                Modifier
            },
        )
        .semantics {
            customActions = buildList {
                if (enabled && previousUuid != null) {
                    add(
                        CustomAccessibilityAction(moveUpLabel) {
                            onMove(uuid, previousUuid)
                            true
                        },
                    )
                }
                if (enabled && nextUuid != null) {
                    add(
                        CustomAccessibilityAction(moveDownLabel) {
                            onMove(uuid, nextUuid)
                            true
                        },
                    )
                }
            }
        }
        .then(
            if (!enabled) {
                Modifier
            } else {
                Modifier.pointerInput(uuid, draft.entries.map(ManualLibraryOrderEntry::uuid)) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragOffset = 0f },
                        onDragCancel = { dragOffset = 0f },
                        onDragEnd = { dragOffset = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            manualLibraryMoveDirection(
                                dragOffset = dragOffset,
                                moveThreshold = moveThreshold,
                            )?.let { direction ->
                                draft.adjacentMovableUuid(uuid, direction)?.let { targetUuid ->
                                    onMove(uuid, targetUuid)
                                }
                                dragOffset = 0f
                            }
                        },
                    )
                }
            },
        )
}
