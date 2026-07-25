package com.limelight.ligase.feature.input.layout.v2.domain

data class LayoutV2EditorPoint(val x: Int, val y: Int)

data class LayoutV2EditorViewport(
    val viewportSizePx: IntSize,
    val contentRectPx: IntRect,
)

enum class LayoutV2EditorGesturePhase { PREVIEW, POINTER_UP, POINTER_CANCEL }

object LayoutV2BlackEditorGestureContract {
    fun shouldCommit(phase: LayoutV2EditorGesturePhase): Boolean =
        phase != LayoutV2EditorGesturePhase.PREVIEW
}

object LayoutV2BlackEditorViewportMapper {
    val CANONICAL_CANVAS = IntSize(1920, 1080)

    fun viewport(widthPx: Int, heightPx: Int): LayoutV2EditorViewport? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val widthLimited =
            widthPx.toLong() * CANONICAL_CANVAS.height <=
                heightPx.toLong() * CANONICAL_CANVAS.width
        val contentWidth: Int
        val contentHeight: Int
        if (widthLimited) {
            contentWidth = widthPx
            contentHeight = roundHalfUp(
                widthPx.toLong() * CANONICAL_CANVAS.height,
                CANONICAL_CANVAS.width,
            ).coerceAtLeast(1)
        } else {
            contentHeight = heightPx
            contentWidth = roundHalfUp(
                heightPx.toLong() * CANONICAL_CANVAS.width,
                CANONICAL_CANVAS.height,
            ).coerceAtLeast(1)
        }
        return LayoutV2EditorViewport(
            IntSize(widthPx, heightPx),
            IntRect(
                (widthPx - contentWidth) / 2,
                (heightPx - contentHeight) / 2,
                contentWidth,
                contentHeight,
            ),
        )
    }

    fun pointerToCanvas(
        viewport: LayoutV2EditorViewport,
        pointPx: LayoutV2EditorPoint,
    ): LayoutV2EditorPoint {
        val rect = viewport.contentRectPx
        val clampedX = pointPx.x.coerceIn(rect.x, rect.right)
        val clampedY = pointPx.y.coerceIn(rect.y, rect.bottom)
        return LayoutV2EditorPoint(
            roundHalfUp(
                (clampedX - rect.x).toLong() * CANONICAL_CANVAS.width,
                rect.width,
            ).coerceIn(0, CANONICAL_CANVAS.width),
            roundHalfUp(
                (clampedY - rect.y).toLong() * CANONICAL_CANVAS.height,
                rect.height,
            ).coerceIn(0, CANONICAL_CANVAS.height),
        )
    }

    fun canvasToPointer(
        viewport: LayoutV2EditorViewport,
        point: LayoutV2EditorPoint,
    ): LayoutV2EditorPoint {
        val rect = viewport.contentRectPx
        val x = point.x.coerceIn(0, CANONICAL_CANVAS.width)
        val y = point.y.coerceIn(0, CANONICAL_CANVAS.height)
        return LayoutV2EditorPoint(
            rect.x + roundHalfUp(x.toLong() * rect.width, CANONICAL_CANVAS.width),
            rect.y + roundHalfUp(y.toLong() * rect.height, CANONICAL_CANVAS.height),
        )
    }

    fun canvasRectToPixels(
        viewport: LayoutV2EditorViewport,
        rect: IntRect,
    ): IntRect {
        val topLeft = canvasToPointer(viewport, LayoutV2EditorPoint(rect.x, rect.y))
        val bottomRight = canvasToPointer(
            viewport,
            LayoutV2EditorPoint(rect.right, rect.bottom),
        )
        return IntRect(
            topLeft.x,
            topLeft.y,
            (bottomRight.x - topLeft.x).coerceAtLeast(1),
            (bottomRight.y - topLeft.y).coerceAtLeast(1),
        )
    }

    fun pixelsRectToCanvas(
        viewport: LayoutV2EditorViewport,
        rectPx: IntRect,
    ): IntRect {
        val topLeft = pointerToCanvas(
            viewport,
            LayoutV2EditorPoint(rectPx.x, rectPx.y),
        )
        val bottomRight = pointerToCanvas(
            viewport,
            LayoutV2EditorPoint(rectPx.right, rectPx.bottom),
        )
        val x = topLeft.x.coerceAtMost(CANONICAL_CANVAS.width - 1)
        val y = topLeft.y.coerceAtMost(CANONICAL_CANVAS.height - 1)
        return IntRect(
            x,
            y,
            (bottomRight.x - x).coerceAtLeast(1)
                .coerceAtMost(CANONICAL_CANVAS.width - x),
            (bottomRight.y - y).coerceAtLeast(1)
                .coerceAtMost(CANONICAL_CANVAS.height - y),
        )
    }

    private fun roundHalfUp(numerator: Long, denominator: Int): Int {
        require(numerator >= 0 && denominator > 0)
        return ((numerator + denominator / 2L) / denominator).toInt()
    }
}
