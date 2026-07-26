package com.limelight.ligase.feature.input.layout.v3.editor

import com.limelight.ligase.feature.input.layout.v3.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutV3ResizePolicyTest {
    @Test
    fun `circle uses dominant width delta and stays square`() {
        val result = LayoutV3ResizePolicy.constrain(
            element(shape = "circle"),
            IntRect(10, 20, 150, 120),
        ) as LayoutV3ResizeDecision.Ready

        assertEquals(IntRect(10, 20, 150, 150), result.rect)
    }

    @Test
    fun `circle tie chooses width and minimum size is one`() {
        val result = LayoutV3ResizePolicy.constrain(
            element(shape = "circle"),
            IntRect(10, 20, 0, 200),
        ) as LayoutV3ResizeDecision.Ready

        assertEquals(IntRect(10, 20, 1, 1), result.rect)
    }

    @Test
    fun `rectangle preserves independent dimensions`() {
        val target = IntRect(10, 20, 150, 120)
        val result = LayoutV3ResizePolicy.constrain(
            element(shape = "rectangle"),
            target,
        ) as LayoutV3ResizeDecision.Ready

        assertEquals(target, result.rect)
    }

    private fun element(shape: String) = LayoutV3EditorElement(
        elementId = "00000000-0000-0000-0000-000000000001",
        kind = ControlKind.KEYBOARD,
        rect = AnchoredRect(0, 0, 100, 100),
        resolvedRect = IntRect(10, 20, 100, 100),
        anchorX = HorizontalAnchor.LEFT,
        anchorY = VerticalAnchor.TOP,
        zOrder = 0,
        enabled = true,
        hidden = false,
        editableProperties = LayoutV3EditableProperties.Keyboard(
            InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 29),
            Appearance("A", "", shape, false),
            Trigger.TAP,
            null,
        ),
        inspectOnlySummary = null,
        capabilities = setOf(LayoutV3ElementCapability.RESIZE),
    )
}
