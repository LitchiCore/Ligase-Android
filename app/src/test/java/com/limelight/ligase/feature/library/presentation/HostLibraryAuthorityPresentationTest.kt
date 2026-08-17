package com.limelight.ligase.feature.library.presentation

import com.limelight.ligase.feature.library.domain.HostLayoutBindingResolution
import com.limelight.ligase.feature.library.domain.HostLayoutBindingState
import com.limelight.ligase.feature.library.domain.HostPortableIdentity
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LibraryItemKey
import com.limelight.ligase.feature.library.domain.HostCoverAuthority
import com.limelight.ligase.feature.library.application.HostVerifiedCoverState
import com.limelight.ligase.feature.library.data.repository.HostCoverIssue
import com.limelight.ligase.feature.library.data.repository.VerifiedHostCover
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
        )

        assertNull(value.portableIdentityProvider)
        assertNull(value.portableIdentityId)
    }

    @Test fun `cover current derives only from typed verified loader result`() {
        val authority = HostCoverAuthority(UUID, SHA, "steam", "123", "local")
        val base = presentHostLibraryAuthority(
            item().copy(coverAuthority = authority),
            HostLayoutBindingResolution(HostLayoutBindingState.NO_EXPLICIT_BINDING),
        )
        val cover = VerifiedHostCover(UUID, SHA, byteArrayOf(1))

        assertFalse(base.coverCurrent)
        assertEquals(true, base.withCoverState(HostVerifiedCoverState.Current(cover)).coverCurrent)
        assertFalse(
            base.withCoverState(
                HostVerifiedCoverState.Rejected(HostCoverIssue.INVALID_LENGTH, cover),
            ).coverCurrent,
        )
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

    companion object {
        private const val UUID = "2c42a3d0-79f1-4bb6-98f8-40c18cd5bc91"
        private const val SHA = "431ced6916a2a21a156e38701afe55bbd7f88969fbbfc56d7fe099d47f265460"
    }
}
