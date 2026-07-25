package com.limelight.ligase.pairing

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class AttendedPairingViewModel : ViewModel() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var coordinator: AttendedPairingCoordinator? = null
    private var nextGeneration = 0L
    private var activeGeneration = 0L
    var targetHostUuid: String? = null
        private set

    var state by mutableStateOf<AttendedPairingUiState>(AttendedPairingUiState.Idle)
        private set

    internal var lastAudit: AttendedPairingAudit? = null
        private set

    internal class Binding internal constructor(
        internal val generation: Long,
        val stateListener: (AttendedPairingUiState) -> Unit,
        val auditListener: (AttendedPairingAudit) -> Unit,
    )

    internal fun createBinding(): Binding {
        val generation = ++nextGeneration
        return Binding(
            generation = generation,
            stateListener = { update(generation, it) },
            auditListener = { updateAudit(generation, it) },
        )
    }

    private fun update(generation: Long, newState: AttendedPairingUiState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (generation == activeGeneration) state = newState
        } else {
            mainHandler.post {
                if (generation == activeGeneration) state = newState
            }
        }
    }

    private fun updateAudit(generation: Long, audit: AttendedPairingAudit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (generation == activeGeneration) lastAudit = audit
        } else {
            mainHandler.post {
                if (generation == activeGeneration) lastAudit = audit
            }
        }
    }

    internal fun start(
        newCoordinator: AttendedPairingCoordinator,
        hostUuid: String,
        binding: Binding,
        start: () -> Unit,
    ) {
        if (state is AttendedPairingUiState.Creating ||
            state is AttendedPairingUiState.Waiting ||
            state is AttendedPairingUiState.Finishing
        ) {
            newCoordinator.close()
            return
        }
        coordinator?.close()
        coordinator = newCoordinator
        activeGeneration = binding.generation
        targetHostUuid = hostUuid
        lastAudit = null
        start()
    }

    fun cancel() = coordinator?.cancelByUser()

    fun dismissStopped() {
        if (state is AttendedPairingUiState.Stopped) state = AttendedPairingUiState.Idle
    }

    fun markCompletedHandled() {
        if (state == AttendedPairingUiState.Completed) {
            state = AttendedPairingUiState.Idle
            targetHostUuid = null
        }
    }

    override fun onCleared() {
        activeGeneration = ++nextGeneration
        coordinator?.close()
        coordinator = null
    }
}
