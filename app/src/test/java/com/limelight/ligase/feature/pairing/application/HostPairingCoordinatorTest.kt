package com.limelight.ligase.feature.pairing.application

import com.limelight.ligase.feature.pairing.infrastructure.LegacyPairingResult
import com.limelight.ligase.feature.pairing.infrastructure.LegacyPairingTransport
import com.limelight.ligase.pairing.AttendedPairingViewModel
import com.limelight.nvstream.http.ComputerDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.ArrayDeque
import java.util.concurrent.Executor

class HostPairingCoordinatorTest {
    @Test
    fun `only exact attended v1 with nonblank path selects attended`() {
        assertEquals(
            HostPairingMode.ATTENDED,
            coordinator().modeFor(host(version = 1, path = "/ligase/v1/pairing/requests")),
        )
        assertEquals(
            HostPairingMode.LEGACY_EXPLICIT_CONFIRMATION,
            coordinator().modeFor(host(version = 2, path = "/ligase/v1/pairing/requests")),
        )
        assertEquals(
            HostPairingMode.LEGACY_EXPLICIT_CONFIRMATION,
            coordinator().modeFor(host(version = 1, path = " ")),
        )
        assertEquals(
            HostPairingMode.LEGACY_EXPLICIT_CONFIRMATION,
            coordinator().modeFor(host(version = 0, path = null)),
        )
    }

    @Test
    fun `legacy result is posted once and preserves exact pin`() {
        val transport = FakeTransport(LegacyPairingResult.PinWrong)
        val results = mutableListOf<LegacyPairingResult>()
        var beforeCount = 0
        val coordinator = coordinator(
            transport = transport,
            beforeLegacy = { beforeCount++ },
        )

        assertTrue(coordinator.startLegacy(host(), "0427", results::add))

        assertEquals("0427", transport.lastPin)
        assertEquals(1, beforeCount)
        assertEquals(listOf(LegacyPairingResult.PinWrong), results)
    }

    @Test
    fun `legacy operation is single flight until main callback completes`() {
        val background = QueueExecutor()
        val main = QueuePoster()
        val transport = FakeTransport(LegacyPairingResult.Paired)
        val coordinator = coordinator(
            transport = transport,
            background = background,
            postToMain = main::post,
        )
        val results = mutableListOf<LegacyPairingResult>()

        assertTrue(coordinator.startLegacy(host(), "1111", results::add))
        assertFalse(coordinator.startLegacy(host(), "2222", results::add))
        background.runNext()
        assertFalse(coordinator.startLegacy(host(), "3333", results::add))
        assertTrue(results.isEmpty())

        main.runNext()

        assertEquals(listOf(LegacyPairingResult.Paired), results)
        assertTrue(coordinator.startLegacy(host(), "4444", results::add))
        assertEquals(1, background.size)
    }

    private fun coordinator(
        transport: LegacyPairingTransport = FakeTransport(LegacyPairingResult.Paired),
        background: Executor = DirectExecutor,
        postToMain: ((() -> Unit) -> Unit) = { it() },
        beforeLegacy: () -> Unit = {},
    ) = HostPairingCoordinator(
        transport = transport,
        attendedViewModel = mock(AttendedPairingViewModel::class.java),
        background = background,
        postToMain = postToMain,
        beforeLegacyPairing = beforeLegacy,
    )

    private fun host(
        version: Int = 0,
        path: String? = null,
    ) = ComputerDetails().apply {
        uuid = HOST_UUID
        ligaseAttendedPairingVersion = version
        ligaseAttendedPairingPath = path
    }

    private class FakeTransport(
        private val result: LegacyPairingResult,
    ) : LegacyPairingTransport() {
        var lastPin: String? = null

        override fun pairLegacy(host: ComputerDetails, pin: String): LegacyPairingResult {
            lastPin = pin
            return result
        }
    }

    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    private class QueueExecutor : Executor {
        private val commands = ArrayDeque<Runnable>()
        val size: Int get() = commands.size
        override fun execute(command: Runnable) {
            commands.addLast(command)
        }
        fun runNext() = commands.removeFirst().run()
    }

    private class QueuePoster {
        private val commands = ArrayDeque<() -> Unit>()
        fun post(command: () -> Unit) {
            commands.addLast(command)
        }
        fun runNext() = commands.removeFirst().invoke()
    }

    companion object {
        private const val HOST_UUID = "53beb7ec-9788-cc23-461a-061f153029a5"
    }
}
