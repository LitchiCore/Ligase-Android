package com.limelight.ligase.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.limelight.ligase.feature.library.data.dto.LigaseStreamingSyncDto
import com.limelight.ligase.feature.library.data.dto.ManualLibrarySortResponse
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.nvstream.http.NvApp

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
        accessMode: String? = null,
    ): Boolean {
        manualSortCoordinator.selectHost(hostUniqueId)
        publishManualSort()
        return store.selectHost(hostUniqueId, displayName, accessMode).also { publish() }
    }

    fun clearHost() {
        manualSortCoordinator.selectHost(null)
        publishManualSort()
        store.clearHost()
        publish()
    }

    fun updateConnectivity(hostUniqueId: String, connectivity: LibraryConnectivity): Boolean =
        store.updateConnectivity(hostUniqueId, connectivity).also { publish() }

    fun updateHostAuthority(
        hostUniqueId: String,
        connectivity: LibraryConnectivity,
        accessMode: String?,
    ): Boolean = store.updateHostAuthority(hostUniqueId, connectivity, accessMode).also { publish() }

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
