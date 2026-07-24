package com.limelight.ligase.library

import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus

import org.junit.Assert.assertEquals
import org.junit.Test

class LigaseLibraryPresentationTest {
    @Test
    fun `loading keeps last successful items visible`() {
        assertEquals(
            LibraryContentPresentation.CONTENT,
            libraryContentPresentation(
                status = LigaseLibraryStatus.LOADING,
                hasItems = true,
            ),
        )
        assertEquals(
            PreservedLibraryBanner.LOADING,
            preservedLibraryBanner(
                status = LigaseLibraryStatus.LOADING,
                hasItems = true,
                connectivity = LibraryConnectivity.ONLINE,
            ),
        )
    }

    @Test
    fun `sync error keeps last successful items visible`() {
        assertEquals(
            LibraryContentPresentation.CONTENT,
            libraryContentPresentation(
                status = LigaseLibraryStatus.SYNC_ERROR,
                hasItems = true,
            ),
        )
        assertEquals(
            PreservedLibraryBanner.ERROR,
            preservedLibraryBanner(
                status = LigaseLibraryStatus.SYNC_ERROR,
                hasItems = true,
                connectivity = LibraryConnectivity.ONLINE,
            ),
        )
    }

    @Test
    fun `initial loading remains blocking without content`() {
        assertEquals(
            LibraryContentPresentation.BLOCKING_STATUS,
            libraryContentPresentation(
                status = LigaseLibraryStatus.LOADING,
                hasItems = false,
            ),
        )
        assertEquals(
            null,
            preservedLibraryBanner(
                status = LigaseLibraryStatus.LOADING,
                hasItems = false,
                connectivity = LibraryConnectivity.CHECKING,
            ),
        )
    }

    @Test
    fun `permission and compatibility failures do not expose stale content`() {
        for (status in listOf(
            LigaseLibraryStatus.PERMISSION_ERROR,
            LigaseLibraryStatus.INCOMPATIBLE,
        )) {
            assertEquals(
                LibraryContentPresentation.BLOCKING_STATUS,
                libraryContentPresentation(status = status, hasItems = true),
            )
            assertEquals(
                null,
                preservedLibraryBanner(
                    status = status,
                    hasItems = true,
                    connectivity = LibraryConnectivity.OFFLINE,
                ),
            )
        }
    }

    @Test
    fun `connectivity is presented without replacing content`() {
        assertEquals(
            PreservedLibraryBanner.CHECKING,
            preservedLibraryBanner(
                status = LigaseLibraryStatus.READY,
                hasItems = true,
                connectivity = LibraryConnectivity.CHECKING,
            ),
        )
        assertEquals(
            PreservedLibraryBanner.OFFLINE,
            preservedLibraryBanner(
                status = LigaseLibraryStatus.READY,
                hasItems = true,
                connectivity = LibraryConnectivity.OFFLINE,
            ),
        )
    }
}
