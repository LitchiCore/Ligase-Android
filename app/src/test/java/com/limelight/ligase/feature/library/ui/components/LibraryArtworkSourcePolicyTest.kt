package com.limelight.ligase.feature.library.ui.components

import com.limelight.ligase.feature.library.domain.HostCoverAuthority
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LibraryItemKey
import com.limelight.nvstream.http.NvApp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryArtworkSourcePolicyTest {
    @Test fun `Host authority never selects legacy loader even when it is available`() {
        val item = item().copy(coverAuthority = authority)
        assertEquals(
            LibraryArtworkSource.VERIFIED_HOST,
            libraryArtworkSource(item, verifiedLoaderAvailable = true, legacyLoaderAvailable = true),
        )
        assertEquals(
            LibraryArtworkSource.PLACEHOLDER,
            libraryArtworkSource(item, verifiedLoaderAvailable = false, legacyLoaderAvailable = true),
        )
    }

    @Test fun `only authority absent launch items may retain explicit legacy boundary`() {
        assertEquals(
            LibraryArtworkSource.LEGACY,
            libraryArtworkSource(item(), verifiedLoaderAvailable = true, legacyLoaderAvailable = true),
        )
        assertEquals(
            LibraryArtworkSource.PLACEHOLDER,
            libraryArtworkSource(item().copy(launchApp = null), true, true),
        )
    }

    private fun item() = LigaseLibraryItem(
        key = LibraryItemKey.HostUuid(UUID.uppercase()),
        name = "Example",
        kind = null,
        hostAppUuid = UUID.uppercase(),
        appId = 7,
        steamAppId = 123,
        addedAt = null,
        updatedAt = null,
        lastPlayedAt = null,
        launchApp = NvApp("Example", UUID.uppercase(), 7, false),
    )

    companion object {
        private const val UUID = "2c42a3d0-79f1-4bb6-98f8-40c18cd5bc91"
        private val authority = HostCoverAuthority(
            UUID,
            "431ced6916a2a21a156e38701afe55bbd7f88969fbbfc56d7fe099d47f265460",
            "steamClientLibraryCache",
            "123",
            "thirdPartyArtworkLocalUseOnlyNoRedistribution",
        )
    }
}
