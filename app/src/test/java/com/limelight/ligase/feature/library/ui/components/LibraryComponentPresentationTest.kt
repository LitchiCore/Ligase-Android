package com.limelight.ligase.feature.library.ui.components

import com.limelight.ligase.feature.library.domain.HostSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryComponentPresentationTest {
    @Test
    fun `sort options preserve product order`() {
        val options = librarySortOptions(
            canSortByLastPlayed = true,
            canManualSort = true,
        )

        assertEquals(HostSortMode.entries, options.map { it.mode })
        assertTrue(options.all { it.enabled })
    }

    @Test
    fun `unavailable sorts remain visible but disabled`() {
        val options = librarySortOptions(
            canSortByLastPlayed = false,
            canManualSort = false,
        ).associateBy { it.mode }

        assertFalse(options.getValue(HostSortMode.LAST_PLAYED_NEWEST).enabled)
        assertFalse(options.getValue(HostSortMode.MANUAL).enabled)
        assertTrue(options.getValue(HostSortMode.NAME_ASCENDING).enabled)
        assertTrue(options.getValue(HostSortMode.ADDED_NEWEST).enabled)
    }
}
