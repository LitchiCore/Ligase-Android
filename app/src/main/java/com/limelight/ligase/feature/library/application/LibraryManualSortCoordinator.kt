package com.limelight.ligase.feature.library.application

import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.ligase.feature.library.data.repository.LigaseSyncRepository
import com.limelight.ligase.library.LibraryOperationGate
import com.limelight.ligase.library.LibrarySessionViewModel
import com.limelight.ligase.library.ManualLibrarySortAction
import com.limelight.ligase.library.ManualLibrarySortResult
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import java.util.concurrent.Executor

/**
 * Application owner for manual library order submission.
 *
 * Validation and wire parsing remain in [ManualLibrarySortAction], while
 * session single-flight and result state remain in [LibrarySessionViewModel].
 * Closing suppresses callbacks into a destroyed Activity but still settles the
 * retained session ticket when an already-started request completes.
 */
class LibraryManualSortCoordinator(
    private val session: LibrarySessionViewModel,
    private val action: ManualLibrarySortAction,
    private val repository: LigaseSyncRepository,
    private val httpFactory: (ComputerDetails) -> NvHTTP,
    private val background: Executor = Executor { task ->
        Thread(task, "Ligase manual library sort").start()
    },
    private val postToMain: ((() -> Unit) -> Unit),
    private val onRevisionConflict: (ComputerDetails) -> Unit = {},
    private val onPermissionDenied: (ComputerDetails) -> Unit = {},
    private val onSuccess: (ComputerDetails) -> Unit = {},
    private val onResult: (ComputerDetails, ManualLibrarySortResult) -> Unit = { _, _ -> },
) {
    private val lifecycleLock = Any()
    private var callbackGeneration = 0L
    private var closed = false

    fun submit(
        host: ComputerDetails,
        snapshot: LigaseSyncSnapshotDto,
        orderedAppUuids: List<String>,
    ): Boolean {
        if (
            !LibraryOperationGate.canOperate(
                session.state.connectivity,
                host.ligaseClientAccessMode,
            )
        ) {
            return false
        }
        val (generation, ticket) = synchronized(lifecycleLock) {
            if (closed) return false
            val ticket = session.beginManualSort(host.uuid) ?: return false
            callbackGeneration to ticket
        }
        background.execute {
            val result = action.submit(
                snapshot = snapshot,
                orderedAppUuids = orderedAppUuids,
                writer = { request ->
                    repository.updateManualOrder(httpFactory(host), request)
                },
            )
            postToMain {
                val acceptedResult =
                    if (
                        result is ManualLibrarySortResult.Success &&
                        !session.applyManualOrder(host.uuid, result.response)
                    ) {
                        ManualLibrarySortResult.Failed
                    } else {
                        result
                    }
                if (!session.acceptManualSort(ticket, acceptedResult)) return@postToMain
                val callbacksAllowed = synchronized(lifecycleLock) {
                    !closed && callbackGeneration == generation
                }
                if (!callbacksAllowed) return@postToMain
                when (acceptedResult) {
                    is ManualLibrarySortResult.Success -> onSuccess(host)
                    is ManualLibrarySortResult.RevisionConflict ->
                        onRevisionConflict(host)
                    ManualLibrarySortResult.PermissionDenied ->
                        onPermissionDenied(host)
                    ManualLibrarySortResult.InvalidOrder,
                    ManualLibrarySortResult.Failed,
                    -> Unit
                }
                onResult(host, acceptedResult)
            }
        }
        return true
    }

    fun close() {
        synchronized(lifecycleLock) {
            closed = true
            callbackGeneration++
        }
    }
}
