package com.limelight.ligase.feature.library.data.repository

import com.limelight.ligase.feature.library.domain.HostCoverAuthority
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import java.security.MessageDigest

enum class HostCoverIssue {
    INVALID_AUTHORITY, INVALID_CONTENT_TYPE, INVALID_LENGTH, INVALID_APP_UUID,
    INVALID_HEADER_SHA, BODY_SHA_MISMATCH, INVALID_PNG,
}

data class VerifiedHostCover(
    val appUuid: String,
    val contentSha256: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is VerifiedHostCover &&
        appUuid == other.appUuid && contentSha256 == other.contentSha256 && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = 31 * (31 * appUuid.hashCode() + contentSha256.hashCode()) + bytes.contentHashCode()
    override fun toString(): String = "VerifiedHostCover(identity=redacted,bytes=redacted)"
}

sealed interface HostCoverFetchResult {
    data class Current(val cover: VerifiedHostCover) : HostCoverFetchResult
    data class Rejected(
        val issue: HostCoverIssue,
        val stale: VerifiedHostCover? = null,
    ) : HostCoverFetchResult
}

data class HostAppAssetWireResponse(
    val contentType: String?,
    val contentLength: String?,
    val appUuid: String?,
    val coverSha256: String?,
    val body: ByteArray,
)

fun interface HostAppAssetTransport {
    fun fetch(app: NvApp): HostAppAssetWireResponse
}

class HostAppAssetRepository {
    fun fetch(
        http: NvHTTP,
        app: NvApp,
        authority: HostCoverAuthority,
        previous: VerifiedHostCover? = null,
    ): HostCoverFetchResult = fetch(
        HostAppAssetTransport { target ->
            http.getBoxArtAuthority(target, MAX_BYTES).let {
                HostAppAssetWireResponse(it.contentType, it.contentLength, it.appUuid, it.coverSha256, it.body)
            }
        },
        app,
        authority,
        previous,
    )

    fun fetch(
        transport: HostAppAssetTransport,
        app: NvApp,
        authority: HostCoverAuthority,
        previous: VerifiedHostCover? = null,
    ): HostCoverFetchResult {
        val stale = previous?.takeIf {
            it.appUuid == authority.appUuid &&
                it.contentSha256 == authority.expectedSha256 &&
                sha256(it.bytes) == authority.expectedSha256
        }
        val response = transport.fetch(app)
        val issue = validate(authority, response)
        if (issue != null) return HostCoverFetchResult.Rejected(issue, stale)
        return HostCoverFetchResult.Current(
            VerifiedHostCover(authority.appUuid, authority.expectedSha256, response.body.copyOf()),
        )
    }

    private fun validate(
        authority: HostCoverAuthority,
        response: HostAppAssetWireResponse,
    ): HostCoverIssue? {
        if (!UUID.matches(authority.appUuid) || !SHA.matches(authority.expectedSha256)) {
            return HostCoverIssue.INVALID_AUTHORITY
        }
        if (response.contentType != "image/png") return HostCoverIssue.INVALID_CONTENT_TYPE
        if (response.body.isEmpty() || response.body.size > MAX_BYTES ||
            response.contentLength != response.body.size.toString()
        ) return HostCoverIssue.INVALID_LENGTH
        if (response.appUuid != authority.appUuid) return HostCoverIssue.INVALID_APP_UUID
        if (response.coverSha256 != authority.expectedSha256 || !SHA.matches(response.coverSha256)) {
            return HostCoverIssue.INVALID_HEADER_SHA
        }
        if (sha256(response.body) != authority.expectedSha256) return HostCoverIssue.BODY_SHA_MISMATCH
        if (!response.body.copyOfRange(0, minOf(PNG.size, response.body.size)).contentEquals(PNG)) {
            return HostCoverIssue.INVALID_PNG
        }
        return null
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        private val SHA = Regex("^[0-9a-f]{64}$")
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}
