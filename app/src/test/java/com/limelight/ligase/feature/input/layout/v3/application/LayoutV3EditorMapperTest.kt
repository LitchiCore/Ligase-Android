package com.limelight.ligase.feature.input.layout.v3.application

import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorElement
import org.junit.Assert.*
import org.junit.Test

class LayoutV3EditorMapperTest {
    @Test
    fun immutableEditorProjectionMapsWithClipAndNoPayloadReconstruction() {
        val element = editorElement(
            rect = AnchoredRect(-60, 20, 100, 100),
            anchorX = HorizontalAnchor.LEFT,
            anchorY = VerticalAnchor.TOP,
        )
        val result = LayoutV3EditorMapper.mapEditorElement(
            IntSize(1000, 600),
            element,
            IntRect(0, 0, 1600, 1600),
        ) as LayoutV3EditorMapResult.Mapped
        assertEquals(IntRect(-96, 53, 267, 267), result.projection.rect)
        assertEquals(IntRect(0, 53, 171, 267), result.projection.clipRect)
        assertTrue(result.projection.drawVisible)
        assertTrue(result.projection.hitTestEnabled)
    }

    @Test
    fun hiddenAndDisabledSemanticsAreProjectedWithoutChangingGeometry() {
        val hidden = editorElement(hidden = true)
        val disabled = editorElement(enabled = false)
        val overlay = IntRect(0, 0, 1000, 600)
        val hiddenProjection = (
            LayoutV3EditorMapper.mapEditorElement(IntSize(1000, 600), hidden, overlay) as
                LayoutV3EditorMapResult.Mapped
            ).projection
        val disabledProjection = (
            LayoutV3EditorMapper.mapEditorElement(IntSize(1000, 600), disabled, overlay) as
                LayoutV3EditorMapResult.Mapped
            ).projection
        assertFalse(hiddenProjection.drawVisible)
        assertFalse(hiddenProjection.hitTestEnabled)
        assertTrue(disabledProjection.drawVisible)
        assertFalse(disabledProjection.hitTestEnabled)
        assertEquals(hiddenProjection.rect, disabledProjection.rect)
    }

    @Test
    fun invalidCanvasOverlayAndInvisibleElementReturnClosedIssues() {
        val element = editorElement()
        assertEquals(
            LayoutV3EditorMapIssue.INVALID_CANVAS,
            (LayoutV3EditorMapper.mapEditorElement(
                IntSize(0, 600),
                element,
                IntRect(0, 0, 1000, 600),
            ) as LayoutV3EditorMapResult.Rejected).issue,
        )
        assertEquals(
            LayoutV3EditorMapIssue.INVALID_OVERLAY,
            (LayoutV3EditorMapper.mapEditorElement(
                IntSize(1000, 600),
                element,
                IntRect(0, 0, 0, 600),
            ) as LayoutV3EditorMapResult.Rejected).issue,
        )
        assertEquals(
            LayoutV3EditorMapIssue.TARGET_NOT_VISIBLE,
            (LayoutV3EditorMapper.mapEditorElement(
                IntSize(1000, 600),
                editorElement(rect = AnchoredRect(-200, 0, 100, 100)),
                IntRect(0, 0, 1000, 600),
            ) as LayoutV3EditorMapResult.Rejected).issue,
        )
    }

    private fun editorElement(
        rect: AnchoredRect = AnchoredRect(10, 20, 100, 100),
        anchorX: HorizontalAnchor = HorizontalAnchor.LEFT,
        anchorY: VerticalAnchor = VerticalAnchor.TOP,
        enabled: Boolean = true,
        hidden: Boolean = false,
    ) = LayoutV3EditorElement(
        elementId = "00000000-0000-0000-0000-000000000001",
        kind = ControlKind.SOFT_KEYBOARD,
        rect = rect,
        resolvedRect = IntRect(rect.horizontalOffset, rect.verticalOffset, rect.width, rect.height),
        anchorX = anchorX,
        anchorY = anchorY,
        zOrder = 0,
        enabled = enabled,
        hidden = hidden,
        editableProperties = null,
        inspectOnlySummary = null,
        capabilities = emptySet(),
    )
}
