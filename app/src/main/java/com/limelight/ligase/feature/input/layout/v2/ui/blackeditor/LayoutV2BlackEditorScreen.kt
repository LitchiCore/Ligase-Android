package com.limelight.ligase.feature.input.layout.v2.ui.blackeditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.feature.input.layout.v2.application.LayoutV2EditorWorkspaceUiState
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.editor.*
import com.limelight.ligase.feature.input.layout.v2.presentation.*
import kotlin.math.roundToInt

@Composable
fun LayoutV2BlackEditorScreen(
    workspace: LayoutV2EditorWorkspaceUiState,
    handoff: LayoutV2EditorHandoffResult,
    onSelect: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onSetAnchors: (String, HorizontalAnchor, VerticalAnchor) -> Unit,
    onSetZOrder: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateProperties: (String, LayoutV2EditableProperties) -> Unit,
    onAdd: (ControlKind) -> Unit,
    onValidate: () -> Unit,
    onKeepAndFinish: () -> Unit,
    onSaveAndFinish: () -> Unit,
    onDiscardAndFinish: () -> Unit,
    onAbort: () -> Unit,
) {
    val state = workspace.editor
    val presentation = presentLayoutV2Editor(state)
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    val toolsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showTools) {
        if (showTools) toolsFocusRequester.requestFocus()
    }
    BackHandler {
        when (
            layoutV2BlackEditorBackAction(
                handoffReady = handoff is LayoutV2EditorHandoffResult.LaunchReady,
                toolsOpen = showTools,
                dirty = state.dirty,
            )
        ) {
            LayoutV2BlackEditorBackAction.ABORT -> onAbort()
            LayoutV2BlackEditorBackAction.CLOSE_TOOLS -> showTools = false
            LayoutV2BlackEditorBackAction.CONFIRM_LEAVE -> showLeaveDialog = true
            LayoutV2BlackEditorBackAction.FINISH -> onKeepAndFinish()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (handoff !is LayoutV2EditorHandoffResult.LaunchReady || state.draft == null) {
            BlackEditorUnavailable(handoff, onAbort)
        } else {
            Box(Modifier.fillMaxSize()) {
                BlackTouchCanvas(
                    state = state,
                    onSelect = onSelect,
                    onMove = onMove,
                    onResize = onResize,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!showTools) {
                    FilledTonalButton(
                        onClick = { showTools = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(18.dp),
                    ) {
                        Text(stringResource(R.string.ligase_layout_v2_open_tools))
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable(
                                onClickLabel = stringResource(
                                    R.string.ligase_layout_v2_close_tools,
                                ),
                            ) { showTools = false },
                    )
                    BlackEditorPanel(
                        state = state,
                        presentation = presentation,
                        onMove = onMove,
                        onResize = onResize,
                        onSetAnchors = onSetAnchors,
                        onSetZOrder = onSetZOrder,
                        onDelete = onDelete,
                        onUpdateProperties = onUpdateProperties,
                        onAdd = onAdd,
                        onValidate = onValidate,
                        onSaveAndFinish = onSaveAndFinish,
                        onRequestLeave = {
                            if (state.dirty) showLeaveDialog = true else onKeepAndFinish()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.38f)
                            .widthIn(max = 460.dp)
                            .focusRequester(toolsFocusRequester)
                            .focusGroup()
                            .focusable(),
                    )
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.ligase_layout_v2_unsaved_title)) },
            text = { Text(stringResource(R.string.ligase_layout_v2_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    onKeepAndFinish()
                }) { Text(stringResource(R.string.ligase_layout_v2_keep_draft_leave)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showLeaveDialog = false
                        onDiscardAndFinish()
                    }) { Text(stringResource(R.string.ligase_layout_v2_discard)) }
                    TextButton(onClick = { showLeaveDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun BlackTouchCanvas(
    state: LayoutV2EditorState,
    onSelect: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    modifier: Modifier,
) {
    val draft = requireNotNull(state.draft)
    BoxWithConstraints(modifier.background(Color.Black)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val viewport = remember(widthPx, heightPx) {
            LayoutV2BlackEditorViewportMapper.viewport(widthPx, heightPx)
        } ?: return@BoxWithConstraints
        val content = viewport.contentRectPx

        Box(
            Modifier
                .offset { IntOffset(content.x, content.y) }
                .size(
                    with(density) { content.width.toDp() },
                    with(density) { content.height.toDp() },
                )
                .background(Color(0xFF090A0D))
                .border(1.dp, Color(0xFF555D6D)),
        )

        draft.elements.sortedBy(LayoutV2EditorElement::zOrder).forEach { element ->
            BlackTouchElement(
                element = element,
                viewport = viewport,
                isSelected = state.selectedElementId == element.elementId,
                onSelect = onSelect,
                onMove = onMove,
                onResize = onResize,
            )
        }

        Text(
            text = stringResource(R.string.ligase_layout_v2_runtime_unverified),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BlackTouchElement(
    element: LayoutV2EditorElement,
    viewport: LayoutV2EditorViewport,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val baseRect = LayoutV2BlackEditorViewportMapper.canvasRectToPixels(viewport, element.rect)
    var previewDx by remember(element.elementId, element.rect) { mutableFloatStateOf(0f) }
    var previewDy by remember(element.elementId, element.rect) { mutableFloatStateOf(0f) }
    var resizeDx by remember(element.elementId, element.rect) { mutableFloatStateOf(0f) }
    var resizeDy by remember(element.elementId, element.rect) { mutableFloatStateOf(0f) }
    val previewRect = IntRect(
        baseRect.x + previewDx.roundToInt(),
        baseRect.y + previewDy.roundToInt(),
        (baseRect.width + resizeDx.roundToInt()).coerceAtLeast(1),
        (baseRect.height + resizeDy.roundToInt()).coerceAtLeast(1),
    )
    val label = blackEditorKindLabel(element.kind)
    val semanticsText = stringResource(
        R.string.ligase_layout_v2_element_geometry,
        element.rect.x, element.rect.y, element.rect.width, element.rect.height,
    )
    val resizeDescription = stringResource(R.string.ligase_layout_v2_resize_handle, label)

    Box(
        Modifier
            .offset { IntOffset(previewRect.x, previewRect.y) }
            .size(
                with(density) { previewRect.width.toDp() },
                with(density) { previewRect.height.toDp() },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. $semanticsText"
                selected = isSelected
                onClick {
                    onSelect(element.elementId)
                    true
                }
            }
            .background(
                if (isSelected) Color(0x6635315C) else Color(0x462A2E39),
                CircleShape,
            )
            .border(
                if (isSelected) 3.dp else 1.dp,
                if (isSelected) Color(0xFFB8B1FF) else Color.White.copy(alpha = 0.52f),
                CircleShape,
            )
            .pointerInput(element.elementId, element.rect, viewport) {
                detectDragGestures(
                    onDragStart = {
                        onSelect(element.elementId)
                        previewDx = 0f
                        previewDy = 0f
                    },
                    onDragEnd = {
                        val mapped = LayoutV2BlackEditorViewportMapper.pointerToCanvas(
                            viewport,
                            LayoutV2EditorPoint(
                                baseRect.x + previewDx.roundToInt(),
                                baseRect.y + previewDy.roundToInt(),
                            ),
                        )
                        if (element.canMove()) onMove(element.elementId, mapped.x, mapped.y)
                        previewDx = 0f
                        previewDy = 0f
                    },
                    onDragCancel = {
                        previewDx = 0f
                        previewDy = 0f
                    },
                ) { change, amount ->
                    change.consume()
                    if (element.canMove()) {
                        previewDx += amount.x
                        previewDy += amount.y
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        if (isSelected && element.canResize()) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .background(Color(0xFFB8B1FF), RoundedCornerShape(topStart = 10.dp))
                    .semantics { contentDescription = resizeDescription }
                    .pointerInput(element.elementId, element.rect, viewport) {
                        detectDragGestures(
                            onDragStart = {
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDragEnd = {
                                val mapped = LayoutV2BlackEditorViewportMapper.pixelsRectToCanvas(
                                    viewport,
                                    IntRect(
                                        baseRect.x,
                                        baseRect.y,
                                        (baseRect.width + resizeDx.roundToInt()).coerceAtLeast(1),
                                        (baseRect.height + resizeDy.roundToInt()).coerceAtLeast(1),
                                    ),
                                )
                                onResize(element.elementId, mapped.width, mapped.height)
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDragCancel = {
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                        ) { change, amount ->
                            change.consume()
                            resizeDx += amount.x
                            resizeDy += amount.y
                        }
                    },
            )
        }
    }
}

@Composable
private fun BlackEditorPanel(
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
    onSaveAndFinish: () -> Unit,
    onRequestLeave: () -> Unit,
    modifier: Modifier,
) {
    val selected = state.draft?.elements?.firstOrNull {
        it.elementId == state.selectedElementId
    }
    Column(
        modifier
            .background(Color(0xF220232C))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.draft?.displayName.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestLeave) {
                Text(stringResource(R.string.ligase_layout_v2_keep_draft_leave))
            }
        }
        if (presentation.showRecoveryWarning) {
            Text(
                stringResource(R.string.ligase_layout_v2_journal_warning),
                color = Color(0xFFF4B860),
            )
        }
        state.issue?.let {
            Text(blackEditorIssueText(it), color = Color(0xFFFF7B72))
        }
        selected?.let { element ->
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Text(blackEditorKindLabel(element.kind), color = Color.White)
            Text(
                stringResource(
                    R.string.ligase_layout_v2_element_geometry,
                    element.rect.x, element.rect.y, element.rect.width, element.rect.height,
                ),
                color = Color.White.copy(alpha = 0.75f),
            )
            if (element.isInspectOnly()) {
                Text(
                    stringResource(R.string.ligase_layout_v2_inspect_only),
                    color = Color(0xFFF4B860),
                )
            } else {
                BlackPropertySummary(element, onUpdateProperties)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorTinyButton("←") {
                        onMove(element.elementId, element.rect.x - 1, element.rect.y)
                    }
                    EditorTinyButton("↑") {
                        onMove(element.elementId, element.rect.x, element.rect.y - 1)
                    }
                    EditorTinyButton("↓") {
                        onMove(element.elementId, element.rect.x, element.rect.y + 1)
                    }
                    EditorTinyButton("→") {
                        onMove(element.elementId, element.rect.x + 1, element.rect.y)
                    }
                }
                EditorTinyButton(
                    stringResource(
                        R.string.ligase_layout_v2_horizontal_anchor,
                        element.horizontalAnchor.name,
                    ),
                ) {
                    onSetAnchors(
                        element.elementId,
                        HorizontalAnchor.entries[
                            (element.horizontalAnchor.ordinal + 1) %
                                HorizontalAnchor.entries.size
                        ],
                        element.verticalAnchor,
                    )
                }
                EditorTinyButton(
                    stringResource(
                        R.string.ligase_layout_v2_vertical_anchor,
                        element.verticalAnchor.name,
                    ),
                ) {
                    onSetAnchors(
                        element.elementId,
                        element.horizontalAnchor,
                        VerticalAnchor.entries[
                            (element.verticalAnchor.ordinal + 1) %
                                VerticalAnchor.entries.size
                        ],
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorTinyButton(stringResource(R.string.ligase_layout_v2_shrink)) {
                        onResize(
                            element.elementId,
                            element.rect.width - 1,
                            element.rect.height - 1,
                        )
                    }
                    EditorTinyButton(stringResource(R.string.ligase_layout_v2_enlarge)) {
                        onResize(
                            element.elementId,
                            element.rect.width + 1,
                            element.rect.height + 1,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorTinyButton(stringResource(R.string.ligase_layout_v2_send_backward)) {
                        onSetZOrder(element.elementId, element.zOrder - 1)
                    }
                    EditorTinyButton(stringResource(R.string.ligase_layout_v2_bring_forward)) {
                        onSetZOrder(element.elementId, element.zOrder + 1)
                    }
                }
                OutlinedButton(
                    onClick = { onDelete(element.elementId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.ligase_layout_v2_delete)) }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Text(stringResource(R.string.ligase_layout_v2_add_control), color = Color.White)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                ControlKind.KEYBOARD,
                ControlKind.MOUSE,
                ControlKind.ANALOG,
                ControlKind.DPAD,
                ControlKind.SOFT_KEYBOARD,
            ).forEach { kind ->
                EditorTinyButton(blackEditorKindLabel(kind)) { onAdd(kind) }
            }
        }
        Button(
            onClick = {
                onValidate()
                onSaveAndFinish()
            },
            enabled = presentation.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.ligase_layout_v2_save)) }
        Text(
            stringResource(R.string.ligase_layout_v2_runtime_unverified),
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BlackPropertySummary(
    element: LayoutV2EditorElement,
    onUpdate: (String, LayoutV2EditableProperties) -> Unit,
) {
    when (val p = element.editableProperties) {
        is LayoutV2EditableProperties.Keyboard -> {
            Text(
                stringResource(
                    R.string.ligase_layout_v2_keyboard_code,
                    p.inputCode.namespace.name,
                    p.inputCode.code,
                ),
                color = Color.White.copy(alpha = 0.8f),
            )
            EditorTinyButton(p.trigger.name) {
                val next = Trigger.entries[(p.trigger.ordinal + 1) % Trigger.entries.size]
                onUpdate(element.elementId, p.copy(trigger = next))
            }
        }
        is LayoutV2EditableProperties.Mouse -> {
            Text(
                stringResource(R.string.ligase_layout_v2_mouse_button, p.button),
                color = Color.White.copy(alpha = 0.8f),
            )
            EditorTinyButton(p.trigger.name) {
                val next = Trigger.entries[(p.trigger.ordinal + 1) % Trigger.entries.size]
                onUpdate(element.elementId, p.copy(trigger = next))
            }
        }
        is LayoutV2EditableProperties.Analog ->
            Text(p.diagonalPolicy, color = Color.White.copy(alpha = 0.8f))
        is LayoutV2EditableProperties.Dpad ->
            Text(p.diagonalPolicy, color = Color.White.copy(alpha = 0.8f))
        LayoutV2EditableProperties.SoftKeyboard ->
            Text(
                stringResource(R.string.ligase_layout_v2_soft_keyboard_summary),
                color = Color.White.copy(alpha = 0.8f),
            )
        null -> Unit
    }
}

@Composable
private fun EditorTinyButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(10.dp, 6.dp)) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun BlackEditorUnavailable(
    handoff: LayoutV2EditorHandoffResult,
    onFinish: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.ligase_layout_v2_no_draft),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        if (handoff is LayoutV2EditorHandoffResult.Rejected) {
            Text(
                stringResource(R.string.ligase_layout_v2_handoff_failed),
                color = Color(0xFFFF7B72),
                modifier = Modifier.padding(12.dp),
            )
        }
        Button(onClick = onFinish) { Text(stringResource(R.string.ligase_back)) }
    }
}

@Composable
private fun blackEditorKindLabel(kind: ControlKind): String = stringResource(
    when (kind) {
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
    },
)

@Composable
private fun blackEditorIssueText(issue: LayoutV2EditorIssue): String = stringResource(
    when (issue) {
        LayoutV2EditorIssue.COLLISION -> R.string.ligase_layout_v2_issue_collision
        LayoutV2EditorIssue.JOURNAL_WRITE_FAILED -> R.string.ligase_layout_v2_journal_warning
        LayoutV2EditorIssue.READ_ONLY_KIND -> R.string.ligase_layout_v2_inspect_only
        LayoutV2EditorIssue.OUT_OF_CANVAS,
        LayoutV2EditorIssue.INVALID_RECT -> R.string.ligase_layout_v2_issue_bounds
        else -> R.string.ligase_layout_v2_issue_generic
    },
)
