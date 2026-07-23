package com.limelight.ligase.library

import java.util.Locale

/**
 * Owns refresh request identity only. Network work and visible library data stay in their
 * existing layers, so a future input-device refresh cannot accidentally share this state.
 */
class LibraryRefreshCoordinator {
    class Ticket internal constructor(
        internal val hostKey: String,
        internal val generation: Long,
        val preservesContent: Boolean,
    )

    private var selectedHostKey: String? = null
    private var generation = 0L
    private var activeTicket: Ticket? = null

    fun selectHost(hostUniqueId: String?) {
        val normalized = hostUniqueId?.normalizedHostKey()
        if (normalized == selectedHostKey) return
        selectedHostKey = normalized
        generation++
        activeTicket = null
    }

    fun begin(hostUniqueId: String, preservesContent: Boolean): Ticket? {
        val normalized = hostUniqueId.normalizedHostKey()
        if (normalized != selectedHostKey || activeTicket != null) return null
        return Ticket(normalized, ++generation, preservesContent).also {
            activeTicket = it
        }
    }

    fun accept(ticket: Ticket): Boolean {
        if (ticket != activeTicket || ticket.hostKey != selectedHostKey) return false
        activeTicket = null
        return true
    }

    fun cancel() {
        generation++
        activeTicket = null
    }

    val inFlight: Boolean
        get() = activeTicket != null

    private fun String.normalizedHostKey(): String = trim().lowercase(Locale.ROOT)
}
