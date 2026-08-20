package com.limelight.ligase.stream.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamQuickMenuModelTest {
    @Test
    fun `main action ids are stable and unique`() {
        val actions = StreamQuickMenuContract.requiredMainActions
        assertEquals(actions.size, actions.map { it.id }.toSet().size)
        assertEquals(
            listOf(
                "continue",
                "disconnect",
                "upload_clipboard",
                "fetch_clipboard",
                "keyboard",
                "advanced",
                "quit",
            ),
            actions.map { it.id },
        )
    }

    @Test
    fun `only quit is dangerous and disconnect remains safe`() {
        val byId = StreamQuickMenuContract.requiredMainActions.associateBy { it.id }
        assertEquals(StreamMenuActionStyle.DANGER, byId.getValue("quit").style)
        assertEquals(StreamMenuSection.DANGER, byId.getValue("quit").section)
        assertEquals(StreamMenuActionStyle.NORMAL, byId.getValue("disconnect").style)
        assertEquals(StreamMenuSection.SESSION, byId.getValue("disconnect").section)
    }

    @Test
    fun `focus requirements keep explicit clipboard confirmation outside game focus`() {
        val byId = StreamQuickMenuContract.requiredMainActions.associateBy { it.id }
        listOf(
            "keyboard",
            "advanced",
        ).forEach { assertTrue("$it should wait for game focus", byId.getValue(it).requiresGameFocus) }
        assertFalse(byId.getValue("upload_clipboard").requiresGameFocus)
        assertFalse(byId.getValue("fetch_clipboard").requiresGameFocus)
        assertFalse(byId.getValue("continue").requiresGameFocus)
        assertFalse(byId.getValue("disconnect").requiresGameFocus)
        assertFalse(byId.getValue("quit").requiresGameFocus)
    }

    @Test
    fun `Ligase sessions hide persistent entry and disable automatic clipboard`() {
        assertFalse(StreamMenuEntryPolicy.showPersistentButton(true, true, true))
        assertTrue(StreamMenuEntryPolicy.showPersistentButton(false, true, true))
        assertFalse(StreamMenuEntryPolicy.allowAutomaticClipboardSync(true, true, true))
        assertTrue(StreamMenuEntryPolicy.allowAutomaticClipboardSync(false, true, true))
    }
}
