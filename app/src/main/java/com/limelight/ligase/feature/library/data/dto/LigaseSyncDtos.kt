package com.limelight.ligase.feature.library.data.dto

data class HostLibraryItemDto(
    val id: String,
    val kind: String,
    val name: String,
    val steamAppId: Long?,
    val addedAt: String,
    val updatedAt: String,
    val lastPlayedAt: String?,
    val system: Boolean,
    val publishedToClients: Boolean? = null,
)

data class LigaseSyncSnapshotDto(
    val schemaVersion: Int,
    val capabilities: LigaseCapabilitiesDto,
    val library: LigaseLibrarySyncDto,
    val streaming: LigaseStreamingSyncDto,
)

data class LigaseCapabilitiesDto(
    val hdrEncodingSupported: Boolean,
)

data class LigaseLibrarySyncDto(
    val revision: Long,
    val updatedAt: String,
    val sortMode: String,
    val items: List<HostLibraryItemDto>,
)

data class LigaseResolutionDto(
    val width: Int,
    val height: Int,
) {
    val label: String
        get() = "${width} × ${height}"

    fun isValid(): Boolean = width in 320..16384 && height in 240..16384
}

data class LigaseStreamingSyncDto(
    val schemaVersion: Int,
    val revision: Long,
    val updatedAt: String,
    val globalResolution: LigaseResolutionDto,
    val apps: Map<String, LigaseAppStreamingDto>,
) {
    fun resolutionFor(appUuid: String): LigaseResolutionDto =
        apps.entries.firstOrNull { it.key.equals(appUuid, ignoreCase = true) }
            ?.value
            ?.resolution
            ?: globalResolution

    fun overrideFor(appUuid: String): LigaseResolutionDto? =
        apps.entries.firstOrNull { it.key.equals(appUuid, ignoreCase = true) }
            ?.value
            ?.resolution
}

data class LigaseAppStreamingDto(
    val resolution: LigaseResolutionDto?,
)

data class LigaseGlobalResolutionWriteDto(
    val baseRevision: Long,
    val globalResolution: LigaseResolutionDto,
)

data class LigaseAppResolutionWriteDto(
    val baseRevision: Long,
    val app: LigaseAppResolutionValueDto,
)

data class LigaseAppResolutionValueDto(
    val id: String,
    val resolution: LigaseResolutionDto?,
)

data class ManualLibrarySortRequest(
    val baseRevision: Long,
    val orderedAppUuids: List<String>,
)

data class ManualLibrarySortResponse(
    val revision: Long,
    val sortMode: String,
    val orderedAppUuids: List<String>,
)
