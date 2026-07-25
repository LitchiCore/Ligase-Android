package com.limelight.ligase.feature.input.layout.v3.application

import com.limelight.ligase.feature.input.layout.v3.domain.IntRect
import com.limelight.ligase.feature.input.layout.v3.domain.IntSize
import com.limelight.ligase.feature.input.layout.v3.domain.LayoutV3Geometry
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorElement
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Exception

enum class LayoutV3EditorMapIssue {
    INVALID_CANVAS,
    INVALID_OVERLAY,
    INVALID_ELEMENT,
    TARGET_BELOW_MINIMUM,
    TARGET_NOT_VISIBLE,
    OVERFLOW,
}

data class LayoutV3EditorPixelProjection(
    val elementId: String,
    val rect: IntRect,
    val clipRect: IntRect,
    val zOrder: Int,
    val drawVisible: Boolean,
    val hitTestEnabled: Boolean,
) {
    override fun toString(): String =
        "LayoutV3EditorPixelProjection(elementId=redacted,rect=redacted,clip=redacted," +
            "zOrder=$zOrder,drawVisible=$drawVisible,hitTestEnabled=$hitTestEnabled)"
}

sealed interface LayoutV3EditorMapResult {
    data class Mapped(val projection: LayoutV3EditorPixelProjection) : LayoutV3EditorMapResult
    data class Rejected(val issue: LayoutV3EditorMapIssue) : LayoutV3EditorMapResult
}

/**
 * UI-safe forward mapper. It consumes only the immutable editor projection and
 * delegates all anchor/scale/rounding authority to [LayoutV3Geometry].
 */
object LayoutV3EditorMapper {
    fun mapEditorElement(
        canvas: IntSize,
        element: LayoutV3EditorElement,
        fullOverlay: IntRect,
        minimumTargetPx: Int = 1,
    ): LayoutV3EditorMapResult {
        val rect = try {
            LayoutV3Geometry.mapAnchoredRect(
                canvas,
                element.rect,
                element.anchorX,
                element.anchorY,
                fullOverlay,
                minimumTargetPx,
            )
        } catch (failure: TouchLayoutV3Exception) {
            return LayoutV3EditorMapResult.Rejected(failure.code.toMapIssue())
        } catch (_: ArithmeticException) {
            return LayoutV3EditorMapResult.Rejected(LayoutV3EditorMapIssue.OVERFLOW)
        }
        val clip = intersection(rect, fullOverlay)
            ?: return LayoutV3EditorMapResult.Rejected(LayoutV3EditorMapIssue.TARGET_NOT_VISIBLE)
        return LayoutV3EditorMapResult.Mapped(
            LayoutV3EditorPixelProjection(
                elementId = element.elementId,
                rect = rect,
                clipRect = clip,
                zOrder = element.zOrder,
                drawVisible = !element.hidden,
                hitTestEnabled = element.enabled && !element.hidden,
            ),
        )
    }

    private fun intersection(left: IntRect, right: IntRect): IntRect? {
        val x = maxOf(left.x.toLong(), right.x.toLong())
        val y = maxOf(left.y.toLong(), right.y.toLong())
        val endX = minOf(left.right, right.right)
        val endY = minOf(left.bottom, right.bottom)
        if (endX <= x || endY <= y) return null
        return runCatching {
            IntRect(
                Math.toIntExact(x),
                Math.toIntExact(y),
                Math.toIntExact(endX - x),
                Math.toIntExact(endY - y),
            )
        }.getOrNull()
    }

    private fun String.toMapIssue(): LayoutV3EditorMapIssue =
        when (this) {
            "invalidCanvas" -> LayoutV3EditorMapIssue.INVALID_CANVAS
            "invalidOverlayBounds" -> LayoutV3EditorMapIssue.INVALID_OVERLAY
            "invalidElementRect" -> LayoutV3EditorMapIssue.INVALID_ELEMENT
            "targetBelowMinimum" -> LayoutV3EditorMapIssue.TARGET_BELOW_MINIMUM
            "targetNotVisible" -> LayoutV3EditorMapIssue.TARGET_NOT_VISIBLE
            else -> LayoutV3EditorMapIssue.OVERFLOW
        }
}
