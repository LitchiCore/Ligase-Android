package com.limelight.ligase.stream

import java.util.concurrent.atomic.AtomicBoolean

class StreamSessionExitCoordinator(
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun disconnect()
        fun endSession()
    }

    private val exitRequested = AtomicBoolean()

    fun disconnect(): Boolean {
        if (!exitRequested.compareAndSet(false, true)) {
            return false
        }
        callbacks.disconnect()
        return true
    }

    fun endSession(): Boolean {
        if (!exitRequested.compareAndSet(false, true)) {
            return false
        }
        callbacks.endSession()
        return true
    }

    fun isExitRequested(): Boolean = exitRequested.get()
}
