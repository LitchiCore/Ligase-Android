package com.limelight.ligase.feature.library.ui.manual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLibraryEditorPresentationTest {
    @Test
    fun `dirty operable draft enables save and cancel`() {
        val actions = manualLibraryEditActions(
            isDirty = true,
            editingEnabled = true,
            saving = false,
        )

        assertTrue(actions.saveEnabled)
        assertTrue(actions.cancelEnabled)
    }

    @Test
    fun `clean or gated draft cannot save`() {
        assertFalse(
            manualLibraryEditActions(
                isDirty = false,
                editingEnabled = true,
                saving = false,
            ).saveEnabled,
        )
        assertFalse(
            manualLibraryEditActions(
                isDirty = true,
                editingEnabled = false,
                saving = false,
            ).saveEnabled,
        )
    }

    @Test
    fun `saving disables both final actions`() {
        val actions = manualLibraryEditActions(
            isDirty = true,
            editingEnabled = true,
            saving = true,
        )

        assertFalse(actions.saveEnabled)
        assertFalse(actions.cancelEnabled)
    }

    @Test
    fun `drag direction changes only at the existing threshold`() {
        assertNull(manualLibraryMoveDirection(47.9f, 48f))
        assertNull(manualLibraryMoveDirection(-47.9f, 48f))
        assertEquals(1, manualLibraryMoveDirection(48f, 48f))
        assertEquals(-1, manualLibraryMoveDirection(-48f, 48f))
    }
}
