package com.limelight.ligase.feature.library.ui.components

import com.limelight.ligase.feature.library.domain.HostLayoutBindingState
import com.limelight.ligase.feature.library.presentation.HostLibraryAuthorityPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCardPresentationTest {
    @Test
    fun `canonical Steam identity and closed layout state are projected`() {
        val metadata = libraryCardAuthorityMetadata(
            authority(provider = "steam", id = "3548580", state = HostLayoutBindingState.RESOLVED),
        )

        assertEquals("3548580", metadata.steamAppId)
        assertEquals(HostLayoutBindingState.RESOLVED, metadata.layoutState)
    }

    @Test
    fun `non Steam absent and malformed identities do not become public App IDs`() {
        assertNull(libraryCardAuthorityMetadata(authority(provider = "gog", id = "3548580")).steamAppId)
        assertNull(libraryCardAuthorityMetadata(authority(provider = "steam", id = "0")).steamAppId)
        assertNull(libraryCardAuthorityMetadata(authority(provider = "steam", id = "35A8580")).steamAppId)
        assertNull(libraryCardAuthorityMetadata(authority(provider = null, id = null)).steamAppId)
    }

    @Test
    fun `all closed layout states pass through without identity inference`() {
        HostLayoutBindingState.entries.forEach { state ->
            val metadata = libraryCardAuthorityMetadata(authority(provider = null, id = null, state = state))
            assertNull(metadata.steamAppId)
            assertEquals(state, metadata.layoutState)
        }
    }

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

    private fun authority(
        provider: String?,
        id: String?,
        state: HostLayoutBindingState = HostLayoutBindingState.NO_EXPLICIT_BINDING,
    ) = HostLibraryAuthorityPresentation(
        hasPortableIdentity = provider != null && id != null,
        portableIdentityProvider = provider,
        portableIdentityId = id,
        hasCoverAuthority = false,
        layoutState = state,
        layoutReadyLocally = state == HostLayoutBindingState.RESOLVED,
        coverCurrent = false,
    )
}
