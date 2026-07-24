package com.limelight.ligase.feature.layout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.limelight.R
import com.limelight.ligase.LigasePageScaffold
import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorElement
import com.limelight.ligase.feature.layout.domain.LayoutEditorSessionState
import com.limelight.ligase.feature.layout.presentation.LayoutCanvasSize
import com.limelight.ligase.feature.layout.presentation.LayoutCanvasViewportState
import com.limelight.ligase.feature.layout.presentation.fitLayoutCanvas
import com.limelight.ligase.feature.layout.presentation.showCanvasElementLabel
import kotlin.math.max
import kotlin.math.roundToInt

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
                        modifier = Modifier
                            .weight(0.66f)
                            .fillMaxHeight(),
                    )
                    EditorTools(
                        state = state,
                        selectedElementId = selectedElementId,
                        onResize = onResize,
                        onDelete = onDelete,
                        onAdd = onAdd,
                        onSave = onSave,
                        onCancel = requestBack,
                        scrollable = true,
                        modifier = Modifier
                            .weight(0.34f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LayoutCanvas(
                        state = state,
                        selectedElementId = selectedElementId,
                        onSelected = { selectedElementId = it },
                        onMove = onMove,
                        onResize = onResize,
                        onDelete = onDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 560.dp)
                            .aspectRatio(state.canvasAspectRatio.coerceIn(0.75f, 2.4f)),
                    )
                    EditorTools(
                        state = state,
                        selectedElementId = selectedElementId,
                        onResize = onResize,
                        onDelete = onDelete,
                        onAdd = onAdd,
                        onSave = onSave,
                        onCancel = requestBack,
                        scrollable = false,
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
    var viewport by remember(state.draftId) { mutableStateOf(LayoutCanvasViewportState()) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fitted = fitLayoutCanvas(
            available = LayoutCanvasSize(maxWidth.value, maxHeight.value),
            referenceAspectRatio = state.canvasAspectRatio,
        )
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .size(fitted.width.dp, fitted.height.dp)
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(24.dp),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(24.dp),
                )
                .pointerInput(canvasSize, viewport) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        viewport = viewport.transformed(
                            zoomChange = zoom,
                            panChangeX = pan.x,
                            panChangeY = pan.y,
                            frame = LayoutCanvasSize(
                                canvasSize.width.toFloat(),
                                canvasSize.height.toFloat(),
                            ),
                        )
                    }
                }
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
                    viewport = viewport,
                    selected = selectedElementId == element.elementId,
                    onSelected = { onSelected(element.elementId) },
                    onMove = onMove,
                    onResize = onResize,
                    onDelete = onDelete,
                )
            }
            OutlinedButton(
                onClick = { viewport = viewport.reset() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            ) {
                Text(
                    if (viewport.zoom == LayoutCanvasViewportState.MIN_ZOOM) {
                        stringResource(R.string.ligase_layout_fit_canvas)
                    } else {
                        stringResource(
                            R.string.ligase_layout_reset_zoom,
                            (viewport.zoom * 100).roundToInt(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LayoutElement(
    element: LayoutEditorElement,
    canvasSize: IntSize,
    viewport: LayoutCanvasViewportState,
    selected: Boolean,
    onSelected: () -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Float, Float) -> Unit,
    onDelete: (String) -> Unit,
) {
    val density = LocalDensity.current
    val widthDp = with(density) {
        max(if (selected) 80f else 8f, element.width * canvasSize.width * viewport.zoom)
            .toDp()
    }
    val heightDp = with(density) {
        max(if (selected) 52f else 8f, element.height * canvasSize.height * viewport.zoom)
            .toDp()
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
    val kindLabel = controlKindLabel(element.kind)
    Box(
        modifier = Modifier
            .zIndex(if (selected) 2f else 1f)
            .offset {
                IntOffset(
                    viewport.screenX(element.x, canvasSize.width.toFloat()).roundToInt(),
                    viewport.screenY(element.y, canvasSize.height.toFloat()).roundToInt(),
                )
            }
            .size(widthDp, heightDp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                },
                RoundedCornerShape(if (selected) 12.dp else 4.dp),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                RoundedCornerShape(if (selected) 12.dp else 4.dp),
            )
            .semantics {
                contentDescription = "$kindLabel, ${element.elementId}"
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
            .pointerInput(element.elementId, canMutate, canvasSize, viewport.zoom) {
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
                    currentX = (
                        currentX +
                            viewport.normalizedDeltaX(
                                dragAmount.x,
                                canvasSize.width.toFloat(),
                            )
                        )
                        .coerceIn(0f, 1f - element.width)
                    currentY = (
                        currentY +
                            viewport.normalizedDeltaY(
                                dragAmount.y,
                                canvasSize.height.toFloat(),
                            )
                        )
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
        if (showCanvasElementLabel(selected)) {
            Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (element.kind == LayoutControlKind.UNKNOWN) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(topStart = 6.dp),
                    ),
            )
        }
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
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val selected = state.elements.firstOrNull { it.elementId == selectedElementId }
    Card(
        modifier = if (scrollable) {
            modifier.verticalScroll(rememberScrollState())
        } else {
            modifier
        },
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
                Text(
                    text = element.elementId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
