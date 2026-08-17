package com.limelight.ligase.feature.library.application

import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.feature.library.data.repository.LigaseSyncRepository
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LigaseLibraryAdapter
import com.limelight.ligase.feature.library.infrastructure.LegacyGameStreamLibraryTransport
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.LibrarySessionError
import com.limelight.ligase.library.LibrarySessionViewModel
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.File
import java.util.concurrent.Executor

/**
 * Coordinates the selected Host's library snapshot with the legacy transport.
 *
 * Request identity and stale-result rejection remain owned by [LibrarySessionViewModel].
 */
class LibraryHostCoordinator(
    private val session: LibrarySessionViewModel,
    private val repository: LigaseSyncRepository,
    private val transport: LegacyGameStreamLibraryTransport,
    private val hdrState: (Boolean?) -> LibraryHdrState,
    private val background: Executor = Executor { action ->
        Thread(action, "Ligase library sync").start()
    },
    private val postToMain: ((() -> Unit) -> Unit),
    private val onAssetLoaderChanged: (CachedAppAssetLoader?) -> Unit = {},
    private val verifiedCoverCacheRoot: File? = null,
    private val onVerifiedCoverLoaderChanged: (HostVerifiedCoverLoader?) -> Unit = {},
    private val onRefreshAccepted: (preservedContent: Boolean) -> Unit = {},
    private val onRefreshFailed: (preservedContent: Boolean, error: Exception) -> Unit = { _, _ -> },
) {
    private var assetLoader: CachedAppAssetLoader? = null
    private var verifiedCoverLoader: HostVerifiedCoverLoader? = null
    private var appListPoller: com.limelight.computers.ComputerManagerService.ApplistPoller? = null

    fun selectHost(host: ComputerDetails): Boolean {
        val changed = session.selectHost(host.uuid, host.name)
        if (changed) disposeAssets()
        session.updateConnectivity(host.uuid, host.connectivity())
        if (assetLoader == null) {
            try {
                assetLoader = transport.createAssetLoader(host)
                onAssetLoaderChanged(assetLoader)
            } catch (_: IOException) {
                // Service reconnect will call selectHost again and retry without clearing content.
            }
        }
        if (verifiedCoverLoader == null && verifiedCoverCacheRoot != null) {
            verifiedCoverLoader = HostVerifiedCoverLoader(
                cacheRoot = verifiedCoverCacheRoot,
                http = { transport.createHttp(host) },
                postToMain = postToMain,
            ).also(onVerifiedCoverLoaderChanged)
        }
        return changed
    }

    fun clearHost() {
        stopAppListUpdates()
        session.clearHost()
        disposeAssets()
    }

    fun updateConnectivity(host: ComputerDetails) {
        session.updateConnectivity(host.uuid, host.connectivity())
    }

    fun fetch(host: ComputerDetails, force: Boolean): Boolean {
        val path = host.ligaseSyncPath
        if (
            host.ligaseSyncVersion != LigaseSyncRepository.SUPPORTED_SYNC_VERSION ||
            path.isNullOrBlank()
        ) {
            session.markIncompatible(host.uuid)
            return false
        }
        if (!force && session.state.content != null) return false
        val ticket = session.beginRefresh(host.uuid) ?: return false
        background.execute {
            try {
                val content = transport.fetchContent(host, path, repository, hdrState)
                postToMain {
                    if (session.acceptSuccess(ticket, content)) {
                        onRefreshAccepted(ticket.preservesContent)
                    }
                }
            } catch (error: Exception) {
                postToMain {
                    val stateError =
                        if (error is HostHttpResponseException && error.errorCode == 403) {
                            LibrarySessionError.PERMISSION_DENIED
                        } else {
                            LibrarySessionError.SYNC_FAILED
                        }
                    if (session.acceptFailure(ticket, stateError)) {
                        onRefreshFailed(ticket.preservesContent, error)
                    }
                }
            }
        }
        return true
    }

    fun updateAppList(host: ComputerDetails, rawAppList: String?): Boolean {
        val content = session.state.content ?: return false
        if (rawAppList == null || rawAppList == content.rawAppList) return false
        return try {
            val transportApps = transport.parseAppList(rawAppList)
            session.updateAppList(
                host.uuid,
                rawAppList,
                transportApps,
                LigaseLibraryAdapter.fromSyncSnapshot(content.sync, transportApps),
            )
        } catch (_: XmlPullParserException) {
            session.markAppListFailure(host.uuid)
            false
        } catch (_: IOException) {
            session.markAppListFailure(host.uuid)
            false
        }
    }

    fun startAppListUpdates(host: ComputerDetails, allowed: Boolean) {
        if (!allowed || appListPoller != null) return
        appListPoller = transport.createAppListPoller(host)?.also { it.start() }
    }

    fun stopAppListUpdates() {
        appListPoller?.stop()
        appListPoller = null
    }

    fun createHttp(host: ComputerDetails): NvHTTP = transport.createHttp(host)

    fun dispose() {
        stopAppListUpdates()
        disposeAssets()
    }

    private fun disposeAssets() {
        assetLoader?.cancelForegroundLoads()
        assetLoader?.cancelBackgroundLoads()
        assetLoader?.freeCacheMemory()
        assetLoader = null
        onAssetLoaderChanged(null)
        verifiedCoverLoader?.close()
        verifiedCoverLoader = null
        onVerifiedCoverLoaderChanged(null)
    }

    private fun ComputerDetails.connectivity(): LibraryConnectivity = when (state) {
        ComputerDetails.State.ONLINE -> LibraryConnectivity.ONLINE
        ComputerDetails.State.OFFLINE -> LibraryConnectivity.OFFLINE
        ComputerDetails.State.UNKNOWN -> LibraryConnectivity.CHECKING
    }
}
