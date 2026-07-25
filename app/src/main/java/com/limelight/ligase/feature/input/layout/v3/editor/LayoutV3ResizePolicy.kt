package com.limelight.ligase.feature.input.layout.v3.editor

import com.limelight.ligase.feature.input.layout.v3.domain.IntRect

sealed interface LayoutV3ResizeDecision {
    data class Ready(val rect: IntRect) : LayoutV3ResizeDecision
    data class Rejected(val issue: LayoutV3EditorIssue) : LayoutV3ResizeDecision
}

/**
 * Single resize authority shared by editor preview and every persisted resize path.
 */
object LayoutV3ResizePolicy {
    fun constrain(
        element: LayoutV3EditorElement,
        targetRect: IntRect,
    ): LayoutV3ResizeDecision {
        val circle = when (val properties = element.editableProperties) {
            is LayoutV3EditableProperties.Keyboard -> properties.appearance.shape == "circle"
            is LayoutV3EditableProperties.Mouse -> properties.appearance.shape == "circle"
            else -> false
        }
        if (!circle) return LayoutV3ResizeDecision.Ready(targetRect)

        val widthDelta = targetRect.width.toLong() - element.resolvedRect.width.toLong()
        val heightDelta = targetRect.height.toLong() - element.resolvedRect.height.toLong()
        val requestedSize = if (absolute(widthDelta) >= absolute(heightDelta)) {
            targetRect.width.toLong()
        } else {
            targetRect.height.toLong()
        }
        val size = requestedSize.coerceAtLeast(1L)
        if (size > Int.MAX_VALUE) {
            return LayoutV3ResizeDecision.Rejected(LayoutV3EditorIssue.INVALID_RECT)
        }
        return LayoutV3ResizeDecision.Ready(
            targetRect.copy(width = size.toInt(), height = size.toInt()),
        )
    }

    private fun absolute(value: Long): Long =
        if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value)
}
