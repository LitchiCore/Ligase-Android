package com.limelight.ligase.feature.input.layout.v3.ui.blackeditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.limelight.R
import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3EditorWorkspaceUiState
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.*
import com.limelight.ligase.feature.input.layout.v3.presentation.*
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun LayoutV3BlackEditorScreen(
    workspace: LayoutV3EditorWorkspaceUiState,
    handoff: LayoutV3EditorHandoffResult,
    onFullOverlayReady: (Int, Int) -> Unit,
    onSelect: (String) -> Unit,
    onBeginGesture: (String) -> LayoutV3GestureStartResult,
    onCommitPixelMove: (LayoutV3GestureCommitToken, IntRect, IntRect) -> LayoutV3EditResult,
    onCommitPixelResize: (LayoutV3GestureCommitToken, IntRect, IntRect) -> LayoutV3EditResult,
    onCancelGesture: (LayoutV3GestureCommitToken) -> LayoutV3EditResult,
    onNudge: (String, Int, Int) -> Unit,
    onSetZOrder: (String, Int) -> Unit,
    onSetOpacityPermille: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateProperties: (String, LayoutV3EditableProperties) -> Unit,
    onAdd: (ControlKind) -> Unit,
    onAddKeyboardKeys: (Set<InputCode>) -> Unit,
    onValidate: () -> Unit,
    onKeepAndFinish: () -> Unit,
    onSaveAndFinish: () -> Unit,
    onDiscardAndFinish: () -> Unit,
    onAbort: () -> Unit,
) {
    val state = workspace.editor
    val presentation = presentLayoutV3Editor(state)
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    var toolsModeName by rememberSaveable { mutableStateOf<String?>(null) }
    val toolsMode = toolsModeName?.let(LayoutV3EditorToolsMode::valueOf)
    val showTools = toolsMode != null
    var showKeyboardPicker by rememberSaveable { mutableStateOf(false) }
    var selectedKeyboardCodes by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var nudgePreview by remember { mutableStateOf<LayoutV3NudgeDelta?>(null) }
    val toolsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showTools) {
        if (showTools) toolsFocusRequester.requestFocus()
    }
    BackHandler {
        when (
            layoutV3BlackEditorBackAction(
                handoff = handoff,
                toolsOpen = showTools,
                dirty = state.dirty,
            )
        ) {
            LayoutV3BlackEditorBackAction.ABORT -> onAbort()
            LayoutV3BlackEditorBackAction.CLOSE_TOOLS -> {
                nudgePreview = null
                toolsModeName = null
            }
            LayoutV3BlackEditorBackAction.CONFIRM_LEAVE -> showLeaveDialog = true
            LayoutV3BlackEditorBackAction.FINISH -> onKeepAndFinish()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (handoff !is LayoutV3EditorHandoffResult.LaunchReady || state.draft == null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.roundToPx() }
                val heightPx = with(density) { maxHeight.roundToPx() }
                LaunchedEffect(handoff, widthPx, heightPx) {
                    if (
                        handoff == LayoutV3EditorHandoffResult.AwaitingViewport &&
                        widthPx > 0 &&
                        heightPx > 0
                    ) {
                        onFullOverlayReady(widthPx, heightPx)
                    }
                }
                BlackEditorUnavailable(handoff, onAbort)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                BlackTouchCanvas(
                    state = state,
                    onSelect = { elementId ->
                        nudgePreview = null
                        onSelect(elementId)
                    },
                    onBeginGesture = onBeginGesture,
                    onCommitPixelMove = onCommitPixelMove,
                    onCommitPixelResize = onCommitPixelResize,
                    onCancelGesture = onCancelGesture,
                    onOpenTools = {
                        toolsModeName = LayoutV3EditorToolsMode.CONTROL_PROPERTIES.name
                    },
                    nudgePreview = nudgePreview,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!showTools) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(18.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.selectedElementId != null) {
                            FilledTonalButton(onClick = {
                                toolsModeName =
                                    LayoutV3EditorToolsMode.CONTROL_PROPERTIES.name
                            }) {
                                Text(stringResource(R.string.ligase_layout_v3_control_properties))
                            }
                        }
                        FilledTonalButton(onClick = {
                            toolsModeName = LayoutV3EditorToolsMode.LAYOUT_SETTINGS.name
                        }) {
                            Text(stringResource(R.string.ligase_layout_v3_layout_settings))
                        }
                    }
                } else {
                    val selected = state.draft.elements.firstOrNull {
                        it.elementId == state.selectedElementId
                    }
                    val density = LocalDensity.current
                    val viewportWidthPx = with(density) { maxWidth.roundToPx() }
                    val viewportHeightPx = with(density) { maxHeight.roundToPx() }
                    val fullOverlay = IntRect(0, 0, viewportWidthPx, viewportHeightPx)
                    val selectedCenterPx = selected?.let { element ->
                        runCatching {
                            LayoutV3Geometry.mapEditorElement(
                                checkNotNull(state.draft).canvas,
                                element,
                                fullOverlay,
                            )
                        }.getOrNull()?.let { it.x + it.width / 2 }
                    }
                    val panelSide = layoutV3EditorPanelSide(
                        selectedCenterPx = selectedCenterPx,
                        viewportWidthPx = viewportWidthPx,
                    )
                    BlackEditorPanel(
                        state = state,
                        presentation = presentation,
                        mode = checkNotNull(toolsMode),
                        onNudgePreview = { preview -> nudgePreview = preview },
                        onNudgeCommit = { elementId, deltaX, deltaY ->
                            nudgePreview = null
                            onNudge(elementId, deltaX, deltaY)
                        },
                        onSetZOrder = onSetZOrder,
                        onSetOpacityPermille = onSetOpacityPermille,
                        onDelete = onDelete,
                        onUpdateProperties = onUpdateProperties,
                        onAdd = onAdd,
                        onOpenKeyboardPicker = {
                            selectedKeyboardCodes = emptyList()
                            showKeyboardPicker = true
                        },
                        onValidate = onValidate,
                        onSaveAndFinish = onSaveAndFinish,
                        onRequestLeave = {
                            if (state.dirty) showLeaveDialog = true else onKeepAndFinish()
                        },
                        onCloseTools = {
                            nudgePreview = null
                            toolsModeName = null
                        },
                        modifier = Modifier
                            .align(
                                if (panelSide == LayoutV3EditorPanelSide.RIGHT) {
                                    Alignment.CenterEnd
                                } else {
                                    Alignment.CenterStart
                                },
                            )
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
            title = { Text(stringResource(R.string.ligase_layout_v3_unsaved_title)) },
            text = { Text(stringResource(R.string.ligase_layout_v3_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    onKeepAndFinish()
                }) { Text(stringResource(R.string.ligase_layout_v3_keep_draft_leave)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showLeaveDialog = false
                        onDiscardAndFinish()
                    }) { Text(stringResource(R.string.ligase_layout_v3_discard)) }
                    TextButton(onClick = { showLeaveDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }

    if (showKeyboardPicker) {
        LayoutV3KeyboardPickerDialog(
            selectedCodes = selectedKeyboardCodes.toSet(),
            onToggle = { code ->
                val inputCode = InputCode(InputCodeNamespace.ANDROID_KEY_CODE, code)
                selectedKeyboardCodes = toggleLayoutV3KeyboardSelection(
                    selectedKeyboardCodes.mapTo(linkedSetOf()) {
                        InputCode(InputCodeNamespace.ANDROID_KEY_CODE, it)
                    },
                    inputCode,
                ).map(InputCode::code)
            },
            onDismiss = {
                selectedKeyboardCodes = emptyList()
                showKeyboardPicker = false
            },
            onConfirm = {
                val selected = selectedKeyboardCodes.mapTo(linkedSetOf()) {
                    InputCode(InputCodeNamespace.ANDROID_KEY_CODE, it)
                }
                if (selected.isNotEmpty()) {
                    onAddKeyboardKeys(selected)
                    selectedKeyboardCodes = emptyList()
                    showKeyboardPicker = false
                    toolsModeName = null
                }
            },
        )
    }
}

private enum class LayoutV3EditorToolsMode {
    CONTROL_PROPERTIES,
    LAYOUT_SETTINGS,
}

@Composable
private fun BlackTouchCanvas(
    state: LayoutV3EditorState,
    onSelect: (String) -> Unit,
    onBeginGesture: (String) -> LayoutV3GestureStartResult,
    onCommitPixelMove: (LayoutV3GestureCommitToken, IntRect, IntRect) -> LayoutV3EditResult,
    onCommitPixelResize: (LayoutV3GestureCommitToken, IntRect, IntRect) -> LayoutV3EditResult,
    onCancelGesture: (LayoutV3GestureCommitToken) -> LayoutV3EditResult,
    onOpenTools: () -> Unit,
    nudgePreview: LayoutV3NudgeDelta?,
    modifier: Modifier,
) {
    val draft = requireNotNull(state.draft)
    BoxWithConstraints(modifier.background(Color.Black)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        if (widthPx <= 0 || heightPx <= 0) return@BoxWithConstraints
        val fullOverlay = IntRect(0, 0, widthPx, heightPx)

        draft.elements.sortedBy(LayoutV3EditorElement::zOrder).forEach { element ->
            BlackTouchElement(
                element = element,
                canvas = draft.canvas,
                fullOverlay = fullOverlay,
                isSelected = state.selectedElementId == element.elementId,
                onSelect = onSelect,
                onBeginGesture = onBeginGesture,
                onCommitPixelMove = onCommitPixelMove,
                onCommitPixelResize = onCommitPixelResize,
                onCancelGesture = onCancelGesture,
                onOpenTools = onOpenTools,
                nudgePreview = nudgePreview.takeIf {
                    state.selectedElementId == element.elementId
                },
            )
        }

        Text(
            text = stringResource(R.string.ligase_layout_v3_runtime_unverified),
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
    element: LayoutV3EditorElement,
    canvas: IntSize,
    fullOverlay: IntRect,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
    onBeginGesture: (String) -> LayoutV3GestureStartResult,
    onCommitPixelMove: (LayoutV3GestureCommitToken, IntRect, IntRect) -> LayoutV3EditResult,
    onCommitPixelResize: (LayoutV3GestureCommitToken, IntRect, IntRect) -> LayoutV3EditResult,
    onCancelGesture: (LayoutV3GestureCommitToken) -> LayoutV3EditResult,
    onOpenTools: () -> Unit,
    nudgePreview: LayoutV3NudgeDelta?,
) {
    val density = LocalDensity.current
    val baseRect = remember(canvas, element, fullOverlay) {
        LayoutV3Geometry.mapEditorElement(canvas, element, fullOverlay)
    }
    var previewDx by remember(element.elementId, element.resolvedRect) { mutableFloatStateOf(0f) }
    var previewDy by remember(element.elementId, element.resolvedRect) { mutableFloatStateOf(0f) }
    var resizeDx by remember(element.elementId, element.resolvedRect) { mutableFloatStateOf(0f) }
    var resizeDy by remember(element.elementId, element.resolvedRect) { mutableFloatStateOf(0f) }
    var moveToken by remember(element.elementId) {
        mutableStateOf<LayoutV3GestureCommitToken?>(null)
    }
    var resizeToken by remember(element.elementId) {
        mutableStateOf<LayoutV3GestureCommitToken?>(null)
    }
    val movedBaseRect = IntRect(
        baseRect.x + previewDx.roundToInt() + (nudgePreview?.deltaX ?: 0),
        baseRect.y + previewDy.roundToInt() + (nudgePreview?.deltaY ?: 0),
        baseRect.width,
        baseRect.height,
    )
    val resizedRect = layoutV3ResizePreviewRect(
        element = element,
        basePixelRect = baseRect,
        deltaX = resizeDx.roundToInt(),
        deltaY = resizeDy.roundToInt(),
    )
    val previewRect = IntRect(
        movedBaseRect.x,
        movedBaseRect.y,
        resizedRect.width,
        resizedRect.height,
    )
    val label = when (val properties = element.editableProperties) {
        is LayoutV3EditableProperties.Keyboard ->
            properties.appearance.label.ifBlank {
                layoutV3KeyboardLabel(properties.inputCode).orEmpty()
            }.ifBlank {
                blackEditorKindLabel(element.kind)
            }
        is LayoutV3EditableProperties.Mouse ->
            properties.appearance.label.ifBlank { blackEditorKindLabel(element.kind) }
        else -> blackEditorKindLabel(element.kind)
    }
    val semanticsText = stringResource(
        R.string.ligase_layout_v3_element_geometry,
        element.resolvedRect.x,
        element.resolvedRect.y,
        element.resolvedRect.width,
        element.resolvedRect.height,
    )
    val resizeDescription = stringResource(R.string.ligase_layout_v3_resize_handle, label)
    val elementShape = element.editorShape().composeShape()
    val contentAlpha = layoutV3EditorContentAlpha(element.opacityPermille)
    val shortEdgeDp = with(density) {
        minOf(previewRect.width, previewRect.height).toDp().value
    }

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
                    onOpenTools()
                    true
                }
            }
            .clickable(
                onClickLabel = stringResource(R.string.ligase_layout_v3_open_tools),
            ) {
                onSelect(element.elementId)
                onOpenTools()
            }
            .background(
                (if (isSelected) Color(0xFF35315C) else Color(0xFF2A2E39))
                    .copy(alpha = (if (isSelected) 0.40f else 0.28f) * contentAlpha),
                elementShape,
            )
            .border(
                if (isSelected) 3.dp else LAYOUT_V3_EDITOR_UNSELECTED_BORDER_DP.dp,
                if (isSelected) {
                    Color(0xFFB8B1FF)
                } else {
                    Color(0xFFB8C0CE).copy(alpha = 0.72f * contentAlpha)
                },
                elementShape,
            )
            .pointerInput(element.elementId, element.resolvedRect, fullOverlay) {
                detectDragGestures(
                    onDragStart = {
                        onSelect(element.elementId)
                        previewDx = 0f
                        previewDy = 0f
                        moveToken = (onBeginGesture(element.elementId) as? 
                            LayoutV3GestureStartResult.Ready)?.token
                    },
                    onDragEnd = {
                        moveToken?.let { token ->
                            if (element.canMove()) {
                                onCommitPixelMove(
                                    token,
                                    fullOverlay,
                                    IntRect(
                                        baseRect.x + previewDx.roundToInt(),
                                        baseRect.y + previewDy.roundToInt(),
                                        baseRect.width,
                                        baseRect.height,
                                    ),
                                )
                            } else {
                                onCancelGesture(token)
                            }
                        }
                        moveToken = null
                        previewDx = 0f
                        previewDy = 0f
                    },
                    onDragCancel = {
                        moveToken?.let(onCancelGesture)
                        moveToken = null
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
            color = Color.White.copy(alpha = layoutV3EditorTextAlpha(element.opacityPermille)),
            fontWeight = FontWeight.Bold,
            fontSize = layoutV3EditorLabelSizeSp(shortEdgeDp).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isSelected && element.canResize()) {
            val maximumHitSizePx = with(density) { 20.dp.roundToPx() }
            val hitSizePx = layoutV3ResizeHandleHitSizePx(
                renderedWidthPx = previewRect.width,
                renderedHeightPx = previewRect.height,
                maximumHitSizePx = maximumHitSizePx,
            )
            val visualSizePx = minOf(hitSizePx, with(density) { 11.dp.roundToPx() })
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(with(density) { hitSizePx.toDp() })
                    .semantics { contentDescription = resizeDescription }
                    .pointerInput(element.elementId, element.resolvedRect, fullOverlay) {
                        detectDragGestures(
                            onDragStart = {
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeToken = (onBeginGesture(element.elementId) as?
                                    LayoutV3GestureStartResult.Ready)?.token
                            },
                            onDragEnd = {
                                resizeToken?.let { token ->
                                    onCommitPixelResize(
                                        token,
                                        fullOverlay,
                                        layoutV3ResizePreviewRect(
                                            element,
                                            baseRect,
                                            resizeDx.roundToInt(),
                                            resizeDy.roundToInt(),
                                        ),
                                    )
                                }
                                resizeToken = null
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                            onDragCancel = {
                                resizeToken?.let(onCancelGesture)
                                resizeToken = null
                                resizeDx = 0f
                                resizeDy = 0f
                            },
                        ) { change, amount ->
                            change.consume()
                            resizeDx += amount.x
                            resizeDy += amount.y
                        }
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(with(density) { visualSizePx.toDp() })
                        .background(
                            Color(0xFFB8B1FF),
                            RoundedCornerShape(topStart = 4.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun BlackEditorPanel(
    state: LayoutV3EditorState,
    presentation: LayoutV3EditorPresentation,
    mode: LayoutV3EditorToolsMode,
    onNudgePreview: (LayoutV3NudgeDelta) -> Unit,
    onNudgeCommit: (String, Int, Int) -> Unit,
    onSetZOrder: (String, Int) -> Unit,
    onSetOpacityPermille: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateProperties: (String, LayoutV3EditableProperties) -> Unit,
    onAdd: (ControlKind) -> Unit,
    onOpenKeyboardPicker: () -> Unit,
    onValidate: () -> Unit,
    onSaveAndFinish: () -> Unit,
    onRequestLeave: () -> Unit,
    onCloseTools: () -> Unit,
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
                stringResource(
                    if (mode == LayoutV3EditorToolsMode.CONTROL_PROPERTIES) {
                        R.string.ligase_layout_v3_control_properties
                    } else {
                        R.string.ligase_layout_v3_layout_settings
                    },
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestLeave) {
                Text(stringResource(R.string.ligase_layout_v3_keep_draft_leave))
            }
            TextButton(onClick = onCloseTools) {
                Text(stringResource(R.string.ligase_layout_v3_close_tools))
            }
        }
        if (presentation.showRecoveryWarning) {
            Text(
                stringResource(R.string.ligase_layout_v3_journal_warning),
                color = Color(0xFFF4B860),
            )
        }
        state.issue?.let {
            Text(blackEditorIssueText(it), color = Color(0xFFFF7B72))
        }
        if (mode == LayoutV3EditorToolsMode.CONTROL_PROPERTIES) selected?.let { element ->
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Text(blackEditorKindLabel(element.kind), color = Color.White)
            Text(
                stringResource(
                    R.string.ligase_layout_v3_element_geometry,
                    element.resolvedRect.x,
                    element.resolvedRect.y,
                    element.resolvedRect.width,
                    element.resolvedRect.height,
                ),
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                stringResource(
                    R.string.ligase_layout_v3_anchor_summary,
                    element.anchorX.name,
                    element.anchorY.name,
                ),
                color = Color.White.copy(alpha = 0.75f),
            )
            if (element.isInspectOnly()) {
                Text(
                    stringResource(R.string.ligase_layout_v3_inspect_only),
                    color = Color(0xFFF4B860),
                )
            } else {
                BlackPropertySummary(
                    element = element,
                    onUpdate = onUpdateProperties,
                    onSetOpacityPermille = onSetOpacityPermille,
                )
                EditorNudgePad(element, onNudgePreview, onNudgeCommit)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorTinyButton(stringResource(R.string.ligase_layout_v3_send_backward)) {
                        onSetZOrder(element.elementId, element.zOrder - 1)
                    }
                    EditorTinyButton(stringResource(R.string.ligase_layout_v3_bring_forward)) {
                        onSetZOrder(element.elementId, element.zOrder + 1)
                    }
                }
                OutlinedButton(
                    onClick = { onDelete(element.elementId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.ligase_layout_v3_delete)) }
            }
        }
        if (mode == LayoutV3EditorToolsMode.LAYOUT_SETTINGS) {
            Text(
                state.draft?.displayName.orEmpty(),
                color = Color.White.copy(alpha = 0.8f),
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Text(stringResource(R.string.ligase_layout_v3_add_control), color = Color.White)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorTinyButton(stringResource(R.string.ligase_layout_v3_add_keyboard_keys)) {
                onOpenKeyboardPicker()
            }
            listOf(
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
            ) { Text(stringResource(R.string.ligase_layout_v3_save)) }
            Text(
            stringResource(R.string.ligase_layout_v3_runtime_unverified),
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LayoutV3KeyboardPickerDialog(
    selectedCodes: Set<Int>,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            color = Color(0xE620232C),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.9f),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.ligase_layout_v3_keyboard_picker_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.ligase_layout_v3_keyboard_picker_hint),
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val keyUnitWidth = layoutV3KeyboardUnitWidthDp(maxWidth.value)
                    val keyboardWidth = layoutV3KeyboardRequiredWidthDp(keyUnitWidth)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.width(keyboardWidth.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    keyUnitWidth.dp *
                                        LAYOUT_V3_KEYBOARD_SECTION_GAP_UNITS,
                                ),
                            ) {
                                Box(
                                    Modifier.width(
                                        layoutV3KeyboardSectionWidthDp(
                                            LAYOUT_V3_ANSI_MAIN_ROWS,
                                            keyUnitWidth,
                                        ).dp,
                                    ),
                                ) {
                                    LayoutV3KeyboardRow(
                                        LAYOUT_V3_ANSI_FUNCTION_MAIN,
                                        keyUnitWidth,
                                        selectedCodes,
                                        onToggle,
                                    )
                                }
                                Box(
                                    Modifier.width(
                                        layoutV3KeyboardSectionWidthDp(
                                            LAYOUT_V3_ANSI_NAVIGATION_ROWS,
                                            keyUnitWidth,
                                        ).dp,
                                    ),
                                ) {
                                    LayoutV3KeyboardRow(
                                        LAYOUT_V3_ANSI_FUNCTION_NAVIGATION,
                                        keyUnitWidth,
                                        selectedCodes,
                                        onToggle,
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    keyUnitWidth.dp *
                                        LAYOUT_V3_KEYBOARD_SECTION_GAP_UNITS,
                                ),
                            ) {
                                LayoutV3KeyboardSection(
                                    LAYOUT_V3_ANSI_MAIN_ROWS,
                                    keyUnitWidth,
                                    selectedCodes,
                                    onToggle,
                                )
                                LayoutV3KeyboardSection(
                                    LAYOUT_V3_ANSI_NAVIGATION_ROWS,
                                    keyUnitWidth,
                                    selectedCodes,
                                    onToggle,
                                )
                                LayoutV3KeyboardNumpad(
                                    keyUnitWidth,
                                    selectedCodes,
                                    onToggle,
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onConfirm,
                        enabled = selectedCodes.isNotEmpty(),
                    ) {
                        Text(
                            stringResource(
                                R.string.ligase_layout_v3_keyboard_add_selected,
                                selectedCodes.size,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutV3KeyboardNumpad(
    keyUnitWidth: Float,
    selectedCodes: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val gap = LAYOUT_V3_KEYBOARD_KEY_GAP_DP
    Box(
        Modifier
            .width((keyUnitWidth * 4 + gap * 3).dp)
            .height((keyUnitWidth * 5 + gap * 4).dp),
    ) {
        LAYOUT_V3_ANSI_NUMPAD_GRID.forEach { placement ->
            val width = keyUnitWidth * placement.columnSpan +
                gap * (placement.columnSpan - 1)
            val height = keyUnitWidth * placement.rowSpan +
                gap * (placement.rowSpan - 1)
            LayoutV3KeyboardKeyCap(
                key = placement.key,
                selected = placement.key.code in selectedCodes,
                onToggle = onToggle,
                modifier = Modifier
                    .offset(
                        x = (placement.column * (keyUnitWidth + gap)).dp,
                        y = (placement.row * (keyUnitWidth + gap)).dp,
                    )
                    .width(width.dp)
                    .height(height.dp),
            )
        }
    }
}

@Composable
private fun LayoutV3KeyboardSection(
    rows: List<List<LayoutV3KeyboardKey>>,
    keyUnitWidth: Float,
    selectedCodes: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            if (row.isEmpty()) {
                Spacer(Modifier.height(keyUnitWidth.dp))
            } else {
                LayoutV3KeyboardRow(row, keyUnitWidth, selectedCodes, onToggle)
            }
        }
    }
}

@Composable
private fun LayoutV3KeyboardRow(
    row: List<LayoutV3KeyboardKey>,
    keyUnitWidth: Float,
    selectedCodes: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            LAYOUT_V3_KEYBOARD_KEY_GAP_DP.dp,
        ),
    ) {
        row.forEach { key ->
            val code = key.code
            if (code == null) {
                Spacer(Modifier.width(keyUnitWidth.dp * key.widthUnits))
            } else {
                LayoutV3KeyboardKeyCap(
                    key = key,
                    selected = code in selectedCodes,
                    onToggle = onToggle,
                    modifier = Modifier
                        .width(keyUnitWidth.dp * key.widthUnits)
                        .height(keyUnitWidth.dp),
                )
            }
        }
    }
}

@Composable
private fun LayoutV3KeyboardKeyCap(
    key: LayoutV3KeyboardKey,
    selected: Boolean,
    onToggle: (Int) -> Unit,
    modifier: Modifier,
) {
    val code = requireNotNull(key.code)
    val description = stringResource(
        R.string.ligase_layout_v3_keyboard_key_semantics,
        key.label,
        if (selected) {
            stringResource(R.string.ligase_layout_v3_key_selected)
        } else {
            stringResource(R.string.ligase_layout_v3_key_not_selected)
        },
    )
    Surface(
        color = if (selected) Color(0xFF6258D9) else Color(0xFF2A2E39),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFFB8B1FF)
            else Color.White.copy(alpha = 0.45f),
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .semantics {
                contentDescription = description
                this.selected = selected
            }
            .clickable { onToggle(code) },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                key.label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EditorNudgePad(
    element: LayoutV3EditorElement,
    onPreview: (LayoutV3NudgeDelta) -> Unit,
    onCommit: (String, Int, Int) -> Unit,
) {
    Text(
        stringResource(R.string.ligase_layout_v3_nudge_position),
        color = Color.White.copy(alpha = 0.8f),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EditorRepeatButton(
            label = "↑",
            description = stringResource(R.string.ligase_layout_v3_nudge_up),
            onPreview = { steps ->
                onPreview(layoutV3NudgeDelta(LayoutV3NudgeDirection.UP, steps))
            },
            onCommit = { steps ->
                val delta = layoutV3NudgeDelta(LayoutV3NudgeDirection.UP, steps)
                onCommit(element.elementId, delta.deltaX, delta.deltaY)
            },
            onCancelPreview = { onPreview(LayoutV3NudgeDelta(0, 0)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorRepeatButton(
                label = "←",
                description = stringResource(R.string.ligase_layout_v3_nudge_left),
                onPreview = { steps ->
                    onPreview(layoutV3NudgeDelta(LayoutV3NudgeDirection.LEFT, steps))
                },
                onCommit = { steps ->
                    val delta = layoutV3NudgeDelta(LayoutV3NudgeDirection.LEFT, steps)
                    onCommit(element.elementId, delta.deltaX, delta.deltaY)
                },
                onCancelPreview = { onPreview(LayoutV3NudgeDelta(0, 0)) },
            )
            EditorRepeatButton(
                label = "↓",
                description = stringResource(R.string.ligase_layout_v3_nudge_down),
                onPreview = { steps ->
                    onPreview(layoutV3NudgeDelta(LayoutV3NudgeDirection.DOWN, steps))
                },
                onCommit = { steps ->
                    val delta = layoutV3NudgeDelta(LayoutV3NudgeDirection.DOWN, steps)
                    onCommit(element.elementId, delta.deltaX, delta.deltaY)
                },
                onCancelPreview = { onPreview(LayoutV3NudgeDelta(0, 0)) },
            )
            EditorRepeatButton(
                label = "→",
                description = stringResource(R.string.ligase_layout_v3_nudge_right),
                onPreview = { steps ->
                    onPreview(layoutV3NudgeDelta(LayoutV3NudgeDirection.RIGHT, steps))
                },
                onCommit = { steps ->
                    val delta = layoutV3NudgeDelta(LayoutV3NudgeDirection.RIGHT, steps)
                    onCommit(element.elementId, delta.deltaX, delta.deltaY)
                },
                onCancelPreview = { onPreview(LayoutV3NudgeDelta(0, 0)) },
            )
        }
    }
}

@Composable
private fun EditorRepeatButton(
    label: String,
    description: String,
    onPreview: (Int) -> Unit,
    onCommit: (Int) -> Unit,
    onCancelPreview: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var wasPressed by remember { mutableStateOf(false) }
    var previewSteps by remember { mutableIntStateOf(0) }
    var suppressNextClick by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            wasPressed = true
            previewSteps = 0
            delay(LAYOUT_V3_NUDGE_REPEAT_DELAY_MS)
            while (true) {
                previewSteps += 1
                onPreview(previewSteps)
                delay(LAYOUT_V3_NUDGE_REPEAT_INTERVAL_MS)
            }
        } else if (wasPressed) {
            if (previewSteps > 0) {
                suppressNextClick = true
                onCommit(previewSteps)
            }
            wasPressed = false
            previewSteps = 0
        }
    }
    LaunchedEffect(suppressNextClick) {
        if (suppressNextClick) {
            delay(LAYOUT_V3_NUDGE_REPEAT_INTERVAL_MS * 3)
            suppressNextClick = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { onCancelPreview() }
    }
    OutlinedButton(
        onClick = {
            if (suppressNextClick) {
                suppressNextClick = false
            } else {
                onPreview(1)
                onCommit(1)
            }
        },
        interactionSource = interactionSource,
        modifier = Modifier
            .size(52.dp)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BlackPropertySummary(
    element: LayoutV3EditorElement,
    onUpdate: (String, LayoutV3EditableProperties) -> Unit,
    onSetOpacityPermille: (String, Int) -> Unit,
) {
    val opacityDescription = stringResource(R.string.ligase_layout_v3_opacity)
    var opacityDraft by rememberSaveable(element.elementId, element.opacityPermille) {
        mutableIntStateOf(element.opacityPermille)
    }
    Text(
        stringResource(
            R.string.ligase_layout_v3_opacity_value,
            (opacityDraft / 10f).roundToInt(),
        ),
        color = Color.White.copy(alpha = 0.8f),
    )
    Slider(
        value = opacityDraft.toFloat(),
        onValueChange = { opacityDraft = it.roundToInt() },
        onValueChangeFinished = {
            if (opacityDraft != element.opacityPermille) {
                onSetOpacityPermille(element.elementId, opacityDraft)
            }
        },
        valueRange = 0f..1000f,
        modifier = Modifier.semantics {
            contentDescription = opacityDescription
        },
    )
    when (val p = element.editableProperties) {
        is LayoutV3EditableProperties.Keyboard -> {
            Text(
                stringResource(
                    R.string.ligase_layout_v3_keyboard_code,
                    p.inputCode.namespace.name,
                    p.inputCode.code,
                ),
                color = Color.White.copy(alpha = 0.8f),
            )
            EditorTinyButton(p.trigger.name) {
                val next = Trigger.entries[(p.trigger.ordinal + 1) % Trigger.entries.size]
                onUpdate(element.elementId, p.copy(trigger = next))
            }
            BlackShapeSelector(element, p, onUpdate)
            BlackLabelEditor(
                elementId = element.elementId,
                label = p.appearance.label,
                onApply = { label ->
                    onUpdate(element.elementId, p.withEditorLabel(label))
                },
            )
            BlackDescriptionEditor(
                elementId = element.elementId,
                description = p.appearance.description,
                onApply = { description ->
                    onUpdate(
                        element.elementId,
                        p.withEditorDescription(description),
                    )
                },
            )
        }
        is LayoutV3EditableProperties.Mouse -> {
            Text(
                stringResource(R.string.ligase_layout_v3_mouse_button, p.button),
                color = Color.White.copy(alpha = 0.8f),
            )
            EditorTinyButton(p.trigger.name) {
                val next = Trigger.entries[(p.trigger.ordinal + 1) % Trigger.entries.size]
                onUpdate(element.elementId, p.copy(trigger = next))
            }
            BlackShapeSelector(element, p, onUpdate)
            BlackLabelEditor(
                elementId = element.elementId,
                label = p.appearance.label,
                onApply = { label ->
                    onUpdate(element.elementId, p.withEditorLabel(label))
                },
            )
            BlackDescriptionEditor(
                elementId = element.elementId,
                description = p.appearance.description,
                onApply = { description ->
                    onUpdate(
                        element.elementId,
                        p.withEditorDescription(description),
                    )
                },
            )
        }
        is LayoutV3EditableProperties.Analog ->
            Text(p.diagonalPolicy, color = Color.White.copy(alpha = 0.8f))
        is LayoutV3EditableProperties.Dpad ->
            Text(p.diagonalPolicy, color = Color.White.copy(alpha = 0.8f))
        LayoutV3EditableProperties.SoftKeyboard ->
            Text(
                stringResource(R.string.ligase_layout_v3_soft_keyboard_summary),
                color = Color.White.copy(alpha = 0.8f),
            )
        null -> Unit
    }
}

@Composable
private fun BlackLabelEditor(
    elementId: String,
    label: String,
    onApply: (String) -> Unit,
) {
    var draft by rememberSaveable(elementId, label) { mutableStateOf(label) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(stringResource(R.string.ligase_layout_v3_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onApply(draft) },
        enabled = draft != label,
    ) {
        Text(stringResource(R.string.ligase_layout_v3_apply_label))
    }
}

@Composable
private fun BlackDescriptionEditor(
    elementId: String,
    description: String,
    onApply: (String) -> Unit,
) {
    var draft by rememberSaveable(elementId, description) {
        mutableStateOf(description)
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(stringResource(R.string.ligase_layout_v3_description_label)) },
        supportingText = {
            Text(stringResource(R.string.ligase_layout_v3_description_help))
        },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onApply(draft) },
        enabled = draft != description,
    ) {
        Text(stringResource(R.string.ligase_layout_v3_apply_description))
    }
}

@Composable
private fun BlackShapeSelector(
    element: LayoutV3EditorElement,
    properties: LayoutV3EditableProperties,
    onUpdate: (String, LayoutV3EditableProperties) -> Unit,
) {
    Text(
        stringResource(R.string.ligase_layout_v3_shape),
        color = Color.White.copy(alpha = 0.8f),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        LayoutV3EditorShape.entries.forEachIndexed { index, shape ->
            SegmentedButton(
                selected = element.editorShape() == shape,
                onClick = {
                    onUpdate(element.elementId, properties.withEditorShape(shape))
                },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = LayoutV3EditorShape.entries.size,
                ),
                label = {
                    Text(
                        stringResource(
                            when (shape) {
                                LayoutV3EditorShape.CIRCLE ->
                                    R.string.ligase_layout_v3_shape_circle
                                LayoutV3EditorShape.ROUNDED_RECTANGLE ->
                                    R.string.ligase_layout_v3_shape_rounded_rectangle
                                LayoutV3EditorShape.RECTANGLE ->
                                    R.string.ligase_layout_v3_shape_rectangle
                            },
                        ),
                    )
                },
            )
        }
    }
}

private fun LayoutV3EditorShape.composeShape(): Shape = when (this) {
    LayoutV3EditorShape.CIRCLE -> CircleShape
    LayoutV3EditorShape.ROUNDED_RECTANGLE -> RoundedCornerShape(18)
    LayoutV3EditorShape.RECTANGLE -> RoundedCornerShape(0)
}

@Composable
private fun EditorTinyButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(10.dp, 6.dp)) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun BlackEditorUnavailable(
    handoff: LayoutV3EditorHandoffResult,
    onFinish: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (handoff == LayoutV3EditorHandoffResult.AwaitingViewport) {
            CircularProgressIndicator(color = Color.White)
            Text(
                stringResource(R.string.ligase_layout_v3_initializing_canvas),
                color = Color.White,
                modifier = Modifier.padding(12.dp),
            )
            return@Column
        }
        Text(
            stringResource(R.string.ligase_layout_v3_no_draft),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        if (handoff is LayoutV3EditorHandoffResult.Rejected) {
            Text(
                stringResource(R.string.ligase_layout_v3_handoff_failed),
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
        ControlKind.KEYBOARD -> R.string.ligase_layout_v3_kind_keyboard
        ControlKind.MOUSE -> R.string.ligase_layout_v3_kind_mouse
        ControlKind.ANALOG -> R.string.ligase_layout_v3_kind_analog
        ControlKind.DPAD -> R.string.ligase_layout_v3_kind_dpad
        ControlKind.CUSTOM_KEYS -> R.string.ligase_layout_v3_kind_custom_keys
        ControlKind.RADIAL -> R.string.ligase_layout_v3_kind_radial
        ControlKind.SCROLL -> R.string.ligase_layout_v3_kind_scroll
        ControlKind.COMBO -> R.string.ligase_layout_v3_kind_combo
        ControlKind.SOFT_KEYBOARD -> R.string.ligase_layout_v3_kind_soft_keyboard
        ControlKind.GYRO -> R.string.ligase_layout_v3_kind_gyro
    },
)

@Composable
private fun blackEditorIssueText(issue: LayoutV3EditorIssue): String = stringResource(
    when (issue) {
        LayoutV3EditorIssue.JOURNAL_WRITE_FAILED -> R.string.ligase_layout_v3_journal_warning
        LayoutV3EditorIssue.READ_ONLY_KIND -> R.string.ligase_layout_v3_inspect_only
        LayoutV3EditorIssue.OUT_OF_CANVAS,
        LayoutV3EditorIssue.INVALID_RECT -> R.string.ligase_layout_v3_issue_bounds
        else -> R.string.ligase_layout_v3_issue_generic
    },
)
