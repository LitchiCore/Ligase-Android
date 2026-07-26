package com.limelight.ligase.feature.input.layout.v3.application

import com.limelight.ligase.feature.input.layout.v3.domain.Appearance
import com.limelight.ligase.feature.input.layout.v3.domain.ControlKind
import com.limelight.ligase.feature.input.layout.v3.domain.InputCode
import com.limelight.ligase.feature.input.layout.v3.domain.InputCodeNamespace
import com.limelight.ligase.feature.input.layout.v3.domain.IntRect
import com.limelight.ligase.feature.input.layout.v3.domain.Trigger
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditableProperties
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorDraft

sealed interface LayoutV3ElementCreationDecision {
    data class Ready(
        val rect: IntRect,
        val properties: LayoutV3EditableProperties,
    ) : LayoutV3ElementCreationDecision

    data object UnsupportedKind : LayoutV3ElementCreationDecision
    data object NoSafePlacement : LayoutV3ElementCreationDecision
}

sealed interface LayoutV3ElementPlacementDecision {
    data class Ready(val rect: IntRect) : LayoutV3ElementPlacementDecision
    data object UnsupportedKind : LayoutV3ElementPlacementDecision
    data object NoSafePlacement : LayoutV3ElementPlacementDecision
}

/**
 * Owns deterministic initial geometry and payload defaults for editor-created controls.
 */
class LayoutV3ElementCreationPolicy {
    fun create(
        draft: LayoutV3EditorDraft,
        kind: ControlKind,
    ): LayoutV3ElementCreationDecision {
        if (kind == ControlKind.COMBO || kind == ControlKind.RADIAL) {
            return LayoutV3ElementCreationDecision.UnsupportedKind
        }
        val rect = when (val placement = place(draft, kind)) {
            is LayoutV3ElementPlacementDecision.Ready -> placement.rect
            LayoutV3ElementPlacementDecision.UnsupportedKind ->
                return LayoutV3ElementCreationDecision.UnsupportedKind
            LayoutV3ElementPlacementDecision.NoSafePlacement ->
                return LayoutV3ElementCreationDecision.NoSafePlacement
        }
        return LayoutV3ElementCreationDecision.Ready(rect, properties(kind))
    }

    fun place(
        draft: LayoutV3EditorDraft,
        kind: ControlKind,
    ): LayoutV3ElementPlacementDecision {
        val dimensions = dimensions(kind)
            ?: return LayoutV3ElementPlacementDecision.UnsupportedKind
        val rect = initialRect(draft, dimensions.first, dimensions.second)
            ?: return LayoutV3ElementPlacementDecision.NoSafePlacement
        return LayoutV3ElementPlacementDecision.Ready(rect)
    }

    private fun dimensions(kind: ControlKind): Pair<Int, Int>? = when (kind) {
        ControlKind.KEYBOARD, ControlKind.MOUSE -> 160 to 160
        ControlKind.ANALOG, ControlKind.DPAD -> 240 to 240
        ControlKind.SOFT_KEYBOARD -> 240 to 120
        ControlKind.COMBO -> 180 to 180
        ControlKind.RADIAL -> 320 to 320
        else -> null
    }

    private fun properties(kind: ControlKind): LayoutV3EditableProperties = when (kind) {
        ControlKind.KEYBOARD -> LayoutV3EditableProperties.Keyboard(
            inputCode = androidKeyCode(62),
            appearance = appearance("Key"),
            trigger = Trigger.HOLD,
            timedHoldMs = null,
        )
        ControlKind.MOUSE -> LayoutV3EditableProperties.Mouse(
            button = "primary",
            appearance = appearance("Mouse"),
            trigger = Trigger.HOLD,
            timedHoldMs = null,
        )
        ControlKind.ANALOG -> LayoutV3EditableProperties.Analog(
            up = listOf(androidKeyCode(19)),
            down = listOf(androidKeyCode(20)),
            left = listOf(androidKeyCode(21)),
            right = listOf(androidKeyCode(22)),
            press = listOf(androidKeyCode(23)),
            diagonalPolicy = "vector",
        )
        ControlKind.DPAD -> LayoutV3EditableProperties.Dpad(
            up = listOf(androidKeyCode(19)),
            down = listOf(androidKeyCode(20)),
            left = listOf(androidKeyCode(21)),
            right = listOf(androidKeyCode(22)),
            press = null,
            diagonalPolicy = "allowTwoDirections",
        )
        ControlKind.SOFT_KEYBOARD -> LayoutV3EditableProperties.SoftKeyboard
        else -> error("Unsupported kind must be rejected before creating defaults")
    }

    private fun initialRect(
        draft: LayoutV3EditorDraft,
        width: Int,
        height: Int,
    ): IntRect? {
        if (width > draft.canvas.width || height > draft.canvas.height) return null
        return IntRect(
            (draft.canvas.width - width) / 2,
            (draft.canvas.height - height) / 2,
            width,
            height,
        )
    }

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
