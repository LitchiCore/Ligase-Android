package com.limelight.ligase.feature.input.layout.v2.application

import com.limelight.ligase.feature.input.layout.v2.domain.Appearance
import com.limelight.ligase.feature.input.layout.v2.domain.ControlKind
import com.limelight.ligase.feature.input.layout.v2.domain.InputCode
import com.limelight.ligase.feature.input.layout.v2.domain.InputCodeNamespace
import com.limelight.ligase.feature.input.layout.v2.domain.IntRect
import com.limelight.ligase.feature.input.layout.v2.domain.Trigger
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditableProperties
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorDraft

sealed interface LayoutV2ElementCreationDecision {
    data class Ready(
        val rect: IntRect,
        val properties: LayoutV2EditableProperties,
    ) : LayoutV2ElementCreationDecision

    data object UnsupportedKind : LayoutV2ElementCreationDecision
    data object NoSafePlacement : LayoutV2ElementCreationDecision
}

/**
 * Owns deterministic initial geometry and payload defaults for editor-created controls.
 */
class LayoutV2ElementCreationPolicy {
    fun create(
        draft: LayoutV2EditorDraft,
        kind: ControlKind,
    ): LayoutV2ElementCreationDecision {
        val dimensions = dimensions(kind)
            ?: return LayoutV2ElementCreationDecision.UnsupportedKind
        val rect = firstNonOverlappingRect(draft, dimensions.first, dimensions.second)
            ?: return LayoutV2ElementCreationDecision.NoSafePlacement
        return LayoutV2ElementCreationDecision.Ready(rect, properties(kind))
    }

    private fun dimensions(kind: ControlKind): Pair<Int, Int>? = when (kind) {
        ControlKind.KEYBOARD, ControlKind.MOUSE -> 160 to 160
        ControlKind.ANALOG, ControlKind.DPAD -> 240 to 240
        ControlKind.SOFT_KEYBOARD -> 240 to 120
        else -> null
    }

    private fun properties(kind: ControlKind): LayoutV2EditableProperties = when (kind) {
        ControlKind.KEYBOARD -> LayoutV2EditableProperties.Keyboard(
            inputCode = androidKeyCode(62),
            appearance = appearance("Key"),
            trigger = Trigger.HOLD,
            timedHoldMs = null,
        )
        ControlKind.MOUSE -> LayoutV2EditableProperties.Mouse(
            button = "primary",
            appearance = appearance("Mouse"),
            trigger = Trigger.HOLD,
            timedHoldMs = null,
        )
        ControlKind.ANALOG -> LayoutV2EditableProperties.Analog(
            up = listOf(androidKeyCode(19)),
            down = listOf(androidKeyCode(20)),
            left = listOf(androidKeyCode(21)),
            right = listOf(androidKeyCode(22)),
            press = listOf(androidKeyCode(23)),
            diagonalPolicy = "vector",
        )
        ControlKind.DPAD -> LayoutV2EditableProperties.Dpad(
            up = listOf(androidKeyCode(19)),
            down = listOf(androidKeyCode(20)),
            left = listOf(androidKeyCode(21)),
            right = listOf(androidKeyCode(22)),
            press = null,
            diagonalPolicy = "allowTwoDirections",
        )
        ControlKind.SOFT_KEYBOARD -> LayoutV2EditableProperties.SoftKeyboard
        else -> error("Unsupported kind must be rejected before creating defaults")
    }

    private fun firstNonOverlappingRect(
        draft: LayoutV2EditorDraft,
        width: Int,
        height: Int,
    ): IntRect? {
        if (width > draft.canvas.width || height > draft.canvas.height) return null
        for (y in 0..(draft.canvas.height - height) step GRID_STEP) {
            for (x in 0..(draft.canvas.width - width) step GRID_STEP) {
                val candidate = IntRect(x, y, width, height)
                if (draft.elements.none { intersects(candidate, it.rect) }) return candidate
            }
        }
        return null
    }

    private fun intersects(left: IntRect, right: IntRect): Boolean =
        maxOf(left.x, right.x) < minOf(left.right, right.right) &&
            maxOf(left.y, right.y) < minOf(left.bottom, right.bottom)

    private fun androidKeyCode(code: Int) =
        InputCode(InputCodeNamespace.ANDROID_KEY_CODE, code)

    private fun appearance(label: String) = Appearance(
        label = label,
        description = "",
        shape = "circle",
        showPhysicalKeyNames = false,
    )

    private companion object {
        const val GRID_STEP = 24
    }
}
