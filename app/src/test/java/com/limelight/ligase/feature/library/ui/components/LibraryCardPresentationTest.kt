package com.limelight.ligase.feature.library.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCardPresentationTest {
    @Test
    fun `launch and configure are enabled for an operable launchable card`() {
        val policy = libraryCardActionPolicy(
            isLaunchable = true,
            canOperate = true,
            manualEditing = false,
        )

        assertTrue(policy.launchEnabled)
        assertTrue(policy.configureEnabled)
        assertFalse(policy.showDragHandle)
        assertNull(policy.launchDisabledReason)
        assertNull(policy.configureDisabledReason)
    }

    @Test
    fun `missing launch identity disables launch but preserves configure`() {
        val policy = libraryCardActionPolicy(
            isLaunchable = false,
            canOperate = true,
            manualEditing = false,
        )

        assertFalse(policy.launchEnabled)
        assertTrue(policy.configureEnabled)
        assertEquals(
            LibraryCardDisabledReason.NOT_LAUNCHABLE,
            policy.launchDisabledReason,
        )
        assertNull(policy.configureDisabledReason)
    }

    @Test
    fun `operation gate disables both actions`() {
        val policy = libraryCardActionPolicy(
            isLaunchable = true,
            canOperate = false,
            manualEditing = false,
        )

        assertFalse(policy.launchEnabled)
        assertFalse(policy.configureEnabled)
        assertEquals(
            LibraryCardDisabledReason.OPERATIONS_DISABLED,
            policy.launchDisabledReason,
        )
        assertEquals(
            LibraryCardDisabledReason.OPERATIONS_DISABLED,
            policy.configureDisabledReason,
        )
    }

    @Test
    fun `manual editing owns the affordance and disables card actions`() {
        val policy = libraryCardActionPolicy(
            isLaunchable = true,
            canOperate = true,
            manualEditing = true,
        )

        assertFalse(policy.launchEnabled)
        assertFalse(policy.configureEnabled)
        assertTrue(policy.showDragHandle)
        assertEquals(
            LibraryCardDisabledReason.MANUAL_EDITING,
            policy.launchDisabledReason,
        )
        assertEquals(
            LibraryCardDisabledReason.MANUAL_EDITING,
            policy.configureDisabledReason,
        )
    }
}
