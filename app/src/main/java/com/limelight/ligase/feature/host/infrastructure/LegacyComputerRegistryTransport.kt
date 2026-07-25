package com.limelight.ligase.feature.host.infrastructure

import android.content.Context
import com.limelight.computers.ComputerManagerListener
import com.limelight.computers.ComputerManagerService
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.ligase.endpoint.LigaseEndpoint
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.wol.WakeOnLanSender
import java.io.IOException

/**
 * Concrete Host-management adapter around the legacy ComputerManager Java ABI.
 *
 * Database writes continue to flow exclusively through ComputerManagerBinder.
 */
open class LegacyComputerRegistryTransport private constructor(
    private val context: Context?,
    private val binderProvider: () -> ComputerManagerService.ComputerManagerBinder?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    constructor(
        context: Context,
        binderProvider: () -> ComputerManagerService.ComputerManagerBinder?,
    ) : this(context.applicationContext, binderProvider, Unit)

    internal constructor() : this(null, { null }, Unit)

    open fun isAvailable(): Boolean = binderProvider() != null

    open fun startPolling(onUpdate: (ComputerDetails) -> Unit): Boolean {
        val binder = binderProvider() ?: return false
        binder.startPolling(ComputerManagerListener(onUpdate))
        return true
    }

    open fun stopPolling(wait: Boolean) {
        val binder = binderProvider() ?: return
        binder.stopPolling()
        if (wait) binder.waitForPollingStopped()
    }

    open fun add(endpoint: LigaseEndpoint): Pair<Boolean, ComputerDetails> {
        val binder = binderProvider() ?: return false to ComputerDetails()
        val details = ComputerDetails().apply {
            endpoints = listOf(endpoint)
            manualAddress = endpoint.toLegacyAddressTuple()
        }
        val added = try {
            binder.addComputerBlocking(details)
        } catch (_: InterruptedException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        return added to details
    }

    open fun remove(host: ComputerDetails): Boolean {
        binderProvider()?.removeComputer(host)
        DiskAssetLoader(checkNotNull(context)).deleteAssetsForComputer(host.uuid)
        return true
    }

    @Throws(IOException::class)
    open fun wake(host: ComputerDetails) {
        WakeOnLanSender.sendWolPacket(host)
    }
}
