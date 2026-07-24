package com.limelight.ligase.feature.library.application

import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.data.dto.LigaseStreamingSyncDto
import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.ligase.feature.library.data.repository.LigaseSyncRepository
import com.limelight.ligase.library.LibrarySessionViewModel
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import java.util.Locale
import java.util.concurrent.Executor

sealed interface LibraryStreamingSettingsResult {
    data class Success(val streaming: LigaseStreamingSyncDto) :
        LibraryStreamingSettingsResult

    data object RevisionConflict : LibraryStreamingSettingsResult
    data object PermissionDenied : LibraryStreamingSettingsResult
    data class Failed(val error: Throwable) : LibraryStreamingSettingsResult
}

/**
 * Coordinates streaming-setting mutations without owning dialogs or navigation.
 *
 * Each Host/operation pair is single-flight. Completion is accepted only while
 * the same Host remains selected, so a late response cannot update another
 * Host's session or trigger a retry. Revision conflicts are surfaced exactly
 * once and are never replayed here.
 */
class LibraryStreamingSettingsCoordinator(
    private val session: LibrarySessionViewModel,
    private val repository: LigaseSyncRepository,
    private val httpFactory: (ComputerDetails) -> NvHTTP,
    private val background: Executor = Executor { action ->
        Thread(action, "Ligase streaming settings").start()
    },
    private val postToMain: ((() -> Unit) -> Unit),
    private val onRevisionConflict: (host: ComputerDetails) -> Unit = {},
    private val onPermissionDenied: (host: ComputerDetails) -> Unit = {},
    private val onResult: (host: ComputerDetails, result: LibraryStreamingSettingsResult) -> Unit,
) {
    private val lock = Any()
    private val inFlight = mutableMapOf<ScopedOperation, Long>()
    private var nextTicket = 1L

    fun updateGlobal(
        host: ComputerDetails,
        snapshot: LigaseSyncSnapshotDto,
        resolution: LigaseResolutionDto,
    ): Boolean = submit(host, OperationKey.Global) {
        repository.updateGlobalResolution(
            httpFactory(host),
            snapshot.streaming.revision,
            resolution,
        )
    }

    fun updateApp(
        host: ComputerDetails,
        snapshot: LigaseSyncSnapshotDto,
        appUuid: String,
        resolution: LigaseResolutionDto?,
    ): Boolean = submit(host, OperationKey.App(appUuid.lowercase(Locale.ROOT))) {
        repository.updateAppResolution(
            httpFactory(host),
            snapshot.streaming.revision,
            appUuid,
            resolution,
        )
    }

    private fun submit(
        host: ComputerDetails,
        operation: OperationKey,
        writer: () -> LigaseStreamingSyncDto,
    ): Boolean {
        val hostKey = host.uuid.normalizedHostKey()
        if (session.state.hostKey != hostKey) return false
        val scopedOperation = operation.forHost(hostKey)
        val ticket = synchronized(lock) {
            if (inFlight.containsKey(scopedOperation)) return false
            nextTicket++.also { inFlight[scopedOperation] = it }
        }
        background.execute {
            val result = try {
                LibraryStreamingSettingsResult.Success(writer())
            } catch (error: HostHttpResponseException) {
                when (error.errorCode) {
                    403 -> LibraryStreamingSettingsResult.PermissionDenied
                    409 -> LibraryStreamingSettingsResult.RevisionConflict
                    else -> LibraryStreamingSettingsResult.Failed(error)
                }
            } catch (error: Exception) {
                LibraryStreamingSettingsResult.Failed(error)
            }
            postToMain {
                val ownsTicket = synchronized(lock) {
                    if (inFlight[scopedOperation] != ticket) {
                        false
                    } else {
                        inFlight.remove(scopedOperation)
                        true
                    }
                }
                if (!ownsTicket || session.state.hostKey != hostKey) return@postToMain
                val acceptedResult =
                    if (
                        result is LibraryStreamingSettingsResult.Success &&
                        !session.updateStreaming(host.uuid, result.streaming)
                    ) {
                        return@postToMain
                    } else {
                        result
                    }
                when (acceptedResult) {
                    LibraryStreamingSettingsResult.RevisionConflict ->
                        onRevisionConflict(host)
                    LibraryStreamingSettingsResult.PermissionDenied ->
                        onPermissionDenied(host)
                    else -> Unit
                }
                onResult(host, acceptedResult)
            }
        }
        return true
    }

    private sealed interface OperationKey {
        data object Global : OperationKey
        data class App(val appUuid: String) : OperationKey
    }

    private data class ScopedOperation(
        val hostKey: String,
        val operation: OperationKey,
    )

    private fun OperationKey.forHost(hostKey: String): ScopedOperation =
        ScopedOperation(hostKey, this)

    private fun String.normalizedHostKey(): String = trim().lowercase(Locale.ROOT)
}
