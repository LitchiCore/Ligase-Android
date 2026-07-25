package com.limelight.ligase.feature.input.layout.v2.presentation

import com.limelight.ligase.feature.input.layout.v2.domain.IntRect
import com.limelight.ligase.feature.input.layout.v2.domain.IntSize
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorState
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2RecoveryProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutV2EditorPresentationTest {
    @Test
    fun `blank draft without elements cannot be saved`() {
        val result = presentLayoutV2Editor(LayoutV2EditorState())

        assertFalse(result.hasDraft)
        assertFalse(result.hasElements)
        assertFalse(result.canSave)
    }

    @Test
    fun `journal failure remains a visible non destructive warning`() {
        val result = presentLayoutV2Editor(
            LayoutV2EditorState(
                recoveryProtection = LayoutV2RecoveryProtection.NOT_SAVED,
            ),
        )

        assertTrue(result.showRecoveryWarning)
    }

    @Test
    fun `integer canvas transform round trips drag delta after zoom`() {
        val transform = LayoutV2CanvasTransform(
            canvas = IntSize(1920, 1080),
            viewportWidthPx = 960f,
            viewportHeightPx = 540f,
            zoom = 2f,
        )

        val screen = transform.screenRect(IntRect(100, 80, 200, 120))
        val delta = transform.canvasDelta(50f, 30f)

        assertEquals(-380f, screen.left, 0.001f)
        assertEquals(-190f, screen.top, 0.001f)
        assertEquals(200f, screen.width, 0.001f)
        assertEquals(120f, screen.height, 0.001f)
        assertEquals(50 to 30, delta)
    }

    @Test
    fun `wide editor keeps two thirds for canvas`() {
        assertEquals(0.66f, layoutV2EditorCanvasWeight(wide = true), 0.001f)
        assertEquals(1f, layoutV2EditorCanvasWeight(wide = false), 0.001f)
    }

    @Test
    fun `black editor overlay never resizes canvas`() {
        assertEquals(
            layoutV2BlackEditorCanvasSize(2400, 1080, toolsOpen = false),
            layoutV2BlackEditorCanvasSize(2400, 1080, toolsOpen = true),
        )
    }

    @Test
    fun `black editor back closes overlay before leaving`() {
        assertEquals(
            LayoutV2BlackEditorBackAction.CLOSE_TOOLS,
            layoutV2BlackEditorBackAction(
                handoffReady = true,
                toolsOpen = true,
                dirty = true,
            ),
        )
        assertEquals(
            LayoutV2BlackEditorBackAction.CONFIRM_LEAVE,
            layoutV2BlackEditorBackAction(
                handoffReady = true,
                toolsOpen = false,
                dirty = true,
            ),
        )
    }
}
