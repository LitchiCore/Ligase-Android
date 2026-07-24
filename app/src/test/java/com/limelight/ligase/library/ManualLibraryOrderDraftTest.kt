package com.limelight.ligase.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLibraryOrderDraftTest {
    @Test
    fun `moves every published item including system desktops`() {
        val draft = draft()

        val moved = draft.move(movingUuid = "desktop", targetUuid = "game-b")

        assertEquals(
            listOf("game-a", "virtual", "game-b", "desktop", "game-c"),
            moved.orderedUuids,
        )
        assertEquals(
            listOf("game-a", "virtual", "game-b", "desktop", "game-c"),
            moved.orderedPublishedUuids,
        )
        assertTrue(moved.isDirty)
    }

    @Test
    fun `ignores unknown move endpoints`() {
        val draft = draft()

        assertEquals(draft, draft.move("missing", "game-a"))
        assertEquals(draft, draft.move("game-a", "missing"))
        assertFalse(draft.isDirty)
    }

    @Test
    fun `adjacent targets include system items`() {
        val draft = draft()

        assertEquals("virtual", draft.adjacentMovableUuid("game-a", 1))
        assertEquals("desktop", draft.adjacentMovableUuid("game-a", -1))
        assertEquals("game-a", draft.adjacentMovableUuid("virtual", -1))
        assertEquals("game-c", draft.adjacentMovableUuid("game-b", 1))
    }

    @Test
    fun `wire order normalizes all published UUIDs without changing entry keys`() {
        val uppercase = ManualLibraryOrderDraft(
            originalEntries = listOf(
                entry("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"),
                entry("BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB"),
            ),
        )

        assertEquals(
            listOf(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            ),
            uppercase.orderedPublishedUuids,
        )
        assertEquals(
            "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
            uppercase.entries.first().uuid,
        )
    }

    private fun draft(): ManualLibraryOrderDraft {
        val entries = listOf(
            entry("desktop"),
            entry("game-a"),
            entry("virtual"),
            entry("game-b"),
            entry("game-c"),
        )
        return ManualLibraryOrderDraft(originalEntries = entries)
    }

    private fun entry(
        uuid: String,
        locked: Boolean = false,
    ): ManualLibraryOrderEntry =
        ManualLibraryOrderEntry(
            uuid = uuid,
            name = uuid,
            locked = locked,
        )
}
