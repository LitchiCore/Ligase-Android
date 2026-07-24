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
    var targetHostUuid: String? = null
        private set

    var state by mutableStateOf<AttendedPairingUiState>(AttendedPairingUiState.Idle)
        private set

    internal var lastAudit: AttendedPairingAudit? = null
        private set

    internal fun update(newState: AttendedPairingUiState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            state = newState
        } else {
            mainHandler.post { state = newState }
        }
    }

    internal fun updateAudit(audit: AttendedPairingAudit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lastAudit = audit
        } else {
            mainHandler.post { lastAudit = audit }
        }
    }

    internal fun start(
        newCoordinator: AttendedPairingCoordinator,
        hostUuid: String,
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
        coordinator?.close()
        coordinator = null
    }
}
