package com.limelight.ligase.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncAutoLoadPolicyTest {
    @Test
    fun loadsOnlyBeforeAStableResultOrUserRetry() {
        assertTrue(
            LibrarySyncAutoLoadPolicy.shouldFetch(
                hasSnapshot = false,
                status = LigaseLibraryStatus.LOADING,
            ),
        )
        assertFalse(
            LibrarySyncAutoLoadPolicy.shouldFetch(
                hasSnapshot = true,
                status = LigaseLibraryStatus.READY,
            ),
        )
        assertFalse(
            LibrarySyncAutoLoadPolicy.shouldFetch(
                hasSnapshot = false,
                status = LigaseLibraryStatus.SYNC_ERROR,
            ),
        )
        assertFalse(
            LibrarySyncAutoLoadPolicy.shouldFetch(
                hasSnapshot = false,
                status = LigaseLibraryStatus.PERMISSION_ERROR,
            ),
        )
    }
}
