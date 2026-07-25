package com.limelight.ligase.feature.input.layout.v2.presentation

import com.limelight.ligase.feature.input.layout.v2.domain.IntRect
import com.limelight.ligase.feature.input.layout.v2.domain.IntSize
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorElement
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditableProperties
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorIssue
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorState
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2ElementCapability
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2RecoveryProtection
import kotlin.math.roundToInt

data class LayoutV2EditorPresentation(
    val hasDraft: Boolean,
    val hasElements: Boolean,
    val canSave: Boolean,
    val showUnsavedWarning: Boolean,
    val showRecoveryWarning: Boolean,
    val issue: LayoutV2EditorIssue?,
)

fun presentLayoutV2Editor(state: LayoutV2EditorState): LayoutV2EditorPresentation {
    val hasDraft = state.draft != null
    val hasElements = state.draft?.elements?.isNotEmpty() == true
    return LayoutV2EditorPresentation(
        hasDraft = hasDraft,
        hasElements = hasElements,
        canSave = hasDraft && hasElements && state.candidateReady && !state.saving,
        showUnsavedWarning = state.dirty,
        showRecoveryWarning =
            state.recoveryProtection == LayoutV2RecoveryProtection.NOT_SAVED,
        issue = state.issue,
    )
}

fun LayoutV2EditorElement.canMove(): Boolean =
    LayoutV2ElementCapability.MOVE in capabilities

fun LayoutV2EditorElement.canResize(): Boolean =
    LayoutV2ElementCapability.RESIZE in capabilities

fun LayoutV2EditorElement.canDelete(): Boolean =
    LayoutV2ElementCapability.DELETE in capabilities

fun LayoutV2EditorElement.isInspectOnly(): Boolean =
    LayoutV2ElementCapability.INSPECT_ONLY in capabilities

enum class LayoutV2EditorShape(val protocolValue: String) {
    CIRCLE("circle"),
    ROUNDED_RECTANGLE("roundedrectangle"),
    RECTANGLE("rectangle"),
}

fun LayoutV2EditorElement.editorShape(): LayoutV2EditorShape = when (
    val properties = editableProperties
) {
    is LayoutV2EditableProperties.Keyboard ->
        requireNotNull(layoutV2EditorShape(properties.appearance.shape))
    is LayoutV2EditableProperties.Mouse ->
        requireNotNull(layoutV2EditorShape(properties.appearance.shape))
    else -> LayoutV2EditorShape.CIRCLE
}

fun LayoutV2EditableProperties.withEditorShape(
    shape: LayoutV2EditorShape,
): LayoutV2EditableProperties = when (this) {
    is LayoutV2EditableProperties.Keyboard ->
        copy(appearance = appearance.copy(shape = shape.protocolValue))
    is LayoutV2EditableProperties.Mouse ->
        copy(appearance = appearance.copy(shape = shape.protocolValue))
    else -> this
}

fun layoutV2EditorShape(protocolValue: String): LayoutV2EditorShape? =
    LayoutV2EditorShape.entries.firstOrNull { it.protocolValue == protocolValue }

data class LayoutV2CanvasTransform(
    val canvas: IntSize,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    init {
        require(canvas.width > 0 && canvas.height > 0)
        require(viewportWidthPx > 0f && viewportHeightPx > 0f)
        require(zoom in MIN_ZOOM..MAX_ZOOM)
    }

    private val fitScale: Float
        get() = minOf(
            viewportWidthPx / canvas.width.toFloat(),
            viewportHeightPx / canvas.height.toFloat(),
        )

    fun screenRect(rect: IntRect): LayoutV2ScreenRect {
        val scale = fitScale * zoom
        val contentWidth = canvas.width * scale
        val contentHeight = canvas.height * scale
        val originX = (viewportWidthPx - contentWidth) / 2f + panX
        val originY = (viewportHeightPx - contentHeight) / 2f + panY
        return LayoutV2ScreenRect(
            left = originX + rect.x * scale,
            top = originY + rect.y * scale,
            width = rect.width * scale,
            height = rect.height * scale,
        )
    }

    fun canvasDelta(screenDeltaX: Float, screenDeltaY: Float): Pair<Int, Int> {
        val scale = fitScale * zoom
        return (screenDeltaX / scale).roundToInt() to
            (screenDeltaY / scale).roundToInt()
    }

    fun transformed(
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
    ): LayoutV2CanvasTransform {
        val nextZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val maxPanX = viewportWidthPx * (nextZoom - 1f) / 2f
        val maxPanY = viewportHeightPx * (nextZoom - 1f) / 2f
        return copy(
            zoom = nextZoom,
            panX = (panX + panChangeX).coerceIn(-maxPanX, maxPanX),
            panY = (panY + panChangeY).coerceIn(-maxPanY, maxPanY),
        )
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
    }
}

data class LayoutV2ScreenRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

fun layoutV2EditorCanvasWeight(wide: Boolean): Float = if (wide) 0.66f else 1f

enum class LayoutV2BlackEditorBackAction {
    ABORT,
    CLOSE_TOOLS,
    CONFIRM_LEAVE,
    FINISH,
}

fun layoutV2BlackEditorBackAction(
    handoffReady: Boolean,
    toolsOpen: Boolean,
    dirty: Boolean,
): LayoutV2BlackEditorBackAction = when {
    !handoffReady -> LayoutV2BlackEditorBackAction.ABORT
    toolsOpen -> LayoutV2BlackEditorBackAction.CLOSE_TOOLS
    dirty -> LayoutV2BlackEditorBackAction.CONFIRM_LEAVE
    else -> LayoutV2BlackEditorBackAction.FINISH
}

fun layoutV2BlackEditorCanvasSize(
    availableWidthPx: Int,
    availableHeightPx: Int,
    @Suppress("UNUSED_PARAMETER") toolsOpen: Boolean,
): IntSize {
    require(availableWidthPx > 0 && availableHeightPx > 0)
    return IntSize(availableWidthPx, availableHeightPx)
}
