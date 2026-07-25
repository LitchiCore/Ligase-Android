package com.limelight.ligase.feature.input.layout.v2.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.LigasePageScaffold
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.editor.*
import com.limelight.ligase.feature.input.layout.v2.presentation.*
import kotlin.math.roundToInt

@Composable
fun LayoutV2EditorScreen(
    state: LayoutV2EditorState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onSetAnchors: (String, HorizontalAnchor, VerticalAnchor) -> Unit,
    onSetZOrder: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateProperties: (String, LayoutV2EditableProperties) -> Unit,
    onAdd: (ControlKind) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val presentation = presentLayoutV2Editor(state)
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        if (state.dirty) confirmLeave = true else onBack()
    }
    BackHandler(onBack = requestBack)
    LigasePageScaffold(
        title = state.draft?.displayName
            ?: stringResource(R.string.ligase_layout_v2_editor_title),
        onBack = requestBack,
    ) { pageModifier ->
        BoxWithConstraints(pageModifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp || maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LayoutV2Canvas(
                        state = state,
                        onSelect = onSelect,
                        onMove = onMove,
                        onResize = onResize,
                        modifier = Modifier
                            .weight(layoutV2EditorCanvasWeight(true))
                            .fillMaxHeight()
                            .padding(16.dp),
                    )
                    LayoutV2EditorTools(
                        state, presentation, onMove, onResize, onSetAnchors, onSetZOrder,
                        onDelete, onUpdateProperties, onAdd, onValidate, onSave, onDiscard,
                        Modifier.weight(0.34f).fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LayoutV2Canvas(
                        state = state,
                        onSelect = onSelect,
                        onMove = onMove,
                        onResize = onResize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(12.dp),
                    )
                    LayoutV2EditorTools(
                        state, presentation, onMove, onResize, onSetAnchors, onSetZOrder,
                        onDelete, onUpdateProperties, onAdd, onValidate, onSave, onDiscard,
                        Modifier.fillMaxWidth().heightIn(max = 310.dp),
                    )
                }
            }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.ligase_layout_v2_unsaved_title)) },
            text = { Text(stringResource(R.string.ligase_layout_v2_unsaved_message)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        confirmLeave = false
                        onDiscard()
                        onBack()
                    }) { Text(stringResource(R.string.ligase_layout_v2_discard)) }
                    Button(onClick = {
                        confirmLeave = false
                        onBack()
                    }) { Text(stringResource(R.string.ligase_layout_v2_keep_draft_leave)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LayoutV2Canvas(
    state: LayoutV2EditorState,
    onSelect: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    modifier: Modifier,
) {
    val draft = state.draft
    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        if (draft == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ligase_layout_v2_no_draft))
            }
            return@Card
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            var transform by remember(draft.identity.layoutId, widthPx, heightPx) {
                mutableStateOf(
                LayoutV2CanvasTransform(draft.canvas, widthPx, heightPx)
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(draft.identity.layoutId, widthPx, heightPx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            transform = transform.transformed(zoom, pan.x, pan.y)
                        }
                    },
            )
            draft.elements.sortedBy(LayoutV2EditorElement::zOrder).forEach { element ->
                val rect = transform.screenRect(element.rect)
                val selected = element.elementId == state.selectedElementId
                var dragX by remember(element.elementId, element.rect) { mutableFloatStateOf(0f) }
                var dragY by remember(element.elementId, element.rect) { mutableFloatStateOf(0f) }
                var resizeX by remember(element.elementId, element.rect) { mutableFloatStateOf(0f) }
                var resizeY by remember(element.elementId, element.rect) { mutableFloatStateOf(0f) }
                Box(
                    Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(
                            with(density) { rect.width.toDp() },
                            with(density) { rect.height.toDp() },
                        )
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        )
                        .border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(element.elementId) }
                        .then(
                            if (element.canMove()) {
                                Modifier.pointerInput(element.elementId, element.rect, transform) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragX = 0f
                                            dragY = 0f
                                        },
                                        onDragEnd = {
                                            val delta = transform.canvasDelta(dragX, dragY)
                                            onMove(
                                                element.elementId,
                                                element.rect.x + delta.first,
                                                element.rect.y + delta.second,
                                            )
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        dragX += amount.x
                                        dragY += amount.y
                                    }
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = controlKindShortLabel(element.kind),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (selected && element.canResize()) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(topStart = 8.dp),
                                )
                                .pointerInput(element.elementId, element.rect, transform) {
                                    detectDragGestures(
                                        onDragStart = {
                                            resizeX = 0f
                                            resizeY = 0f
                                        },
                                        onDragEnd = {
                                            val delta = transform.canvasDelta(resizeX, resizeY)
                                            onResize(
                                                element.elementId,
                                                element.rect.width + delta.first,
                                                element.rect.height + delta.second,
                                            )
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        resizeX += amount.x
                                        resizeY += amount.y
                                    }
                                },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    transform = LayoutV2CanvasTransform(draft.canvas, widthPx, heightPx)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Text(stringResource(R.string.ligase_layout_v2_fit_canvas))
            }
        }
    }
}

@Composable
private fun LayoutV2EditorTools(
    state: LayoutV2EditorState,
    presentation: LayoutV2EditorPresentation,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onSetAnchors: (String, HorizontalAnchor, VerticalAnchor) -> Unit,
    onSetZOrder: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateProperties: (String, LayoutV2EditableProperties) -> Unit,
    onAdd: (ControlKind) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier,
) {
    val selected = state.draft?.elements?.firstOrNull {
        it.elementId == state.selectedElementId
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.ligase_layout_v2_runtime_unverified),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (presentation.showRecoveryWarning) {
            Text(
                stringResource(R.string.ligase_layout_v2_journal_warning),
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.issue?.let {
            Text(layoutV2IssueText(it), color = MaterialTheme.colorScheme.error)
        }
        if (!presentation.hasElements) {
            Text(stringResource(R.string.ligase_layout_v2_empty_cannot_save))
        }
        selected?.let { element ->
            Text(
                controlKindLabel(element.kind),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.ligase_layout_v2_element_geometry,
                    element.rect.x,
                    element.rect.y,
                    element.rect.width,
                    element.rect.height,
                ),
            )
            if (element.isInspectOnly()) {
                Text(stringResource(R.string.ligase_layout_v2_inspect_only))
            } else {
                LayoutV2TypedPropertiesInspector(element, onUpdateProperties)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onMove(element.elementId, element.rect.x - 1, element.rect.y)
                    }) { Text("←") }
                    OutlinedButton(onClick = {
                        onMove(element.elementId, element.rect.x + 1, element.rect.y)
                    }) { Text("→") }
                    OutlinedButton(onClick = {
                        onMove(element.elementId, element.rect.x, element.rect.y - 1)
                    }) { Text("↑") }
                    OutlinedButton(onClick = {
                        onMove(element.elementId, element.rect.x, element.rect.y + 1)
                    }) { Text("↓") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onResize(
                            element.elementId,
                            element.rect.width + 1,
                            element.rect.height + 1,
                        )
                    }) { Text(stringResource(R.string.ligase_layout_v2_enlarge)) }
                    OutlinedButton(onClick = {
                        onResize(
                            element.elementId,
                            element.rect.width - 1,
                            element.rect.height - 1,
                        )
                    }) { Text(stringResource(R.string.ligase_layout_v2_shrink)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onSetZOrder(element.elementId, element.zOrder - 1)
                    }) { Text(stringResource(R.string.ligase_layout_v2_send_backward)) }
                    OutlinedButton(onClick = {
                        onSetZOrder(element.elementId, element.zOrder + 1)
                    }) { Text(stringResource(R.string.ligase_layout_v2_bring_forward)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onSetAnchors(
                            element.elementId,
                            HorizontalAnchor.entries[
                                (element.horizontalAnchor.ordinal + 1) %
                                    HorizontalAnchor.entries.size
                            ],
                            element.verticalAnchor,
                        )
                    }) {
                        Text(
                            stringResource(
                                R.string.ligase_layout_v2_horizontal_anchor,
                                element.horizontalAnchor.name,
                            ),
                        )
                    }
                    OutlinedButton(onClick = {
                        onSetAnchors(
                            element.elementId,
                            element.horizontalAnchor,
                            VerticalAnchor.entries[
                                (element.verticalAnchor.ordinal + 1) %
                                    VerticalAnchor.entries.size
                            ],
                        )
                    }) {
                        Text(
                            stringResource(
                                R.string.ligase_layout_v2_vertical_anchor,
                                element.verticalAnchor.name,
                            ),
                        )
                    }
                }
                OutlinedButton(onClick = { onDelete(element.elementId) }) {
                    Text(stringResource(R.string.ligase_layout_v2_delete))
                }
            }
        }
        Text(
            stringResource(R.string.ligase_layout_v2_add_control),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddControlButton(ControlKind.KEYBOARD, onAdd)
            AddControlButton(ControlKind.MOUSE, onAdd)
            AddControlButton(ControlKind.SOFT_KEYBOARD, onAdd)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddControlButton(ControlKind.ANALOG, onAdd)
            AddControlButton(ControlKind.DPAD, onAdd)
        }
        Button(
            onClick = {
                onValidate()
                onSave()
            },
            enabled = presentation.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.ligase_layout_v2_save)) }
        OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ligase_layout_v2_discard))
        }
    }
}

@Composable
private fun LayoutV2TypedPropertiesInspector(
    element: LayoutV2EditorElement,
    onUpdateProperties: (String, LayoutV2EditableProperties) -> Unit,
) {
    when (val properties = element.editableProperties) {
        is LayoutV2EditableProperties.Keyboard -> {
            Text(
                stringResource(
                    R.string.ligase_layout_v2_keyboard_code,
                    properties.inputCode.namespace.name,
                    properties.inputCode.code,
                ),
            )
            TriggerSelector(properties.trigger) { trigger ->
                onUpdateProperties(element.elementId, properties.copy(trigger = trigger))
            }
        }
        is LayoutV2EditableProperties.Mouse -> {
            Text(stringResource(R.string.ligase_layout_v2_mouse_button, properties.button))
            TriggerSelector(properties.trigger) { trigger ->
                onUpdateProperties(element.elementId, properties.copy(trigger = trigger))
            }
        }
        is LayoutV2EditableProperties.Analog -> {
            Text(
                stringResource(
                    R.string.ligase_layout_v2_directional_summary,
                    properties.diagonalPolicy,
                ),
            )
        }
        is LayoutV2EditableProperties.Dpad -> {
            Text(
                stringResource(
                    R.string.ligase_layout_v2_directional_summary,
                    properties.diagonalPolicy,
                ),
            )
        }
        LayoutV2EditableProperties.SoftKeyboard -> {
            Text(stringResource(R.string.ligase_layout_v2_soft_keyboard_summary))
        }
        null -> Unit
    }
}

@Composable
private fun TriggerSelector(
    selected: Trigger,
    onSelected: (Trigger) -> Unit,
) {
    Text(stringResource(R.string.ligase_layout_v2_trigger))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Trigger.entries.forEach { trigger ->
            FilterChip(
                selected = trigger == selected,
                onClick = { onSelected(trigger) },
                label = { Text(trigger.name.lowercase().replace('_', ' ')) },
            )
        }
    }
}

@Composable
private fun AddControlButton(
    kind: ControlKind,
    onAdd: (ControlKind) -> Unit,
) {
    OutlinedButton(
        onClick = { onAdd(kind) },
    ) { Text(controlKindShortLabel(kind)) }
}

@Composable
private fun controlKindLabel(kind: ControlKind): String =
    stringResource(controlKindLabelResource(kind))

@Composable
private fun controlKindShortLabel(kind: ControlKind): String =
    stringResource(controlKindLabelResource(kind))

private fun controlKindLabelResource(kind: ControlKind): Int = when (kind) {
    ControlKind.KEYBOARD -> R.string.ligase_layout_v2_kind_keyboard
    ControlKind.MOUSE -> R.string.ligase_layout_v2_kind_mouse
    ControlKind.ANALOG -> R.string.ligase_layout_v2_kind_analog
    ControlKind.DPAD -> R.string.ligase_layout_v2_kind_dpad
    ControlKind.CUSTOM_KEYS -> R.string.ligase_layout_v2_kind_custom_keys
    ControlKind.RADIAL -> R.string.ligase_layout_v2_kind_radial
    ControlKind.SCROLL -> R.string.ligase_layout_v2_kind_scroll
    ControlKind.COMBO -> R.string.ligase_layout_v2_kind_combo
    ControlKind.SOFT_KEYBOARD -> R.string.ligase_layout_v2_kind_soft_keyboard
    ControlKind.GYRO -> R.string.ligase_layout_v2_kind_gyro
}

@Composable
private fun layoutV2IssueText(issue: LayoutV2EditorIssue): String =
    stringResource(
        when (issue) {
            LayoutV2EditorIssue.COLLISION -> R.string.ligase_layout_v2_issue_collision
            LayoutV2EditorIssue.JOURNAL_WRITE_FAILED ->
                R.string.ligase_layout_v2_journal_warning
            LayoutV2EditorIssue.READ_ONLY_KIND -> R.string.ligase_layout_v2_inspect_only
            LayoutV2EditorIssue.OUT_OF_CANVAS,
            LayoutV2EditorIssue.INVALID_RECT -> R.string.ligase_layout_v2_issue_bounds
            else -> R.string.ligase_layout_v2_issue_generic
        },
    )
