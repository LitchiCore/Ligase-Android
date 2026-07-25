package com.limelight.ligase.app.root

import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LigaseRootRouteTest {
    @Test
    fun selectingAnyMainPageResetsNestedLayoutRoute() {
        assertEquals(LigaseLayoutRoute.MAIN, rootRouteAfterMainPageSelection())
    }

    @Test
    fun libraryGridRetentionKeyPersistsForSameHostAndLayout() {
        val first = libraryGridRetentionKey("host-a", LibraryLayoutMode.POSTER)
        val recomposed = libraryGridRetentionKey("host-a", LibraryLayoutMode.POSTER)

        assertEquals(first, recomposed)
    }

    @Test
    fun libraryGridRetentionKeyResetsForHostOrLayoutChange() {
        val original = libraryGridRetentionKey("host-a", LibraryLayoutMode.POSTER)

        assertNotEquals(
            original,
            libraryGridRetentionKey("host-b", LibraryLayoutMode.POSTER),
        )
        assertNotEquals(
            original,
            libraryGridRetentionKey("host-a", LibraryLayoutMode.LIST),
        )
    }

    @Test
    fun manualDraftRetentionTracksOnlySelectedHost() {
        assertEquals("host-a", manualDraftRetentionKey("host-a"))
        assertEquals(null, manualDraftRetentionKey(null))
        assertNotEquals(
            manualDraftRetentionKey("host-a"),
            manualDraftRetentionKey("host-b"),
        )
    }
}
