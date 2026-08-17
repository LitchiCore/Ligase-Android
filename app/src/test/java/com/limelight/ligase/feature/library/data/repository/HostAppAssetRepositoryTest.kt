package com.limelight.ligase.feature.library.data.repository

import com.limelight.ligase.feature.library.domain.HostCoverAuthority
import com.limelight.nvstream.http.NvApp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class HostAppAssetRepositoryTest {
    private val repository = HostAppAssetRepository()
    private val app = NvApp("Example", UUID.uppercase(), 7, false)
    private val authority = HostCoverAuthority(UUID, SHA, "steamClientLibraryCache", "123", "thirdPartyArtworkLocalUseOnlyNoRedistribution")

    @Test fun `correlated PNG is current only after header length and bytes hash validation`() {
        val result = repository.fetch(transport(), app, authority) as HostCoverFetchResult.Current
        assertEquals(UUID, result.cover.appUuid)
        assertArrayEquals(PNG, result.cover.bytes)
    }

    @Test fun `mismatch rejects new bytes and only retains independently verified stale bytes`() {
        val old = VerifiedHostCover(UUID, SHA, PNG)
        val mismatch = repository.fetch(transport(headerUuid = "11111111-1111-4111-8111-111111111111"), app, authority, old)
            as HostCoverFetchResult.Rejected
        assertEquals(HostCoverIssue.INVALID_APP_UUID, mismatch.issue)
        assertEquals(old, mismatch.stale)
        val corruptOld = old.copy(bytes = byteArrayOf(1, 2, 3))
        val rejected = repository.fetch(transport(contentType = "image/jpeg"), app, authority, corruptOld)
            as HostCoverFetchResult.Rejected
        assertNull(rejected.stale)
    }

    @Test fun `length SHA and PNG failures are fail closed`() {
        val cases = listOf(
            transport(length = "67") to HostCoverIssue.INVALID_LENGTH,
            transport(headerSha = "a".repeat(64)) to HostCoverIssue.INVALID_HEADER_SHA,
            transport(body = byteArrayOf(1, 2, 3), length = "3", headerSha = SHA) to HostCoverIssue.BODY_SHA_MISMATCH,
        )
        cases.forEach { (wire, issue) ->
            val result = repository.fetch(wire, app, authority) as HostCoverFetchResult.Rejected
            assertEquals(issue, result.issue)
            assertTrue(result.stale == null)
        }
    }

    @Test fun `stored cache is reverified against exact authority and bytes`() {
        assertEquals(UUID, repository.verifyStored(authority, PNG)?.appUuid)
        assertNull(repository.verifyStored(authority.copy(expectedSha256 = "a".repeat(64)), PNG))
        assertNull(repository.verifyStored(authority, PNG.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }))
    }

    private fun transport(
        contentType: String = "image/png",
        length: String = PNG.size.toString(),
        headerUuid: String = UUID,
        headerSha: String = SHA,
        body: ByteArray = PNG,
    ) = HostAppAssetTransport { HostAppAssetWireResponse(contentType, length, headerUuid, headerSha, body) }

    companion object {
        private const val UUID = "2c42a3d0-79f1-4bb6-98f8-40c18cd5bc91"
        private const val SHA = "431ced6916a2a21a156e38701afe55bbd7f88969fbbfc56d7fe099d47f265460"
        private val PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    }
}
