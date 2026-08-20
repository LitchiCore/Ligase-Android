package com.limelight.ligase.feature.host.application

import com.limelight.nvstream.http.NvHTTP
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePresenceV1Test {
    private val repository = DevicePresenceRepository()

    @Test
    fun `exact heartbeat result is accepted`() {
        assertTrue(
            repository.validate(
                response(
                    """{"schemaVersion":1,"presenceState":"online","heartbeatIntervalMs":5000,"presenceTimeoutMs":15000}""",
                ),
            ),
        )
    }

    @Test
    fun `wrong envelope extra duplicate trailing and oversized responses fail closed`() {
        assertFalse(repository.validate(response("{}", status = 204)))
        assertFalse(repository.validate(response("{}", contentType = "application/json; charset=utf-8")))
        assertFalse(repository.validate(response("""{"schemaVersion":1,"schemaVersion":1,"presenceState":"online","heartbeatIntervalMs":5000,"presenceTimeoutMs":15000}""")))
        assertFalse(repository.validate(response("""{"schemaVersion":1,"presenceState":"online","heartbeatIntervalMs":5000,"presenceTimeoutMs":15000,"extra":1}""")))
        assertFalse(repository.validate(response("""{"schemaVersion":1,"presenceState":"online","heartbeatIntervalMs":5000,"presenceTimeoutMs":15000} {}""")))
        assertFalse(repository.validate(NvHTTP.DevicePresenceResponse(200, "application/json", ByteArray(513))))
    }

    @Test
    fun `safe result classification preserves no response details`() {
        assertEquals(DevicePresenceAttemptResult.HTTP_STATUS_CLASS, repository.classify(response("{}", status = 503)))
        assertEquals(DevicePresenceAttemptResult.CONTENT_TYPE, repository.classify(response("{}", contentType = "text/html")))
        assertEquals(DevicePresenceAttemptResult.TOO_LARGE, repository.classify(NvHTTP.DevicePresenceResponse(200, "application/json", ByteArray(513))))
        assertEquals(DevicePresenceAttemptResult.EOF_SHAPE, repository.classify(response("{} {}")))
        assertEquals(DevicePresenceAttemptResult.BODY_SCHEMA, repository.classify(response("{}")))
    }

    @Test
    fun `target switch discards late result from old generation`() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val states = Collections.synchronizedList(mutableListOf<DevicePresenceClientState>())
        val coordinator = DevicePresenceCoordinator { state ->
            states += state
        }
        try {
            coordinator.onActive(
                DevicePresenceTarget.test("first") {
                    firstStarted.countDown()
                    releaseFirst.await(2, TimeUnit.SECONDS)
                    DevicePresenceAttemptResult.IO_OTHER
                },
            )
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            val switchIndex = states.size
            coordinator.onActive(DevicePresenceTarget.test("second") { DevicePresenceAttemptResult.ACK_200_VALID })
            releaseFirst.countDown()
            Thread.sleep(100)
            assertFalse(states.drop(switchIndex).contains(DevicePresenceClientState.FAILED))
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun `inactive publishes idle and sends no final heartbeat`() {
        var calls = 0
        val idle = CountDownLatch(1)
        val ack = CountDownLatch(1)
        val coordinator = DevicePresenceCoordinator { state ->
            if (state == DevicePresenceClientState.IDLE) idle.countDown()
            if (state == DevicePresenceClientState.ACKNOWLEDGED) ack.countDown()
        }
        try {
            coordinator.onActive(DevicePresenceTarget.test("host") { calls += 1; DevicePresenceAttemptResult.ACK_200_VALID })
            assertTrue(ack.await(1, TimeUnit.SECONDS))
            coordinator.onInactive()
            assertTrue(idle.await(1, TimeUnit.SECONDS))
            val callsAtStop = calls
            Thread.sleep(100)
            assertTrue(calls == callsAtStop)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun `diagnostics deduplicate recurring attempts and results per generation`() {
        val events = Collections.synchronizedList(mutableListOf<DevicePresenceDiagnosticEvent>())
        val twoCalls = CountDownLatch(2)
        val coordinator = DevicePresenceCoordinator(
            diagnostics = DevicePresenceDiagnosticSink { events += it },
        )
        try {
            val eligibility = DevicePresenceEligibility(
                DevicePresenceEligibilityReason.ELIGIBLE,
                foreground = true,
                activeStream = false,
                selectedAuthenticatedTarget = true,
            )
            coordinator.updateEligibility(eligibility)
            coordinator.updateEligibility(eligibility)
            coordinator.onActive(DevicePresenceTarget.test("host") {
                twoCalls.countDown()
                DevicePresenceAttemptResult.ACK_200_VALID
            })
            assertTrue(twoCalls.await(7, TimeUnit.SECONDS))
            assertEquals(1, events.filterIsInstance<DevicePresenceDiagnosticEvent.Eligibility>().size)
            assertEquals(1, events.filterIsInstance<DevicePresenceDiagnosticEvent.AttemptStarted>().size)
            assertEquals(1, events.filterIsInstance<DevicePresenceDiagnosticEvent.Result>().size)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun `late result emits one generation discarded diagnostic`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<DevicePresenceDiagnosticEvent>())
        val coordinator = DevicePresenceCoordinator(
            diagnostics = DevicePresenceDiagnosticSink { events += it },
        )
        try {
            coordinator.onActive(DevicePresenceTarget.test("host") {
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                DevicePresenceAttemptResult.CANCELLED
            })
            assertTrue(started.await(1, TimeUnit.SECONDS))
            coordinator.onInactive()
            release.countDown()
            Thread.sleep(100)
            assertEquals(1, events.filterIsInstance<DevicePresenceDiagnosticEvent.GenerationDiscarded>().size)
        } finally {
            coordinator.close()
        }
    }

    private fun response(
        body: String,
        status: Int = 200,
        contentType: String? = "application/json",
    ) = NvHTTP.DevicePresenceResponse(status, contentType, body.toByteArray(Charsets.UTF_8))
}
