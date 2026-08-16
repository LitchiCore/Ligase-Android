package com.limelight.ligase.feature.library.presentation

import com.limelight.ligase.feature.library.domain.HostLayoutBindingResolution
import com.limelight.ligase.feature.library.domain.HostLayoutBindingState
import com.limelight.ligase.feature.library.domain.HostPortableIdentity
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LibraryItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HostLibraryAuthorityPresentationTest {
    @Test fun `Steam identity and no explicit binding are safely projected`() {
        val item = item().copy(portableIdentity = HostPortableIdentity("steam", "123"))
        val value = presentHostLibraryAuthority(
            item,
            HostLayoutBindingResolution(HostLayoutBindingState.NO_EXPLICIT_BINDING),
            verifiedCoverCurrent = false,
        )

        assertEquals("steam", value.portableIdentityProvider)
        assertEquals("123", value.portableIdentityId)
        assertEquals(HostLayoutBindingState.NO_EXPLICIT_BINDING, value.layoutState)
        assertFalse(value.layoutReadyLocally)
    }

    @Test fun `system item without portable identity exposes no guessed identity`() {
        val value = presentHostLibraryAuthority(
            item(),
            HostLayoutBindingResolution(HostLayoutBindingState.NO_EXPLICIT_BINDING),
            verifiedCoverCurrent = false,
        )

        assertNull(value.portableIdentityProvider)
        assertNull(value.portableIdentityId)
    }

    private fun item() = LigaseLibraryItem(
        key = LibraryItemKey.HostUuid("2C42A3D0-79F1-4BB6-98F8-40C18CD5BC91"),
        name = "Desktop",
        kind = null,
        hostAppUuid = "2C42A3D0-79F1-4BB6-98F8-40C18CD5BC91",
        appId = null,
        steamAppId = null,
        addedAt = "2026-08-16T00:00:00Z",
        updatedAt = "2026-08-16T00:00:00Z",
        lastPlayedAt = null,
        launchApp = null,
    )
}
