package com.limelight.ligase.pairing

import com.limelight.nvstream.http.PairingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AttendedPairingCoordinatorTest {
    @Test
    fun pairedFlowCreatesOnceAndCompletes() {
        val transport = FakeTransport(AttendedStatus.State.PENDING)
        val states = CopyOnWriteArrayList<AttendedPairingUiState>()
        val completed = CountDownLatch(1)
        val coordinator = coordinator(transport, { 0L }) {
            states += it
            if (it == AttendedPairingUiState.Completed) completed.countDown()
        }
        coordinator.start(PATH, HOST_ID, "Phone", certificate())
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals(1, transport.createCount.get())
        assertEquals(1, transport.envelopeCount.get())
        assertTrue(states.any { it is AttendedPairingUiState.Waiting })
        assertTrue(!coordinator.hasOwnedSecretsForTest())
        coordinator.close()
    }

    @Test
    fun terminalAuditRetainsOnlyCanonicalIdStateAndElapsedTime() {
        val transport = FakeTransport(AttendedStatus.State.PENDING)
        val audit = CopyOnWriteArrayList<AttendedPairingAudit>()
        val completed = CountDownLatch(1)
        val coordinator = coordinator(
            transport = transport,
            clock = { 42L },
            auditListener = {
                audit += it
                completed.countDown()
            },
        ) {}
        coordinator.start(PATH, HOST_ID, "Phone", certificate())
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals(1, audit.size)
        assertEquals(AttendedPairingAudit.TerminalState.PAIRED, audit.single().terminalState)
        assertEquals(42L, audit.single().completedAtElapsedMs)
        assertEquals(audit.single().requestId.lowercase(), audit.single().requestId)
        assertTrue(!coordinator.hasOwnedSecretsForTest())
        coordinator.close()
    }

    @Test
    fun rejectedStatusStopsHeldPairAndDoesNotReplay() {
        val transport = FakeTransport(AttendedStatus.State.REJECTED)
        val stopped = CountDownLatch(1)
        val legacy = BlockingLegacyPairing()
        val coordinator = coordinator(transport, { 0L }, legacy) {
            if (it is AttendedPairingUiState.Stopped) stopped.countDown()
        }
        coordinator.start(PATH, HOST_ID, "Tablet", certificate())
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        assertEquals(1, transport.createCount.get())
        assertEquals(1, transport.envelopeCount.get())
        assertTrue(legacy.cancelCount.get() >= 1)
        coordinator.close()
    }

    @Test
    fun localDeadlineUsesMonotonicClock() {
        val now = AtomicLong(0)
        val transport = FakeTransport(AttendedStatus.State.PENDING)
        val stopped = CountDownLatch(1)
        val expiredCount = AtomicInteger()
        val coordinator = coordinator(transport, now::get, BlockingLegacyPairing()) {
            if (it is AttendedPairingUiState.Waiting) now.set(121_000)
            if (it is AttendedPairingUiState.Stopped && it.reason == StopReason.EXPIRED) {
                expiredCount.incrementAndGet()
                stopped.countDown()
            }
        }
        coordinator.start(PATH, HOST_ID, "Phone", certificate())
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        assertEquals(1, transport.createCount.get())
        assertEquals(1, expiredCount.get())
        assertEquals(0, transport.statusCount.get())
        coordinator.close()
    }

    @Test
    fun countdownStartsImmediatelyAndOnlyMovesDownForSameDeadline() {
        val now = AtomicLong(0)
        val transport = FakeTransport(AttendedStatus.State.PENDING)
        val countdowns = CopyOnWriteArrayList<Int>()
        val twoTicks = CountDownLatch(2)
        lateinit var coordinator: AttendedPairingCoordinator
        coordinator = coordinator(transport, now::get, BlockingLegacyPairing()) {
            if (it is AttendedPairingUiState.Waiting) {
                countdowns += it.countdown.remainingSeconds
                twoTicks.countDown()
                if (countdowns.size == 1) now.set(1_000)
            }
        }

        coordinator.start(PATH, HOST_ID, "Phone", certificate())

        assertTrue(twoTicks.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(120, 119), countdowns.take(2))
        assertTrue(countdowns.zipWithNext().all { (before, after) -> after <= before })
        coordinator.close()
    }

    @Test
    fun approvedStateKeepsTheSameMonotonicCountdown() {
        val now = AtomicLong(0)
        val transport = FakeTransport(AttendedStatus.State.APPROVED)
        val finishing = CountDownLatch(1)
        val states = CopyOnWriteArrayList<AttendedPairingUiState>()
        val coordinator = coordinator(transport, now::get, BlockingLegacyPairing()) {
            states += it
            if (it is AttendedPairingUiState.Waiting) now.compareAndSet(0, 1_000)
            if (it is AttendedPairingUiState.Finishing) finishing.countDown()
        }

        coordinator.start(PATH, HOST_ID, "Phone", certificate())

        assertTrue(finishing.await(5, TimeUnit.SECONDS))
        val waiting = states.filterIsInstance<AttendedPairingUiState.Waiting>().last()
        val approved = states.filterIsInstance<AttendedPairingUiState.Finishing>().last()
        assertEquals(119, waiting.countdown.remainingSeconds)
        assertEquals(waiting.countdown, approved.countdown)
        coordinator.close()
    }

    @Test
    fun terminalStateCancelsCountdownPublisher() {
        val transport = FakeTransport(AttendedStatus.State.REJECTED)
        val states = CopyOnWriteArrayList<AttendedPairingUiState>()
        val stopped = CountDownLatch(1)
        val coordinator = coordinator(transport, { 0L }, BlockingLegacyPairing()) {
            states += it
            if (it is AttendedPairingUiState.Stopped) stopped.countDown()
        }

        coordinator.start(PATH, HOST_ID, "Phone", certificate())

        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        val terminalCount = states.size
        Thread.sleep(1_100)
        assertEquals(terminalCount, states.size)
        coordinator.close()
    }

    @Test
    fun countdownValueIsBoundedAndRedactedFromToString() {
        assertEquals(0, PairingCountdown.of(0).remainingSeconds)
        assertEquals(120, PairingCountdown.of(120).remainingSeconds)
        assertEquals("PairingCountdown(redacted)", PairingCountdown.of(73).toString())
        assertFalse(PairingCountdown.of(73).toString().contains("73"))
    }

    @Test
    fun cancelUsesDeleteThenExactlyOneStatusProbe() {
        val transport = FakeTransport(AttendedStatus.State.CANCELLED)
        val waiting = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val coordinator = coordinator(transport, { 0L }, BlockingLegacyPairing()) {
            if (it is AttendedPairingUiState.Waiting) waiting.countDown()
            if (it is AttendedPairingUiState.Stopped) stopped.countDown()
        }
        coordinator.start(PATH, HOST_ID, "Phone", certificate())
        assertTrue(waiting.await(5, TimeUnit.SECONDS))
        val pollsBeforeCancel = transport.statusCount.get()
        coordinator.cancelByUser()
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        assertEquals(1, transport.cancelCount.get())
        assertEquals(pollsBeforeCancel + 1, transport.statusCount.get())
        coordinator.close()
    }

    private fun coordinator(
        transport: FakeTransport,
        clock: () -> Long,
        legacy: AttendedPairingCoordinator.LegacyPairing = ImmediateLegacyPairing(),
        auditListener: (AttendedPairingAudit) -> Unit = {},
        listener: (AttendedPairingUiState) -> Unit,
    ) = AttendedPairingCoordinator(
        repository = transport,
        crypto = AttendedPairingCrypto(),
        clock = clock,
        legacyPairing = legacy,
        listener = listener,
        auditListener = auditListener,
    )

    private fun certificate(): X509Certificate =
        Mockito.mock(X509Certificate::class.java).also {
            Mockito.`when`(it.encoded).thenReturn(ByteArray(64) { index -> index.toByte() })
        }

    private class FakeTransport(
        private val statusState: AttendedStatus.State,
    ) : AttendedPairingTransport {
        val createCount = AtomicInteger()
        val envelopeCount = AtomicInteger()
        val statusCount = AtomicInteger()
        val cancelCount = AtomicInteger()

        override fun create(path: String, request: AttendedCreateRequest): AttendedCreateResponse {
            createCount.incrementAndGet()
            return AttendedCreateResponse(
                requestId = request.requestId,
                requestToken = encode(ByteArray(32) { (it + 1).toByte() }),
                hostUniqueId = HOST_ID,
                hostCertificateSha256 = encode(ByteArray(32) { (it + 2).toByte() }),
                hostEphemeralKey =
                    "3p7bfXt9wbTTW2HC7OQ1Nz-DQ8hbeGdNrfx-FG-IK08",
                hostNonce =
                    "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8",
                expiresAt = "2026-07-23T07:30:00Z",
            )
        }

        override fun putEnvelope(
            path: String,
            requestId: String,
            token: String,
            envelope: StrictJsonValue.Obj,
        ) {
            envelopeCount.incrementAndGet()
        }

        override fun status(path: String, requestId: String, token: String): AttendedStatus {
            statusCount.incrementAndGet()
            val effectiveState =
                if (statusState == AttendedStatus.State.CANCELLED && cancelCount.get() == 0) {
                    AttendedStatus.State.PENDING
                } else {
                    statusState
                }
            return AttendedStatus(
                requestId,
                effectiveState,
                "2026-07-23T07:30:00Z",
                if (effectiveState == AttendedStatus.State.FAILED) "legacyPairingFailed" else null,
            )
        }

        override fun cancel(path: String, requestId: String, token: String): Boolean {
            cancelCount.incrementAndGet()
            return true
        }

        override fun cancelInFlight() = Unit

        private fun encode(bytes: ByteArray) = AttendedPairingJson.canonicalBase64Url(bytes)
    }

    private open class BlockingLegacyPairing : AttendedPairingCoordinator.LegacyPairing {
        val cancelCount = AtomicInteger()
        private val cancelled = CountDownLatch(1)

        override fun pair(
            pin: String,
            requestId: String,
            expectedCertificateSha256: ByteArray,
        ): PairingManager.PairState {
            cancelled.await(5, TimeUnit.SECONDS)
            return PairingManager.PairState.FAILED
        }

        override fun cancel() {
            cancelCount.incrementAndGet()
            cancelled.countDown()
        }

        override fun onPaired(certificate: X509Certificate?) = Unit
    }

    private class ImmediateLegacyPairing : BlockingLegacyPairing() {
        override fun pair(
            pin: String,
            requestId: String,
            expectedCertificateSha256: ByteArray,
        ) = PairingManager.PairState.PAIRED
    }

    companion object {
        private const val PATH = "/ligase/v1/pairing/requests"
        private const val HOST_ID = "53beb7ec-9788-cc23-461a-061f153029a5"
    }
}
