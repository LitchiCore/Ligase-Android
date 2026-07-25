package com.limelight.ligase.feature.pairing.application

import com.limelight.ligase.feature.pairing.infrastructure.LegacyPairingResult
import com.limelight.ligase.feature.pairing.infrastructure.LegacyPairingTransport
import com.limelight.ligase.pairing.AttendedPairingViewModel
import com.limelight.nvstream.http.ComputerDetails
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

enum class HostPairingMode {
    ATTENDED,
    LEGACY_EXPLICIT_CONFIRMATION,
}

/**
 * Owns pairing mode selection and background orchestration, not UI.
 *
 * Exact attended v1 capability is authoritative. Every other Host remains on
 * the explicit legacy-confirmation path; this class never silently falls back.
 */
class HostPairingCoordinator(
    private val transport: LegacyPairingTransport,
    private val attendedViewModel: AttendedPairingViewModel,
    private val background: Executor = Executor { action ->
        Thread(action, "Ligase legacy pairing").start()
    },
    private val postToMain: ((() -> Unit) -> Unit),
    private val beforeLegacyPairing: () -> Unit = {},
) {
    private val legacyInFlight = AtomicBoolean(false)

    fun modeFor(host: ComputerDetails): HostPairingMode =
        if (
            host.ligaseAttendedPairingVersion == 1 &&
            !host.ligaseAttendedPairingPath.isNullOrBlank()
        ) {
            HostPairingMode.ATTENDED
        } else {
            HostPairingMode.LEGACY_EXPLICIT_CONFIRMATION
        }

    @Throws(IOException::class)
    fun startAttended(host: ComputerDetails, deviceName: String) {
        check(modeFor(host) == HostPairingMode.ATTENDED)
        val binding = attendedViewModel.createBinding()
        val coordinator = transport.createAttended(host, binding)
        attendedViewModel.start(coordinator, host.uuid, binding) {
            coordinator.start(
                capabilityPath = checkNotNull(host.ligaseAttendedPairingPath),
                hostUniqueId = host.uuid,
                deviceName = deviceName,
                clientCertificate = transport.clientCertificate(),
            )
        }
    }

    fun startLegacy(
        host: ComputerDetails,
        pin: String,
        onResult: (LegacyPairingResult) -> Unit,
    ): Boolean {
        check(modeFor(host) == HostPairingMode.LEGACY_EXPLICIT_CONFIRMATION)
        if (!legacyInFlight.compareAndSet(false, true)) return false
        background.execute {
            val result = try {
                beforeLegacyPairing()
                transport.pairLegacy(host, pin)
            } catch (error: Throwable) {
                LegacyPairingResult.TransportFailure(error.message)
            }
            postToMain {
                legacyInFlight.set(false)
                onResult(result)
            }
        }
        return true
    }
}
