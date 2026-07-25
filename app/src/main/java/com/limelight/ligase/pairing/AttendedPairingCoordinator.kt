package com.limelight.ligase.pairing

import com.limelight.nvstream.http.PairingManager
import java.io.Closeable
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class AttendedPairingCoordinator(
    private val repository: AttendedPairingTransport,
    private val crypto: AttendedPairingCrypto,
    private val clock: () -> Long,
    private val legacyPairing: LegacyPairing,
    private val listener: (AttendedPairingUiState) -> Unit,
    private val auditListener: (AttendedPairingAudit) -> Unit = {},
) : Closeable {
    interface LegacyPairing {
        fun pair(
            pin: String,
            requestId: String,
            expectedCertificateSha256: ByteArray,
        ): PairingManager.PairState

        fun cancel()
        fun onPaired(certificate: X509Certificate?)
    }

    private val executor = Executors.newScheduledThreadPool(2)
    private val terminal = AtomicBoolean(false)
    private var poll: ScheduledFuture<*>? = null
    private var path: String? = null
    private var requestId: String? = null
    private var token: CharArray? = null
    private var privateKey: ByteArray? = null
    private var pairingKey: ByteArray? = null
    private var transcriptHash: ByteArray? = null
    private var deadline = 0L

    fun start(
        capabilityPath: String,
        hostUniqueId: String,
        deviceName: String,
        clientCertificate: X509Certificate,
    ) {
        check(path == null && !terminal.get())
        path = capabilityPath
        emit(AttendedPairingUiState.Creating)
        executor.execute {
            try {
                val keyPair = crypto.generateKeyPair()
                privateKey = keyPair.privateKey
                val clientNonce = crypto.randomBytes(32)
                val id = UUID.randomUUID().toString()
                requestId = id
                val request = AttendedCreateRequest(
                    requestId = id,
                    deviceName = deviceName.trim(),
                    clientEphemeralKey = AttendedPairingJson.canonicalBase64Url(keyPair.publicKey),
                    clientNonce = AttendedPairingJson.canonicalBase64Url(clientNonce),
                    clientCertificateSha256 = AttendedPairingJson.canonicalBase64Url(
                        crypto.certificateSha256(clientCertificate),
                    ),
                )
                val response = repository.create(capabilityPath, request)
                token = response.requestToken.toCharArray()
                if (terminal.get()) {
                    runCatching {
                        repository.cancel(capabilityPath, id, response.requestToken)
                    }
                    cleanup()
                    return@execute
                }
                require(canonicalUuid(response.hostUniqueId) == canonicalUuid(hostUniqueId)) {
                    "hostIdentityMismatch"
                }
                val material = crypto.derive(request, response, keyPair.privateKey, clientNonce)
                pairingKey = material.pairingKey
                transcriptHash = material.transcriptHash
                deadline = clock() + 120_000L
                val pin = PairingManager.generatePinString().toCharArray()
                val envelope = crypto.createEnvelope(pin, material.pairingKey, material.transcriptHash)
                repository.putEnvelope(capabilityPath, id, String(token!!), envelope)
                if (terminal.get()) {
                    cleanup()
                    return@execute
                }
                emit(
                    AttendedPairingUiState.Waiting(
                        deviceName,
                        material.safetyCode,
                        monotonicCountdown(),
                    ),
                )

                val expectedFingerprint =
                    AttendedPairingJson.decodeBase64Url(response.hostCertificateSha256, 32)
                executor.execute {
                    try {
                        val pairResult = legacyPairing.pair(
                            String(pin),
                            id,
                            expectedFingerprint,
                        )
                        if (pairResult == PairingManager.PairState.PAIRED && terminal.compareAndSet(false, true)) {
                            legacyPairing.onPaired(null)
                            val audit = buildAudit(AttendedPairingAudit.TerminalState.PAIRED)
                            cleanup()
                            audit?.let(auditListener)
                            emit(AttendedPairingUiState.Completed)
                        } else if (
                            pairResult != PairingManager.PairState.PAIRED &&
                            terminal.compareAndSet(false, true)
                        ) {
                            stop(StopReason.FAILED)
                        }
                    } finally {
                        Arrays.fill(expectedFingerprint, 0)
                        Arrays.fill(pin, '\u0000')
                    }
                }
                poll = executor.scheduleAtFixedRate(
                    ::pollOnce,
                    0,
                    1,
                    TimeUnit.SECONDS,
                )
                crypto.wipe(clientNonce, keyPair.publicKey)
            } catch (_: Throwable) {
                if (terminal.compareAndSet(false, true)) {
                    stop(StopReason.NETWORK)
                }
            }
        }
    }

    fun cancelByUser() {
        if (!terminal.compareAndSet(false, true)) return
        poll?.cancel(true)
        repository.cancelInFlight()
        legacyPairing.cancel()
        val localPath = path
        val localId = requestId
        val localToken = token?.concatToString()
        wipeNonProbeSecrets()
        executor.execute {
            var reason = StopReason.UNKNOWN
            if (localPath != null && localId != null && localToken != null) {
                try {
                    if (repository.cancel(localPath, localId, localToken)) {
                        reason = when (repository.status(localPath, localId, localToken).state) {
                            AttendedStatus.State.PAIRED -> StopReason.UNKNOWN
                            AttendedStatus.State.CANCELLED -> StopReason.CANCELLED
                            AttendedStatus.State.REJECTED -> StopReason.REJECTED
                            AttendedStatus.State.EXPIRED -> StopReason.EXPIRED
                            AttendedStatus.State.FAILED -> StopReason.FAILED
                            else -> StopReason.PROTOCOL
                        }
                    }
                } catch (_: Throwable) {
                    reason = StopReason.UNKNOWN
                } finally {
                    localToken.toCharArray().fill('\u0000')
                    wipeToken()
                }
            }
            val audit = buildAudit(reason.toAuditTerminalState())
            cleanup()
            audit?.let(auditListener)
            emit(AttendedPairingUiState.Stopped(reason))
        }
    }

    private fun pollOnce() {
        if (terminal.get()) return
        if (clock() >= deadline) {
            if (terminal.compareAndSet(false, true)) {
                legacyPairing.cancel()
                stop(StopReason.EXPIRED)
            }
            return
        }
        refreshCountdown()
        val localPath = path ?: return
        val localId = requestId ?: return
        val localToken = token?.concatToString() ?: return
        try {
            val status = repository.status(localPath, localId, localToken).state
            if (terminal.get()) return
            when (status) {
                AttendedStatus.State.PENDING -> Unit
                AttendedStatus.State.APPROVED ->
                    emit(
                        AttendedPairingUiState.Finishing(
                            currentSafetyCode(),
                            monotonicCountdown(),
                        ),
                    )
                AttendedStatus.State.PAIRED -> Unit
                AttendedStatus.State.REJECTED ->
                    finishTerminal(StopReason.REJECTED)
                AttendedStatus.State.CANCELLED ->
                    finishTerminal(StopReason.CANCELLED)
                AttendedStatus.State.EXPIRED ->
                    finishTerminal(StopReason.EXPIRED)
                AttendedStatus.State.FAILED ->
                    finishTerminal(StopReason.FAILED)
            }
        } catch (_: Throwable) {
            finishTerminal(StopReason.NETWORK)
        } finally {
            localToken.toCharArray().fill('\u0000')
        }
    }

    private var lastState: AttendedPairingUiState = AttendedPairingUiState.Idle
    private fun listenerState() = lastState

    private fun currentSafetyCode(): String = when (val state = listenerState()) {
        is AttendedPairingUiState.Waiting -> state.safetyCode
        is AttendedPairingUiState.Finishing -> state.safetyCode
        else -> ""
    }

    private fun monotonicCountdown(): PairingCountdown {
        val remainingMs = (deadline - clock()).coerceAtLeast(0L)
        val calculatedSeconds = (
            remainingMs / 1_000L +
                if (remainingMs % 1_000L == 0L) 0L else 1L
            ).coerceIn(0L, 120L)
        val priorSeconds = when (val state = listenerState()) {
            is AttendedPairingUiState.Waiting -> state.countdown.remainingSeconds
            is AttendedPairingUiState.Finishing -> state.countdown.remainingSeconds
            else -> 120
        }
        val seconds = minOf(calculatedSeconds.toInt(), priorSeconds)
        return PairingCountdown.of(seconds)
    }

    private fun refreshCountdown() {
        val countdown = monotonicCountdown()
        when (val state = listenerState()) {
            is AttendedPairingUiState.Waiting ->
                if (countdown != state.countdown) emit(state.copy(countdown = countdown))
            is AttendedPairingUiState.Finishing ->
                if (countdown != state.countdown) emit(state.copy(countdown = countdown))
            else -> Unit
        }
    }

    private fun finishTerminal(reason: StopReason) {
        if (!terminal.compareAndSet(false, true)) return
        legacyPairing.cancel()
        stop(reason)
    }

    private fun stop(reason: StopReason) {
        poll?.cancel(true)
        val audit = buildAudit(reason.toAuditTerminalState())
        cleanup()
        audit?.let(auditListener)
        emit(AttendedPairingUiState.Stopped(reason))
    }

    private fun buildAudit(
        terminalState: AttendedPairingAudit.TerminalState,
    ): AttendedPairingAudit? {
        val id = requestId ?: return null
        return AttendedPairingAudit(
            requestId = canonicalUuid(id),
            terminalState = terminalState,
            completedAtElapsedMs = clock(),
        )
    }

    private fun wipeNonProbeSecrets() {
        crypto.wipe(privateKey, pairingKey, transcriptHash)
        privateKey = null
        pairingKey = null
        transcriptHash = null
    }

    private fun wipeToken() {
        token?.fill('\u0000')
        token = null
    }

    private fun cleanup() {
        wipeNonProbeSecrets()
        wipeToken()
        poll?.cancel(true)
        poll = null
        requestId = null
        path = null
        deadline = 0L
    }

    internal fun hasOwnedSecretsForTest(): Boolean =
        token != null || privateKey != null || pairingKey != null || transcriptHash != null

    private fun emit(state: AttendedPairingUiState) {
        lastState = state
        listener(state)
    }

    override fun close() {
        if (!terminal.get()) cancelByUser()
        executor.shutdownNow()
        cleanup()
    }

    private fun StopReason.toAuditTerminalState(): AttendedPairingAudit.TerminalState = when (this) {
        StopReason.REJECTED -> AttendedPairingAudit.TerminalState.REJECTED
        StopReason.CANCELLED -> AttendedPairingAudit.TerminalState.CANCELLED
        StopReason.EXPIRED -> AttendedPairingAudit.TerminalState.EXPIRED
        StopReason.FAILED -> AttendedPairingAudit.TerminalState.FAILED
        StopReason.NETWORK -> AttendedPairingAudit.TerminalState.NETWORK
        StopReason.PROTOCOL -> AttendedPairingAudit.TerminalState.PROTOCOL
        StopReason.UNKNOWN -> AttendedPairingAudit.TerminalState.UNKNOWN
    }
}
