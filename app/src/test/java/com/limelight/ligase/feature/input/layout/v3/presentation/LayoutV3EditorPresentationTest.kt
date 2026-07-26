package com.limelight.ligase.feature.input.layout.v3.presentation

import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditableProperties
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorHandoffIssue
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorHandoffResult
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorState
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorElement
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3ElementCapability
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3RecoveryProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutV3EditorPresentationTest {
    @Test
    fun `control label scales with short edge and remains bounded`() {
        assertEquals(13f, layoutV3EditorLabelSizeSp(8f))
        assertEquals(17f, layoutV3EditorLabelSizeSp(50f))
        assertEquals(34f, layoutV3EditorLabelSizeSp(500f))
    }

    @Test
    fun `opacity composes control content without exceeding typed bounds`() {
        assertEquals(0f, layoutV3EditorContentAlpha(0))
        assertEquals(0.375f, layoutV3EditorContentAlpha(375))
        assertEquals(1f, layoutV3EditorContentAlpha(1000))
        assertEquals(0f, layoutV3EditorContentAlpha(-1))
        assertEquals(1f, layoutV3EditorContentAlpha(1001))
        assertEquals(0.82f, layoutV3EditorTextAlpha(1000))
    }

    @Test
    fun `blank draft without elements cannot be saved`() {
        val result = presentLayoutV3Editor(LayoutV3EditorState())

        assertFalse(result.hasDraft)
        assertFalse(result.hasElements)
        assertFalse(result.canSave)
    }

    @Test
    fun `journal failure remains a visible non destructive warning`() {
        val result = presentLayoutV3Editor(
            LayoutV3EditorState(
                recoveryProtection = LayoutV3RecoveryProtection.NOT_SAVED,
            ),
        )

        assertTrue(result.showRecoveryWarning)
    }

    @Test
    fun `black editor back closes overlay before leaving`() {
        assertEquals(
            LayoutV3BlackEditorBackAction.CLOSE_TOOLS,
            layoutV3BlackEditorBackAction(
                handoff = LayoutV3EditorHandoffResult.LaunchReady("redacted"),
                toolsOpen = true,
                dirty = true,
            ),
        )
        assertEquals(
            LayoutV3BlackEditorBackAction.CONFIRM_LEAVE,
            layoutV3BlackEditorBackAction(
                handoff = LayoutV3EditorHandoffResult.LaunchReady("redacted"),
                toolsOpen = false,
                dirty = true,
            ),
        )
        assertEquals(
            LayoutV3BlackEditorBackAction.ABORT,
            layoutV3BlackEditorBackAction(
                handoff = LayoutV3EditorHandoffResult.Rejected(
                    LayoutV3EditorHandoffIssue.MISSING,
                ),
                toolsOpen = false,
                dirty = false,
            ),
        )
    }

    @Test
    fun `editor panel stays opposite the selected control`() {
        assertEquals(
            LayoutV3EditorPanelSide.RIGHT,
            layoutV3EditorPanelSide(selectedCenterPx = 300, viewportWidthPx = 1000),
        )
        assertEquals(
            LayoutV3EditorPanelSide.LEFT,
            layoutV3EditorPanelSide(selectedCenterPx = 700, viewportWidthPx = 1000),
        )
        assertEquals(
            LayoutV3EditorPanelSide.LEFT,
            layoutV3EditorPanelSide(selectedCenterPx = 500, viewportWidthPx = 1000),
        )
        assertEquals(
            LayoutV3EditorPanelSide.RIGHT,
            layoutV3EditorPanelSide(selectedCenterPx = null, viewportWidthPx = 1000),
        )
    }

    @Test
    fun `resize handle never consumes more than one fifth of a control`() {
        assertEquals(20, layoutV3ResizeHandleHitSizePx(300, 200, 20))
        assertEquals(8, layoutV3ResizeHandleHitSizePx(80, 40, 20))
        assertEquals(1, layoutV3ResizeHandleHitSizePx(4, 4, 20))
    }

    @Test
    fun `circle resize preview stays square before pointer release`() {
        val base = IntRect(10, 20, 100, 100)
        assertEquals(
            IntRect(10, 20, 160, 160),
            layoutV3ResizePreviewRect(
                previewElement("circle", base),
                base,
                deltaX = 60,
                deltaY = 10,
            ),
        )
        assertEquals(
            IntRect(10, 20, 145, 145),
            layoutV3ResizePreviewRect(
                previewElement("circle", base),
                base,
                deltaX = 5,
                deltaY = 45,
            ),
        )
    }

    @Test
    fun `rectangle resize preview keeps independent axes`() {
        val base = IntRect(10, 20, 100, 80)
        assertEquals(
            IntRect(10, 20, 140, 95),
            layoutV3ResizePreviewRect(
                previewElement("rectangle", base),
                base,
                deltaX = 40,
                deltaY = 15,
            ),
        )
    }

    @Test
    fun `nudge pad follows keyboard arrow layout and fixed repeat timing`() {
        assertEquals(
            listOf(
                listOf(LayoutV3NudgeDirection.UP),
                listOf(
                    LayoutV3NudgeDirection.LEFT,
                    LayoutV3NudgeDirection.DOWN,
                    LayoutV3NudgeDirection.RIGHT,
                ),
            ),
            LAYOUT_V3_NUDGE_PAD_ROWS,
        )
        assertEquals(350L, LAYOUT_V3_NUDGE_REPEAT_DELAY_MS)
        assertEquals(60L, LAYOUT_V3_NUDGE_REPEAT_INTERVAL_MS)
    }

    @Test
    fun `nudge preview emits only a typed canonical delta`() {
        assertEquals(LayoutV3NudgeDelta(0, -1), layoutV3NudgeDelta(
            LayoutV3NudgeDirection.UP,
        ))
        assertEquals(LayoutV3NudgeDelta(-5, 0), layoutV3NudgeDelta(
            LayoutV3NudgeDirection.LEFT,
            steps = 5,
        ))
        assertEquals(LayoutV3NudgeDelta(0, 6), layoutV3NudgeDelta(
            LayoutV3NudgeDirection.DOWN,
            steps = 6,
        ))
        assertEquals(LayoutV3NudgeDelta(7, 0), layoutV3NudgeDelta(
            LayoutV3NudgeDirection.RIGHT,
            steps = 7,
        ))
    }

    @Test
    fun `shape protocol values are the exact three supported choices`() {
        assertEquals(
            listOf("circle", "roundedrectangle", "rectangle"),
            LayoutV3EditorShape.entries.map(LayoutV3EditorShape::protocolValue),
        )
        LayoutV3EditorShape.entries.forEach {
            assertEquals(it, layoutV3EditorShape(it.protocolValue))
        }
        assertEquals(null, layoutV3EditorShape("oval"))
    }

    @Test
    fun `changing keyboard shape preserves every other typed field`() {
        val original = LayoutV3EditableProperties.Keyboard(
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

        val changed = original.withEditorShape(LayoutV3EditorShape.ROUNDED_RECTANGLE)
            as LayoutV3EditableProperties.Keyboard

        assertEquals(
            original.copy(
                appearance = original.appearance.copy(shape = "roundedrectangle"),
            ),
            changed,
        )
    }

    @Test
    fun `changing keyboard description preserves all other typed fields`() {
        val original = LayoutV3EditableProperties.Keyboard(
            inputCode = InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 51),
            appearance = Appearance(
                label = "W",
                description = "",
                shape = "circle",
                showPhysicalKeyNames = true,
            ),
            trigger = Trigger.TIMED_HOLD,
            timedHoldMs = 400,
        )

        val changed = original.withEditorDescription("Move forward")

        assertEquals(
            original.copy(
                appearance = original.appearance.copy(description = "Move forward"),
            ),
            changed,
        )
    }

    @Test
    fun `changing mouse shape preserves button trigger and appearance metadata`() {
        val original = LayoutV3EditableProperties.Mouse(
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

        val changed = original.withEditorShape(LayoutV3EditorShape.RECTANGLE)
            as LayoutV3EditableProperties.Mouse

        assertEquals(
            original.copy(
                appearance = original.appearance.copy(shape = "rectangle"),
            ),
            changed,
        )
    }

    private fun previewElement(shape: String, rect: IntRect) = LayoutV3EditorElement(
        elementId = "00000000-0000-0000-0000-000000000001",
        kind = ControlKind.KEYBOARD,
        rect = AnchoredRect(0, 0, rect.width, rect.height),
        resolvedRect = rect,
        anchorX = HorizontalAnchor.LEFT,
        anchorY = VerticalAnchor.TOP,
        zOrder = 0,
        enabled = true,
        hidden = false,
        opacityPermille = 1000,
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
