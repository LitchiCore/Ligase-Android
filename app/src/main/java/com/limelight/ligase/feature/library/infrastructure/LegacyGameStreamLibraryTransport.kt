package com.limelight.ligase.feature.library.infrastructure

import android.content.Context
import android.graphics.BitmapFactory
import com.limelight.R
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerService
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.grid.assets.MemoryAssetLoader
import com.limelight.grid.assets.NetworkAssetLoader
import com.limelight.ligase.feature.library.data.repository.LigaseSyncRepository
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LigaseLibraryAdapter
import com.limelight.ligase.library.LibraryContentSnapshot
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.utils.ServerHelper
import java.io.IOException
import java.io.StringReader

/**
 * The only library-facing adapter that knows the legacy GameStream Java ABI.
 *
 * Pairing, launch, RTSP, media, and input remain outside this adapter.
 */
open class LegacyGameStreamLibraryTransport private constructor(
    private val context: Context?,
    private val binderProvider: () -> ComputerManagerService.ComputerManagerBinder?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    constructor(
        context: Context,
        binderProvider: () -> ComputerManagerService.ComputerManagerBinder?,
    ) : this(context.applicationContext, binderProvider, Unit)

    internal constructor() : this(null, { null }, Unit)

    open fun createHttp(host: ComputerDetails): NvHTTP {
        val appContext = checkNotNull(context)
        val binder = binderProvider() ?: throw IOException("Computer manager is unavailable")
        return NvHTTP(
            ServerHelper.getCurrentAddressFromComputer(host),
            host.httpsPort,
            binder.uniqueId,
            host.serverCert,
            PlatformBinding.getCryptoProvider(appContext),
        )
    }

    open fun fetchContent(
        host: ComputerDetails,
        syncPath: String,
        repository: LigaseSyncRepository,
        hdrState: (Boolean?) -> LibraryHdrState,
    ): LibraryContentSnapshot {
        val http = createHttp(host)
        val snapshot = repository.fetch(http, syncPath)
        val rawAppList = http.appListRaw
        val transportApps = parseAppList(rawAppList)
        return LibraryContentSnapshot(
            sync = snapshot,
            transportApps = transportApps,
            rawAppList = rawAppList,
            items = LigaseLibraryAdapter.fromSyncSnapshot(snapshot, transportApps),
            hdr = hdrState(snapshot.capabilities.hdrEncodingSupported),
        )
    }

    open fun parseAppList(rawAppList: String): List<NvApp> =
        NvHTTP.getAppListByReader(StringReader(rawAppList)).toList()

    open fun createAssetLoader(host: ComputerDetails): CachedAppAssetLoader {
        val appContext = checkNotNull(context)
        val binder = binderProvider() ?: throw IOException("Computer manager is unavailable")
        return CachedAppAssetLoader(
            host,
            1.0,
            NetworkAssetLoader(appContext, binder.uniqueId),
            MemoryAssetLoader(),
            DiskAssetLoader(appContext),
            BitmapFactory.decodeResource(appContext.resources, R.drawable.no_app_image),
        )
    }

    open fun createAppListPoller(
        host: ComputerDetails,
    ): ComputerManagerService.ApplistPoller? =
        binderProvider()?.createAppListPoller(host)
}
