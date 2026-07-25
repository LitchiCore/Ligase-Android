package com.limelight.ligase.feature.host.application

import com.limelight.ligase.endpoint.LigaseEndpoint
import com.limelight.ligase.feature.host.infrastructure.LegacyComputerRegistryTransport
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager.PairState
import java.io.Closeable
import java.util.concurrent.Executor

enum class HostClickAction {
    IGNORE,
    WAKE,
    PAIR,
    OPEN,
}

sealed interface HostWakeResult {
    data object Sent : HostWakeResult
    data object MissingMacAddress : HostWakeResult
    data object Failed : HostWakeResult
}

sealed interface HostAddResult {
    data class Added(val details: ComputerDetails) : HostAddResult
    data object ManagerUnavailable : HostAddResult
    data object Failed : HostAddResult
}

/**
 * Coordinates Host discovery and registry mutations without owning UI or navigation.
 *
 * Poll generations reject callbacks after stop/restart or Activity destruction.
 * Registry mutations still use the concrete legacy binder adapter.
 */
class HostEndpointCoordinator(
    private val transport: LegacyComputerRegistryTransport,
    private val background: Executor = Executor { action ->
        Thread(action, "Ligase Host endpoint").start()
    },
    private val postToMain: ((() -> Unit) -> Unit),
) : Closeable {
    private val lock = Any()
    private var foreground = false
    private var polling = false
    private var closed = false
    private var generation = 0L
    private var onHostUpdate: ((ComputerDetails) -> Unit)? = null
    private var addInFlight = false

    fun actionFor(host: ComputerDetails): HostClickAction = when {
        host.state == ComputerDetails.State.UNKNOWN -> HostClickAction.IGNORE
        host.state == ComputerDetails.State.OFFLINE -> HostClickAction.WAKE
        host.pairState != PairState.PAIRED -> HostClickAction.PAIR
        else -> HostClickAction.OPEN
    }

    fun defaultHost(hosts: List<ComputerDetails>): ComputerDetails? =
        hosts.firstOrNull {
            it.state == ComputerDetails.State.ONLINE && it.pairState == PairState.PAIRED
        }

    fun onForeground(onUpdate: (ComputerDetails) -> Unit) {
        synchronized(lock) {
            if (closed) return
            foreground = true
            onHostUpdate = onUpdate
        }
        startUpdates()
    }

    fun onTransportAvailable(onUpdate: (ComputerDetails) -> Unit) {
        synchronized(lock) {
            if (closed) return
            onHostUpdate = onUpdate
        }
        startUpdates()
    }

    fun onTransportUnavailable() {
        synchronized(lock) {
            generation++
            polling = false
        }
    }

    fun onBackground() {
        synchronized(lock) { foreground = false }
        stopUpdates(wait = false)
    }

    fun startUpdates(): Boolean {
        val ticket = synchronized(lock) {
            if (closed || !foreground || polling || !transport.isAvailable()) return false
            (++generation).also { polling = true }
        }
        val started = transport.startPolling { details ->
            postToMain {
                val callback = synchronized(lock) {
                    if (closed || !polling || generation != ticket) null else onHostUpdate
                }
                callback?.invoke(details)
            }
        }
        if (!started) {
            synchronized(lock) {
                if (generation == ticket) polling = false
            }
        }
        return started
    }

    fun stopUpdates(wait: Boolean) {
        val shouldStop = synchronized(lock) {
            if (!polling) return
            polling = false
            generation++
            true
        }
        if (shouldStop) transport.stopPolling(wait)
    }

    fun wake(host: ComputerDetails, onResult: (HostWakeResult) -> Unit): Boolean {
        if (host.macAddress == null) {
            onResult(HostWakeResult.MissingMacAddress)
            return false
        }
        val ticket = synchronized(lock) {
            if (closed) return false
            generation
        }
        background.execute {
            val result = try {
                transport.wake(host)
                HostWakeResult.Sent
            } catch (_: Exception) {
                HostWakeResult.Failed
            }
            postToMain {
                if (accepts(ticket)) onResult(result)
            }
        }
        return true
    }

    fun add(endpoint: LigaseEndpoint, onResult: (HostAddResult) -> Unit): Boolean {
        val ticket = synchronized(lock) {
            if (closed || addInFlight) return false
            if (!transport.isAvailable()) {
                onResult(HostAddResult.ManagerUnavailable)
                return false
            }
            addInFlight = true
            generation
        }
        background.execute {
            val (added, details) = transport.add(endpoint)
            postToMain {
                val accepted = synchronized(lock) {
                    addInFlight = false
                    !closed && generation == ticket
                }
                if (accepted) {
                    onResult(
                        if (added) HostAddResult.Added(details) else HostAddResult.Failed,
                    )
                }
            }
        }
        return true
    }

    fun remove(host: ComputerDetails): Boolean = transport.remove(host)

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            foreground = false
            generation++
            addInFlight = false
        }
        stopUpdates(wait = false)
    }

    private fun accepts(ticket: Long): Boolean =
        synchronized(lock) { !closed && generation == ticket }

    internal fun isPollingForTest(): Boolean = synchronized(lock) { polling }
}
