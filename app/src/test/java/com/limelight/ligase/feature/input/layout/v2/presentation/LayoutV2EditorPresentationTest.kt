package com.limelight.ligase.feature.input.layout.v2.presentation

import com.limelight.ligase.feature.input.layout.v2.domain.IntRect
import com.limelight.ligase.feature.input.layout.v2.domain.IntSize
import com.limelight.ligase.feature.input.layout.v2.domain.Appearance
import com.limelight.ligase.feature.input.layout.v2.domain.InputCode
import com.limelight.ligase.feature.input.layout.v2.domain.InputCodeNamespace
import com.limelight.ligase.feature.input.layout.v2.domain.Trigger
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditableProperties
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

    @Test
    fun `shape protocol values are the exact three supported choices`() {
        assertEquals(
            listOf("circle", "roundedrectangle", "rectangle"),
            LayoutV2EditorShape.entries.map(LayoutV2EditorShape::protocolValue),
        )
        LayoutV2EditorShape.entries.forEach {
            assertEquals(it, layoutV2EditorShape(it.protocolValue))
        }
        assertEquals(null, layoutV2EditorShape("oval"))
    }

    @Test
    fun `changing keyboard shape preserves every other typed field`() {
        val original = LayoutV2EditableProperties.Keyboard(
            inputCode = InputCode(InputCodeNamespace.USB_HID_KEYBOARD_USAGE, 42),
            appearance = Appearance(
                label = "Jump",
                description = "Hold to jump",
                shape = "circle",
                showPhysicalKeyNames = true,
            ),
            trigger = Trigger.TIMED_HOLD,
            timedHoldMs = 750,
        )

        val changed = original.withEditorShape(LayoutV2EditorShape.ROUNDED_RECTANGLE)
            as LayoutV2EditableProperties.Keyboard

        assertEquals(
            original.copy(
                appearance = original.appearance.copy(shape = "roundedrectangle"),
            ),
            changed,
        )
    }

    @Test
    fun `changing mouse shape preserves button trigger and appearance metadata`() {
        val original = LayoutV2EditableProperties.Mouse(
            button = "secondary",
            appearance = Appearance(
                label = "Aim",
                description = "Aim button",
                shape = "circle",
                showPhysicalKeyNames = false,
            ),
            trigger = Trigger.TOGGLE,
            timedHoldMs = null,
        )

        val changed = original.withEditorShape(LayoutV2EditorShape.RECTANGLE)
            as LayoutV2EditableProperties.Mouse

        assertEquals(
            original.copy(
                appearance = original.appearance.copy(shape = "rectangle"),
            ),
            changed,
        )
    }
}
