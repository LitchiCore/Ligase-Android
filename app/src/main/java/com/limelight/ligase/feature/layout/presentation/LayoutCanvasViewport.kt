package com.limelight.ligase.feature.layout.presentation

import kotlin.math.max
import kotlin.math.min

data class LayoutCanvasSize(
    val width: Float,
    val height: Float,
)

data class LayoutCanvasRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class LayoutCanvasViewportState(
    val zoom: Float = MIN_ZOOM,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    fun transformed(
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
        frame: LayoutCanvasSize,
    ): LayoutCanvasViewportState {
        val nextZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val maxPanX = frame.width * (nextZoom - 1f) / 2f
        val maxPanY = frame.height * (nextZoom - 1f) / 2f
        return copy(
            zoom = nextZoom,
            panX = (panX + panChangeX).coerceIn(-maxPanX, maxPanX),
            panY = (panY + panChangeY).coerceIn(-maxPanY, maxPanY),
        )
    }

    fun reset(): LayoutCanvasViewportState = LayoutCanvasViewportState()

    fun screenX(normalizedX: Float, frameWidth: Float): Float =
        ((normalizedX - 0.5f) * zoom + 0.5f) * frameWidth + panX

    fun screenY(normalizedY: Float, frameHeight: Float): Float =
        ((normalizedY - 0.5f) * zoom + 0.5f) * frameHeight + panY

    fun normalizedDeltaX(screenDelta: Float, frameWidth: Float): Float =
        if (frameWidth <= 0f) 0f else screenDelta / (frameWidth * zoom)

    fun normalizedDeltaY(screenDelta: Float, frameHeight: Float): Float =
        if (frameHeight <= 0f) 0f else screenDelta / (frameHeight * zoom)

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
    }
}

fun fitLayoutCanvas(
    available: LayoutCanvasSize,
    referenceAspectRatio: Float,
): LayoutCanvasRect {
    if (available.width <= 0f || available.height <= 0f) {
        return LayoutCanvasRect(0f, 0f, 0f, 0f)
    }
    val aspect = referenceAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 16f / 9f
    val width = min(available.width, available.height * aspect)
    val height = min(available.height, available.width / aspect)
    return LayoutCanvasRect(
        left = max(0f, (available.width - width) / 2f),
        top = max(0f, (available.height - height) / 2f),
        width = width,
        height = height,
    )
}

fun showCanvasElementLabel(selected: Boolean): Boolean = selected

fun layoutHallColumns(availableWidthDp: Float): Int = when {
    availableWidthDp >= 1240f -> 3
    availableWidthDp >= 720f -> 2
    else -> 1
}
