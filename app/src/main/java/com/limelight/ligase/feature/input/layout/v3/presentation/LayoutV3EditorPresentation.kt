package com.limelight.ligase.feature.input.layout.v3.presentation

import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditableProperties
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorElement
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorHandoffResult
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorIssue
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorState
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3ElementCapability
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3RecoveryProtection
import com.limelight.ligase.feature.input.layout.v3.domain.IntRect
import com.limelight.ligase.feature.input.layout.v3.domain.InputCode
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3ResizeDecision
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3ResizePolicy

data class LayoutV3EditorPresentation(
    val hasDraft: Boolean,
    val hasElements: Boolean,
    val canSave: Boolean,
    val showUnsavedWarning: Boolean,
    val showRecoveryWarning: Boolean,
    val issue: LayoutV3EditorIssue?,
)

const val LAYOUT_V3_EDITOR_LABEL_MIN_SP = 13f
const val LAYOUT_V3_EDITOR_LABEL_MAX_SP = 34f
const val LAYOUT_V3_EDITOR_UNSELECTED_BORDER_DP = 2f
const val LAYOUT_V3_EDITOR_TEXT_BASE_ALPHA = 0.82f

fun layoutV3EditorLabelSizeSp(shortEdgeDp: Float): Float =
    (shortEdgeDp * 0.34f).coerceIn(
        LAYOUT_V3_EDITOR_LABEL_MIN_SP,
        LAYOUT_V3_EDITOR_LABEL_MAX_SP,
    )

fun layoutV3EditorContentAlpha(opacityPermille: Int): Float =
    opacityPermille.coerceIn(0, 1000) / 1000f

fun layoutV3EditorTextAlpha(opacityPermille: Int): Float =
    layoutV3EditorContentAlpha(opacityPermille) * LAYOUT_V3_EDITOR_TEXT_BASE_ALPHA

fun layoutV3ChordLabels(
    keys: List<InputCode>,
    unknownLabel: String,
): List<String> = keys.map { layoutV3KeyboardLabel(it) ?: unknownLabel }

fun presentLayoutV3Editor(state: LayoutV3EditorState): LayoutV3EditorPresentation {
    val hasDraft = state.draft != null
    val hasElements = state.draft?.elements?.isNotEmpty() == true
    return LayoutV3EditorPresentation(
        hasDraft = hasDraft,
        hasElements = hasElements,
        canSave = hasDraft && hasElements && state.candidateReady && !state.saving,
        showUnsavedWarning = state.dirty,
        showRecoveryWarning =
            state.recoveryProtection == LayoutV3RecoveryProtection.NOT_SAVED,
        issue = state.issue,
    )
}

fun LayoutV3EditorElement.canMove(): Boolean =
    LayoutV3ElementCapability.MOVE in capabilities

fun LayoutV3EditorElement.canResize(): Boolean =
    LayoutV3ElementCapability.RESIZE in capabilities

fun LayoutV3EditorElement.canDelete(): Boolean =
    LayoutV3ElementCapability.DELETE in capabilities

fun LayoutV3EditorElement.isInspectOnly(): Boolean =
    LayoutV3ElementCapability.INSPECT_ONLY in capabilities

enum class LayoutV3EditorShape(val protocolValue: String) {
    CIRCLE("circle"),
    ROUNDED_RECTANGLE("roundedrectangle"),
    RECTANGLE("rectangle"),
}

fun LayoutV3EditorElement.editorShape(): LayoutV3EditorShape = when (
    val properties = editableProperties
) {
    is LayoutV3EditableProperties.Keyboard ->
        requireNotNull(layoutV3EditorShape(properties.appearance.shape))
    is LayoutV3EditableProperties.Mouse ->
        requireNotNull(layoutV3EditorShape(properties.appearance.shape))
    else -> LayoutV3EditorShape.CIRCLE
}

fun LayoutV3EditableProperties.withEditorShape(
    shape: LayoutV3EditorShape,
): LayoutV3EditableProperties = when (this) {
    is LayoutV3EditableProperties.Keyboard ->
        copy(appearance = appearance.copy(shape = shape.protocolValue))
    is LayoutV3EditableProperties.Mouse ->
        copy(appearance = appearance.copy(shape = shape.protocolValue))
    else -> this
}

fun LayoutV3EditableProperties.withEditorDescription(
    description: String,
): LayoutV3EditableProperties = when (this) {
    is LayoutV3EditableProperties.Keyboard ->
        copy(appearance = appearance.copy(description = description))
    is LayoutV3EditableProperties.Mouse ->
        copy(appearance = appearance.copy(description = description))
    else -> this
}

fun LayoutV3EditableProperties.withEditorLabel(
    label: String,
): LayoutV3EditableProperties = when (this) {
    is LayoutV3EditableProperties.Keyboard ->
        copy(appearance = appearance.copy(label = label))
    is LayoutV3EditableProperties.Mouse ->
        copy(appearance = appearance.copy(label = label))
    else -> this
}

fun layoutV3EditorShape(protocolValue: String): LayoutV3EditorShape? =
    LayoutV3EditorShape.entries.firstOrNull { it.protocolValue == protocolValue }

enum class LayoutV3BlackEditorBackAction {
    ABORT,
    CLOSE_TOOLS,
    CONFIRM_LEAVE,
    FINISH,
}

enum class LayoutV3EditorPanelSide {
    LEFT,
    RIGHT,
}

fun layoutV3EditorPanelSide(
    selectedCenterPx: Int?,
    viewportWidthPx: Int,
): LayoutV3EditorPanelSide {
    require(viewportWidthPx > 0)
    if (selectedCenterPx == null) return LayoutV3EditorPanelSide.RIGHT
    return if (selectedCenterPx.toLong() * 2L < viewportWidthPx.toLong()) {
        LayoutV3EditorPanelSide.RIGHT
    } else {
        LayoutV3EditorPanelSide.LEFT
    }
}

fun layoutV3ResizeHandleHitSizePx(
    renderedWidthPx: Int,
    renderedHeightPx: Int,
    maximumHitSizePx: Int,
): Int {
    require(renderedWidthPx > 0 && renderedHeightPx > 0 && maximumHitSizePx > 0)
    return minOf(
        maximumHitSizePx,
        minOf(renderedWidthPx, renderedHeightPx) / 5,
    ).coerceAtLeast(1)
}

fun layoutV3ResizePreviewRect(
    element: LayoutV3EditorElement,
    basePixelRect: IntRect,
    deltaX: Int,
    deltaY: Int,
): IntRect {
    val requested = basePixelRect.copy(
        width = (basePixelRect.width + deltaX).coerceAtLeast(1),
        height = (basePixelRect.height + deltaY).coerceAtLeast(1),
    )
    return when (
        val decision = LayoutV3ResizePolicy.constrain(
            element.copy(resolvedRect = basePixelRect),
            requested,
        )
    ) {
        is LayoutV3ResizeDecision.Ready -> decision.rect
        is LayoutV3ResizeDecision.Rejected -> basePixelRect
    }
}

enum class LayoutV3NudgeDirection(val deltaX: Int, val deltaY: Int) {
    UP(0, -1),
    LEFT(-1, 0),
    DOWN(0, 1),
    RIGHT(1, 0),
}

data class LayoutV3NudgeDelta(val deltaX: Int, val deltaY: Int)

fun layoutV3NudgeDelta(
    direction: LayoutV3NudgeDirection,
    steps: Int = 1,
): LayoutV3NudgeDelta {
    require(steps >= 0)
    return LayoutV3NudgeDelta(direction.deltaX * steps, direction.deltaY * steps)
}

const val LAYOUT_V3_NUDGE_REPEAT_DELAY_MS = 350L
const val LAYOUT_V3_NUDGE_REPEAT_INTERVAL_MS = 60L

val LAYOUT_V3_NUDGE_PAD_ROWS = listOf(
    listOf(LayoutV3NudgeDirection.UP),
    listOf(
        LayoutV3NudgeDirection.LEFT,
        LayoutV3NudgeDirection.DOWN,
        LayoutV3NudgeDirection.RIGHT,
    ),
)

fun layoutV3BlackEditorBackAction(
    handoff: LayoutV3EditorHandoffResult,
    toolsOpen: Boolean,
    dirty: Boolean,
): LayoutV3BlackEditorBackAction = when {
    handoff !is LayoutV3EditorHandoffResult.LaunchReady ->
        LayoutV3BlackEditorBackAction.ABORT
    toolsOpen -> LayoutV3BlackEditorBackAction.CLOSE_TOOLS
    dirty -> LayoutV3BlackEditorBackAction.CONFIRM_LEAVE
    else -> LayoutV3BlackEditorBackAction.FINISH
}
