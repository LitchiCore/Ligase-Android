package com.limelight.ligase.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.limelight.nvstream.http.NvApp
import java.util.Locale

enum class LibraryConnectivity {
    UNKNOWN,
    CHECKING,
    ONLINE,
    OFFLINE,
}

enum class LibrarySessionError {
    INCOMPATIBLE,
    PERMISSION_DENIED,
    SYNC_FAILED,
    APPLIST_FAILED,
}

data class LibraryContentSnapshot(
    val sync: LigaseSyncSnapshotDto,
    val transportApps: List<NvApp>,
    val rawAppList: String,
    val items: List<LigaseLibraryItem>,
    val hdr: LibraryHdrState,
)

data class LibrarySessionState(
    val hostKey: String? = null,
    val hostDisplayName: String? = null,
    val content: LibraryContentSnapshot? = null,
    val connectivity: LibraryConnectivity = LibraryConnectivity.UNKNOWN,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: LibrarySessionError? = null,
) {
    val status: LigaseLibraryStatus
        get() = when {
            initialLoading -> LigaseLibraryStatus.LOADING
            error == LibrarySessionError.INCOMPATIBLE -> LigaseLibraryStatus.INCOMPATIBLE
            error == LibrarySessionError.PERMISSION_DENIED -> LigaseLibraryStatus.PERMISSION_ERROR
            error != null -> LigaseLibraryStatus.SYNC_ERROR
            content != null -> LigaseLibraryStatus.READY
            else -> LigaseLibraryStatus.IDLE
        }
}

object LibraryOperationGate {
    fun canOperate(
        connectivity: LibraryConnectivity,
        accessMode: String?,
    ): Boolean = connectivity == LibraryConnectivity.ONLINE && accessMode == "operate"
}

/**
 * Owns the selected Host's last successful library snapshot and request lifecycle.
 *
 * Connectivity, loading, refreshing, and errors are independent from [LibraryContentSnapshot],
 * so transient polling and refresh failures never erase already rendered content.
 */
class LibrarySessionStore(
    private val refreshCoordinator: LibraryRefreshCoordinator = LibraryRefreshCoordinator(),
) {
    var state: LibrarySessionState = LibrarySessionState()
        private set

    fun selectHost(
        hostUniqueId: String,
        displayName: String = hostUniqueId,
    ): Boolean {
        val normalized = hostUniqueId.normalizedHostKey()
        if (normalized == state.hostKey) {
            refreshCoordinator.selectHost(hostUniqueId)
            if (state.hostDisplayName != displayName) {
                state = state.copy(hostDisplayName = displayName)
            }
            return false
        }
        refreshCoordinator.selectHost(hostUniqueId)
        state = LibrarySessionState(
            hostKey = normalized,
            hostDisplayName = displayName,
            initialLoading = true,
        )
        return true
    }

    fun clearHost() {
        refreshCoordinator.selectHost(null)
        state = LibrarySessionState()
    }

    fun updateConnectivity(
        hostUniqueId: String,
        connectivity: LibraryConnectivity,
    ): Boolean = mutateCurrent(hostUniqueId) {
        copy(
            connectivity = connectivity,
            initialLoading = if (connectivity == LibraryConnectivity.OFFLINE) {
                false
            } else {
                initialLoading
            },
        )
    }

    fun markIncompatible(hostUniqueId: String): Boolean = mutateCurrent(hostUniqueId) {
        copy(
            initialLoading = false,
            refreshing = false,
            error = LibrarySessionError.INCOMPATIBLE,
        )
    }.also {
        if (it) refreshCoordinator.cancel()
    }

    fun beginRefresh(hostUniqueId: String): LibraryRefreshCoordinator.Ticket? {
        if (!matches(hostUniqueId)) return null
        val ticket = refreshCoordinator.begin(
            hostUniqueId = hostUniqueId,
            preservesContent = state.content != null,
        ) ?: return null
        state = if (ticket.preservesContent) {
            state.copy(refreshing = true, error = null)
        } else {
            state.copy(initialLoading = true, refreshing = false, error = null)
        }
        return ticket
    }

    fun acceptSuccess(
        ticket: LibraryRefreshCoordinator.Ticket,
        content: LibraryContentSnapshot,
    ): Boolean {
        if (!refreshCoordinator.accept(ticket)) return false
        state = state.copy(
            content = content,
            initialLoading = false,
            refreshing = false,
            error = null,
        )
        return true
    }

    fun acceptFailure(
        ticket: LibraryRefreshCoordinator.Ticket,
        error: LibrarySessionError,
    ): Boolean {
        if (!refreshCoordinator.accept(ticket)) return false
        state = state.copy(
            initialLoading = false,
            refreshing = false,
            error = error,
        )
        return true
    }

    fun updateAppList(
        hostUniqueId: String,
        rawAppList: String,
        transportApps: List<NvApp>,
        items: List<LigaseLibraryItem>,
    ): Boolean = mutateCurrent(hostUniqueId) {
        val currentContent = content ?: return@mutateCurrent this
        copy(
            content = currentContent.copy(
                rawAppList = rawAppList,
                transportApps = transportApps.toList(),
                items = items.toList(),
            ),
            initialLoading = false,
            error = null,
        )
    }

    fun updateStreaming(
        hostUniqueId: String,
        streaming: LigaseStreamingSyncDto,
    ): Boolean = mutateCurrent(hostUniqueId) {
        val currentContent = content ?: return@mutateCurrent this
        copy(
            content = currentContent.copy(
                sync = currentContent.sync.copy(streaming = streaming),
            ),
        )
    }

    fun updateHdr(
        hostUniqueId: String,
        hdr: LibraryHdrState,
    ): Boolean = mutateCurrent(hostUniqueId) {
        val currentContent = content ?: return@mutateCurrent this
        copy(content = currentContent.copy(hdr = hdr))
    }

    fun applyManualOrder(
        hostUniqueId: String,
        response: ManualLibrarySortResponse,
    ): Boolean {
        if (!matches(hostUniqueId)) return false
        val currentContent = state.content ?: return false
        val expected = ManualLibrarySortValidator.expectedPublishedAppUuids(currentContent.sync)
            ?: return false
        if (
            response.sortMode != HostSortMode.MANUAL.wireValue ||
            !ManualLibrarySortValidator.isSafeRevision(response.revision) ||
            response.orderedAppUuids.toSet() != expected.toSet() ||
            response.orderedAppUuids.size != expected.size
        ) {
            return false
        }

        val publishedDtos = currentContent.sync.library.items
            .filter { it.publishedToClients != false }
            .associateBy(HostLibraryItemDto::id)
        val unpublishedDtos = currentContent.sync.library.items
            .filter { it.publishedToClients == false }
        val orderedDtos = response.orderedAppUuids.map { uuid ->
            publishedDtos[uuid] ?: return false
        }

        val publishedItems = currentContent.items
            .associateBy { it.hostAppUuid?.lowercase(Locale.ROOT) }
        val orderedItems = response.orderedAppUuids.map { uuid ->
            publishedItems[uuid] ?: return false
        }
        state = state.copy(
            content = currentContent.copy(
                sync = currentContent.sync.copy(
                    library = currentContent.sync.library.copy(
                        revision = response.revision,
                        sortMode = response.sortMode,
                        items = orderedDtos + unpublishedDtos,
                    ),
                ),
                items = orderedItems,
            ),
            error = null,
        )
        return true
    }

    fun markAppListFailure(hostUniqueId: String): Boolean = mutateCurrent(hostUniqueId) {
        copy(
            initialLoading = false,
            error = LibrarySessionError.APPLIST_FAILED,
        )
    }

    fun cancelRefresh() {
        refreshCoordinator.cancel()
        state = state.copy(
            initialLoading = false,
            refreshing = false,
        )
    }

    val inFlight: Boolean
        get() = refreshCoordinator.inFlight

    private fun mutateCurrent(
        hostUniqueId: String,
        transform: LibrarySessionState.() -> LibrarySessionState,
    ): Boolean {
        if (!matches(hostUniqueId)) return false
        state = state.transform()
        return true
    }

    private fun matches(hostUniqueId: String): Boolean =
        state.hostKey == hostUniqueId.normalizedHostKey()

    private fun String.normalizedHostKey(): String = trim().lowercase(Locale.ROOT)
}

class LibrarySessionViewModel : ViewModel() {
    private val store = LibrarySessionStore()
    private val manualSortCoordinator = ManualLibrarySortCoordinator()

    var state by mutableStateOf(store.state)
        private set

    var manualSortState by mutableStateOf(manualSortCoordinator.state)
        private set

    fun selectHost(
        hostUniqueId: String,
        displayName: String = hostUniqueId,
    ): Boolean {
        manualSortCoordinator.selectHost(hostUniqueId)
        publishManualSort()
        return store.selectHost(hostUniqueId, displayName).also { publish() }
    }

    fun clearHost() {
        manualSortCoordinator.selectHost(null)
        publishManualSort()
        store.clearHost()
        publish()
    }

    fun updateConnectivity(hostUniqueId: String, connectivity: LibraryConnectivity): Boolean =
        store.updateConnectivity(hostUniqueId, connectivity).also { publish() }

    fun markIncompatible(hostUniqueId: String): Boolean =
        store.markIncompatible(hostUniqueId).also { publish() }

    fun beginRefresh(hostUniqueId: String): LibraryRefreshCoordinator.Ticket? =
        store.beginRefresh(hostUniqueId).also { publish() }

    fun acceptSuccess(
        ticket: LibraryRefreshCoordinator.Ticket,
        content: LibraryContentSnapshot,
    ): Boolean = store.acceptSuccess(ticket, content).also { publish() }

    fun acceptFailure(
        ticket: LibraryRefreshCoordinator.Ticket,
        error: LibrarySessionError,
    ): Boolean = store.acceptFailure(ticket, error).also { publish() }

    fun updateAppList(
        hostUniqueId: String,
        rawAppList: String,
        transportApps: List<NvApp>,
        items: List<LigaseLibraryItem>,
    ): Boolean = store.updateAppList(
        hostUniqueId,
        rawAppList,
        transportApps,
        items,
    ).also { publish() }

    fun updateStreaming(
        hostUniqueId: String,
        streaming: LigaseStreamingSyncDto,
    ): Boolean = store.updateStreaming(hostUniqueId, streaming).also { publish() }

    fun updateHdr(hostUniqueId: String, hdr: LibraryHdrState): Boolean =
        store.updateHdr(hostUniqueId, hdr).also { publish() }

    fun applyManualOrder(
        hostUniqueId: String,
        response: ManualLibrarySortResponse,
    ): Boolean = store.applyManualOrder(hostUniqueId, response).also { publish() }

    fun beginManualSort(hostUniqueId: String): ManualLibrarySortCoordinator.Ticket? =
        manualSortCoordinator.begin(hostUniqueId).also { publishManualSort() }

    fun acceptManualSort(
        ticket: ManualLibrarySortCoordinator.Ticket,
        result: ManualLibrarySortResult,
    ): Boolean = manualSortCoordinator.accept(ticket, result).also { publishManualSort() }

    fun markAppListFailure(hostUniqueId: String): Boolean =
        store.markAppListFailure(hostUniqueId).also { publish() }

    fun cancelRefresh() {
        store.cancelRefresh()
        publish()
    }

    val inFlight: Boolean
        get() = store.inFlight

    private fun publish() {
        state = store.state
    }

    private fun publishManualSort() {
        manualSortState = manualSortCoordinator.state
    }

    override fun onCleared() {
        store.cancelRefresh()
    }
}

private fun HostLibraryKind?.systemRank(): Int = when (this) {
    HostLibraryKind.DESKTOP -> 0
    HostLibraryKind.VIRTUAL_DESKTOP -> 1
    else -> 2
}
