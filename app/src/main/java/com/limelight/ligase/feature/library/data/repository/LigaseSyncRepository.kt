package com.limelight.ligase.feature.library.data.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.limelight.ligase.feature.library.data.dto.LigaseAppResolutionValueDto
import com.limelight.ligase.feature.library.data.dto.LigaseAppResolutionWriteDto
import com.limelight.ligase.feature.library.data.dto.LigaseGlobalResolutionWriteDto
import com.limelight.ligase.feature.library.data.dto.LigaseLibrarySyncDto
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.data.dto.LigaseStreamingSyncDto
import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.ligase.feature.library.data.dto.ManualLibrarySortRequest
import com.limelight.ligase.feature.library.data.dto.ManualLibrarySortResponse
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.library.ManualLibrarySortValidator
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import java.io.IOException

class LigaseSyncRepository(
    private val gson: Gson = GsonBuilder().serializeNulls().create(),
) {
    fun fetch(http: NvHTTP, advertisedPath: String): LigaseSyncSnapshotDto {
        val snapshot = gson.fromJson(
            http.getLigaseJson(advertisedPath),
            LigaseSyncSnapshotDto::class.java,
        ) ?: throw IOException("Ligase sync returned an empty snapshot")
        validateSnapshot(snapshot)
        return snapshot
    }

    fun updateGlobalResolution(
        http: NvHTTP,
        baseRevision: Long,
        resolution: LigaseResolutionDto,
    ): LigaseStreamingSyncDto {
        require(resolution.isValid()) { "Resolution is outside the Host contract" }
        return parseStreaming(
            http.postLigaseJson(
                STREAMING_PATH,
                gson.toJson(
                    LigaseGlobalResolutionWriteDto(
                        baseRevision = baseRevision,
                        globalResolution = resolution,
                    ),
                ),
            ),
        )
    }

    fun updateAppResolution(
        http: NvHTTP,
        baseRevision: Long,
        appUuid: String,
        resolution: LigaseResolutionDto?,
    ): LigaseStreamingSyncDto {
        require(resolution == null || resolution.isValid()) {
            "Resolution is outside the Host contract"
        }
        return parseStreaming(
            http.postLigaseJson(
                STREAMING_PATH,
                encodeAppResolutionWrite(baseRevision, appUuid, resolution),
            ),
        )
    }

    fun updateManualOrder(
        http: NvHTTP,
        request: ManualLibrarySortRequest,
    ): ManualLibrarySortResponse {
        require(ManualLibrarySortValidator.isSafeRevision(request.baseRevision)) {
            "Library revision is outside the Host contract"
        }
        require(
            request.orderedAppUuids.all(ManualLibrarySortValidator::isCanonicalUuid) &&
                request.orderedAppUuids.distinct().size == request.orderedAppUuids.size,
        ) {
            "Manual library order contains an invalid UUID sequence"
        }
        return ManualLibrarySortCodec.parseResponse(
            http.postLigaseJson(
                LIBRARY_SORT_PATH,
                ManualLibrarySortCodec.encodeRequest(request, gson),
            ),
            request.orderedAppUuids,
        )
    }

    internal fun encodeManualOrderWrite(request: ManualLibrarySortRequest): String =
        ManualLibrarySortCodec.encodeRequest(request, gson)

    internal fun encodeAppResolutionWrite(
        baseRevision: Long,
        appUuid: String,
        resolution: LigaseResolutionDto?,
    ): String = gson.toJson(
        LigaseAppResolutionWriteDto(
            baseRevision = baseRevision,
            app = LigaseAppResolutionValueDto(
                id = appUuid,
                resolution = resolution,
            ),
        ),
    )

    private fun parseStreaming(json: String): LigaseStreamingSyncDto {
        val streaming = gson.fromJson(json, LigaseStreamingSyncDto::class.java)
            ?: throw IOException("Ligase streaming update returned an empty response")
        validateStreaming(streaming)
        return streaming
    }

    private fun validateSnapshot(snapshot: LigaseSyncSnapshotDto) {
        if (snapshot.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw IOException("Unsupported Ligase sync schema ${snapshot.schemaVersion}")
        }
        validateLibrary(snapshot.library)
        validateStreaming(snapshot.streaming)
    }

    private fun validateLibrary(library: LigaseLibrarySyncDto) {
        if (HostSortMode.entries.none { it.wireValue == library.sortMode }) {
            throw IOException("Host returned an unsupported sort mode")
        }
    }

    private fun validateStreaming(streaming: LigaseStreamingSyncDto) {
        if (streaming.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw IOException("Unsupported Ligase streaming schema ${streaming.schemaVersion}")
        }
        if (!streaming.globalResolution.isValid()) {
            throw IOException("Host returned an invalid global resolution")
        }
        if (streaming.apps.values.any { it.resolution?.isValid() == false }) {
            throw IOException("Host returned an invalid application resolution")
        }
    }

    companion object {
        const val SUPPORTED_SYNC_VERSION = 1
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val STREAMING_PATH = "/ligase/v1/streaming"
        const val LIBRARY_SORT_PATH = "/ligase/v1/library/sort"

        fun isRevisionConflict(error: Throwable): Boolean =
            error is HostHttpResponseException && error.errorCode == 409
    }
}
