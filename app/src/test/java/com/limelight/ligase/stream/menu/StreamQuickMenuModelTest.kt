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
                "server_commands",
                "keyboard",
                "zoom",
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
    fun `focus requirements preserve clipboard command keyboard zoom and advanced behavior`() {
        val byId = StreamQuickMenuContract.requiredMainActions.associateBy { it.id }
        listOf(
            "upload_clipboard",
            "fetch_clipboard",
            "server_commands",
            "keyboard",
            "zoom",
            "advanced",
        ).forEach { assertTrue("$it should wait for game focus", byId.getValue(it).requiresGameFocus) }
        assertFalse(byId.getValue("continue").requiresGameFocus)
        assertFalse(byId.getValue("disconnect").requiresGameFocus)
        assertFalse(byId.getValue("quit").requiresGameFocus)
    }
}
