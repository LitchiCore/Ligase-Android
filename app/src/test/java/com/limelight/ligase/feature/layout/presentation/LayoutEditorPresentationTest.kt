package com.limelight.ligase.feature.layout.presentation

import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LayoutEditorPresentationTest {
    @Test
    fun `hall uses two columns only when enough width is available`() {
        assertEquals(LayoutHallPaneMode.SINGLE_COLUMN, layoutHallPaneMode(719))
        assertEquals(LayoutHallPaneMode.TWO_COLUMN, layoutHallPaneMode(720))
    }

    @Test
    fun `move keeps a known control inside normalized canvas`() {
        val moved = element().moveBy(deltaX = 2f, deltaY = -2f)

        assertEquals(0.8f, moved.x)
        assertEquals(0f, moved.y)
    }

    @Test
    fun `resize keeps control inside remaining canvas`() {
        val resized = element(x = 0.75f, y = 0.7f).resizeBy(4f)

        assertEquals(0.25f, resized.width)
        assertEquals(0.3f, resized.height)
    }

    @Test
    fun `unknown control remains read only`() {
        val unknown = element(kind = LayoutControlKind.UNKNOWN, deletable = true)

        assertEquals(unknown, unknown.moveBy(0.2f, 0.2f))
        assertEquals(unknown, unknown.resizeBy(2f))
        assertFalse(unknown.canDelete())
    }

    @Test
    fun `clean initial editor does not navigate without an explicit save`() {
        assertEquals(
            LayoutSaveNavigation.WAIT,
            layoutSaveNavigation(
                pendingSave = false,
                saving = false,
                dirty = false,
                hasError = false,
            ),
        )
    }

    @Test
    fun `successful pending save returns to hall only after saving settles`() {
        assertEquals(
            LayoutSaveNavigation.WAIT,
            layoutSaveNavigation(true, saving = true, dirty = true, hasError = false),
        )
        assertEquals(
            LayoutSaveNavigation.HALL,
            layoutSaveNavigation(true, saving = false, dirty = false, hasError = false),
        )
    }

    @Test
    fun `failed pending save stays in editor`() {
        assertEquals(
            LayoutSaveNavigation.STAY_EDITOR,
            layoutSaveNavigation(true, saving = false, dirty = true, hasError = true),
        )
    }

    private fun element(
        kind: LayoutControlKind = LayoutControlKind.KEYBOARD_KEY,
        x: Float = 0.1f,
        y: Float = 0.2f,
        deletable: Boolean = true,
    ) = LayoutEditorElement(
        elementId = "key-a",
        kind = kind,
        x = x,
        y = y,
        width = 0.2f,
        height = 0.2f,
        deletable = deletable,
    )
}
