package com.limelight.ligase.feature.layout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.limelight.R
import com.limelight.ligase.LigasePageScaffold
import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorElement
import com.limelight.ligase.feature.layout.domain.LayoutEditorSessionState
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    state: LayoutEditorSessionState,
    onBack: () -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Float, Float) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (LayoutControlKind) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    var selectedElementId by remember(state.draftId) { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = {
        if (state.dirty) confirmDiscard = true else onBack()
    }
    BackHandler(onBack = requestBack)
    LigasePageScaffold(
        title = state.displayName.ifBlank {
            stringResource(R.string.ligase_layout_editor_title)
        },
        onBack = requestBack,
    ) { pageModifier ->
        BoxWithConstraints(
            modifier = pageModifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LayoutCanvas(
                        state = state,
                        selectedElementId = selectedElementId,
                        onSelected = { selectedElementId = it },
                        onMove = onMove,
                        onResize = onResize,
                        onDelete = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                    EditorTools(
                        state = state,
                        selectedElementId = selectedElementId,
                        onResize = onResize,
                        onDelete = onDelete,
                        onAdd = onAdd,
                        onSave = onSave,
                        onCancel = requestBack,
                        modifier = Modifier.widthIn(max = 340.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LayoutCanvas(
                        state = state,
                        selectedElementId = selectedElementId,
                        onSelected = { selectedElementId = it },
                        onMove = onMove,
                        onResize = onResize,
                        onDelete = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                    EditorTools(
                        state = state,
                        selectedElementId = selectedElementId,
                        onResize = onResize,
                        onDelete = onDelete,
                        onAdd = onAdd,
                        onSave = onSave,
                        onCancel = requestBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.ligase_layout_discard_title)) },
            text = { Text(stringResource(R.string.ligase_layout_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onDiscard()
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.ligase_layout_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LayoutCanvas(
    state: LayoutEditorSessionState,
    selectedElementId: String?,
    onSelected: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Float, Float) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(24.dp),
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(24.dp),
            )
            .onSizeChanged { canvasSize = it },
    ) {
        if (state.elements.isEmpty()) {
            Text(
                text = stringResource(R.string.ligase_layout_canvas_empty),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.elements.forEach { element ->
            LayoutElement(
                element = element,
                canvasSize = canvasSize,
                selected = selectedElementId == element.elementId,
                onSelected = { onSelected(element.elementId) },
                onMove = onMove,
                onResize = onResize,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun LayoutElement(
    element: LayoutEditorElement,
    canvasSize: IntSize,
    selected: Boolean,
    onSelected: () -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Float, Float) -> Unit,
    onDelete: (String) -> Unit,
) {
    val density = LocalDensity.current
    val xDp = with(density) { (element.x * canvasSize.width).toDp() }
    val yDp = with(density) { (element.y * canvasSize.height).toDp() }
    val widthDp = with(density) {
        max(44f, element.width * canvasSize.width).toDp()
    }
    val heightDp = with(density) {
        max(44f, element.height * canvasSize.height).toDp()
    }
    val canMutate = element.kind != LayoutControlKind.UNKNOWN
    val step = 0.02f
    val selectLabel = stringResource(R.string.ligase_layout_action_select)
    val moveLeftLabel = stringResource(R.string.ligase_layout_action_move_left)
    val moveRightLabel = stringResource(R.string.ligase_layout_action_move_right)
    val moveUpLabel = stringResource(R.string.ligase_layout_action_move_up)
    val moveDownLabel = stringResource(R.string.ligase_layout_action_move_down)
    val smallerLabel = stringResource(R.string.ligase_layout_action_smaller)
    val largerLabel = stringResource(R.string.ligase_layout_action_larger)
    val deleteLabel = stringResource(R.string.ligase_layout_action_delete)
    Box(
        modifier = Modifier
            .zIndex(if (selected) 2f else 1f)
            .offset(x = xDp, y = yDp)
            .size(widthDp, heightDp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                RoundedCornerShape(14.dp),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                RoundedCornerShape(14.dp),
            )
            .semantics {
                customActions = if (canMutate) {
                    buildList {
                        add(CustomAccessibilityAction(selectLabel) {
                            onSelected()
                            true
                        })
                        add(CustomAccessibilityAction(moveLeftLabel) {
                            onMove(
                                element.elementId,
                                (element.x - step).coerceAtLeast(0f),
                                element.y,
                            )
                            true
                        })
                        add(CustomAccessibilityAction(moveRightLabel) {
                            onMove(
                                element.elementId,
                                (element.x + step).coerceAtMost(1f - element.width),
                                element.y,
                            )
                            true
                        })
                        add(CustomAccessibilityAction(moveUpLabel) {
                            onMove(
                                element.elementId,
                                element.x,
                                (element.y - step).coerceAtLeast(0f),
                            )
                            true
                        })
                        add(CustomAccessibilityAction(moveDownLabel) {
                            onMove(
                                element.elementId,
                                element.x,
                                (element.y + step).coerceAtMost(1f - element.height),
                            )
                            true
                        })
                        add(CustomAccessibilityAction(smallerLabel) {
                            onResize(
                                element.elementId,
                                (element.width * 0.9f).coerceAtLeast(0.05f),
                                (element.height * 0.9f).coerceAtLeast(0.05f),
                            )
                            true
                        })
                        add(CustomAccessibilityAction(largerLabel) {
                            onResize(
                                element.elementId,
                                (element.width * 1.1f).coerceAtMost(1f - element.x),
                                (element.height * 1.1f).coerceAtMost(1f - element.y),
                            )
                            true
                        })
                        if (element.deletable) {
                            add(CustomAccessibilityAction(deleteLabel) {
                                onDelete(element.elementId)
                                true
                            })
                        }
                    }
                } else {
                    emptyList()
                }
            }
            .pointerInput(element.elementId, canMutate, canvasSize) {
                if (!canMutate || canvasSize == IntSize.Zero) return@pointerInput
                var currentX = element.x
                var currentY = element.y
                detectDragGestures(
                    onDragStart = {
                        currentX = element.x
                        currentY = element.y
                        onSelected()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    currentX = (currentX + dragAmount.x / canvasSize.width)
                        .coerceIn(0f, 1f - element.width)
                    currentY = (currentY + dragAmount.y / canvasSize.height)
                        .coerceIn(0f, 1f - element.height)
                    onMove(
                        element.elementId,
                        currentX,
                        currentY,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = controlKindLabel(element.kind),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (element.kind == LayoutControlKind.UNKNOWN) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun EditorTools(
    state: LayoutEditorSessionState,
    selectedElementId: String?,
    onResize: (String, Float, Float) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (LayoutControlKind) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.elements.firstOrNull { it.elementId == selectedElementId }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.ligase_layout_add_control),
                fontWeight = FontWeight.Bold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                editableControlKinds.forEach { kind ->
                    OutlinedButton(onClick = { onAdd(kind) }) {
                        Text(controlKindLabel(kind))
                    }
                }
            }
            selected?.let { element ->
                Text(
                    text = stringResource(
                        R.string.ligase_layout_selected_control,
                        controlKindLabel(element.kind),
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                if (element.kind == LayoutControlKind.UNKNOWN) {
                    Text(
                        text = stringResource(R.string.ligase_layout_unknown_read_only),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onResize(
                                    element.elementId,
                                    (element.width * 0.9f).coerceAtLeast(0.05f),
                                    (element.height * 0.9f).coerceAtLeast(0.05f),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.ligase_layout_smaller))
                        }
                        OutlinedButton(
                            onClick = {
                                onResize(
                                    element.elementId,
                                    (element.width * 1.1f)
                                        .coerceAtMost(1f - element.x),
                                    (element.height * 1.1f)
                                        .coerceAtMost(1f - element.y),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.ligase_layout_larger))
                        }
                    }
                    OutlinedButton(
                        onClick = { onDelete(element.elementId) },
                        enabled = element.deletable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ligase_layout_delete_control))
                    }
                }
            }
            state.error?.let {
                Text(
                    text = layoutErrorMessage(it),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = state.dirty && !state.saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (state.saving) {
                                R.string.ligase_layout_saving
                            } else {
                                R.string.ligase_layout_save
                            },
                        ),
                    )
                }
            }
        }
    }
}

private val editableControlKinds = listOf(
    LayoutControlKind.KEYBOARD_KEY,
    LayoutControlKind.MOUSE_BUTTON,
    LayoutControlKind.ANALOG_STICK,
    LayoutControlKind.DPAD,
    LayoutControlKind.SOFT_KEYBOARD,
)

@Composable
private fun controlKindLabel(kind: LayoutControlKind): String = stringResource(
    when (kind) {
        LayoutControlKind.KEYBOARD_KEY -> R.string.ligase_layout_control_key
        LayoutControlKind.MOUSE_BUTTON -> R.string.ligase_layout_control_mouse
        LayoutControlKind.ANALOG_STICK -> R.string.ligase_layout_control_stick
        LayoutControlKind.DPAD -> R.string.ligase_layout_control_dpad
        LayoutControlKind.SOFT_KEYBOARD -> R.string.ligase_layout_control_keyboard
        LayoutControlKind.UNKNOWN -> R.string.ligase_layout_control_unknown
    },
)
