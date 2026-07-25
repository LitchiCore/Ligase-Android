package com.limelight.ligase.feature.host.application

import com.limelight.ligase.endpoint.LigaseEndpoint
import com.limelight.ligase.endpoint.LigaseEndpointParser
import com.limelight.ligase.feature.host.infrastructure.LegacyComputerRegistryTransport
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager.PairState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Executor

class HostEndpointCoordinatorTest {
    @Test
    fun `host click policy and default selection preserve legacy semantics`() {
        val coordinator = coordinator()
        val unknown = host("unknown", ComputerDetails.State.UNKNOWN, PairState.NOT_PAIRED)
        val offline = host("offline", ComputerDetails.State.OFFLINE, PairState.PAIRED)
        val unpaired = host("unpaired", ComputerDetails.State.ONLINE, PairState.NOT_PAIRED)
        val paired = host("paired", ComputerDetails.State.ONLINE, PairState.PAIRED)

        assertEquals(HostClickAction.IGNORE, coordinator.actionFor(unknown))
        assertEquals(HostClickAction.WAKE, coordinator.actionFor(offline))
        assertEquals(HostClickAction.PAIR, coordinator.actionFor(unpaired))
        assertEquals(HostClickAction.OPEN, coordinator.actionFor(paired))
        assertSame(paired, coordinator.defaultHost(listOf(unknown, offline, paired)))
        assertNull(coordinator.defaultHost(listOf(unknown, offline, unpaired)))
    }

    @Test
    fun `poll stop and restart reject stale callbacks`() {
        val transport = FakeTransport()
        val updates = mutableListOf<String>()
        val coordinator = coordinator(transport)
        val first = host("first", ComputerDetails.State.ONLINE, PairState.PAIRED)
        val stale = host("stale", ComputerDetails.State.ONLINE, PairState.PAIRED)
        val fresh = host("fresh", ComputerDetails.State.ONLINE, PairState.PAIRED)

        coordinator.onForeground { updates += it.uuid }
        assertTrue(coordinator.isPollingForTest())
        transport.emit(0, first)
        coordinator.stopUpdates(wait = true)
        assertFalse(coordinator.isPollingForTest())
        assertEquals(1, transport.stopCount)
        assertTrue(transport.lastStopWait)

        assertTrue(coordinator.startUpdates())
        transport.emit(0, stale)
        transport.emit(1, fresh)

        assertEquals(listOf("first", "fresh"), updates)
    }

    @Test
    fun `background transition stops polling and transport return restarts it`() {
        val transport = FakeTransport()
        val coordinator = coordinator(transport)

        coordinator.onForeground {}
        coordinator.onBackground()
        assertEquals(1, transport.stopCount)
        assertFalse(coordinator.isPollingForTest())

        coordinator.onTransportAvailable {}
        assertFalse(coordinator.isPollingForTest())
        coordinator.onForeground {}
        assertTrue(coordinator.isPollingForTest())
    }

    @Test
    fun `manual add is single flight and late Activity callback is dropped`() {
        val queue = QueueExecutor()
        val transport = FakeTransport()
        val coordinator = coordinator(transport, queue)
        val results = mutableListOf<HostAddResult>()

        assertTrue(coordinator.add(ENDPOINT, results::add))
        assertFalse(coordinator.add(ENDPOINT, results::add))
        coordinator.close()
        queue.runNext()

        assertTrue(results.isEmpty())
        assertEquals(1, transport.addCount)
    }

    @Test
    fun `manual add unavailable is typed and does not queue work`() {
        val queue = QueueExecutor()
        val transport = FakeTransport().apply { available = false }
        val coordinator = coordinator(transport, queue)
        val results = mutableListOf<HostAddResult>()

        assertFalse(coordinator.add(ENDPOINT, results::add))

        assertEquals(listOf(HostAddResult.ManagerUnavailable), results)
        assertEquals(0, queue.size)
    }

    @Test
    fun `wake reports missing mac and transport failure without retry`() {
        val transport = FakeTransport().apply { wakeFails = true }
        val coordinator = coordinator(transport)
        val missing = host("missing", ComputerDetails.State.OFFLINE, PairState.PAIRED)
        val wakeable = host("wakeable", ComputerDetails.State.OFFLINE, PairState.PAIRED).apply {
            macAddress = "01:02:03:04:05:06"
        }
        val results = mutableListOf<HostWakeResult>()

        assertFalse(coordinator.wake(missing, results::add))
        assertTrue(coordinator.wake(wakeable, results::add))

        assertEquals(
            listOf(HostWakeResult.MissingMacAddress, HostWakeResult.Failed),
            results,
        )
        assertEquals(1, transport.wakeCount)
    }

    @Test
    fun `remove delegates registry and asset cleanup exactly once`() {
        val transport = FakeTransport()
        val coordinator = coordinator(transport)
        val target = host("remove", ComputerDetails.State.OFFLINE, PairState.PAIRED)

        assertTrue(coordinator.remove(target))

        assertEquals(listOf("remove"), transport.removed)
    }

    private fun coordinator(
        transport: FakeTransport = FakeTransport(),
        background: Executor = DirectExecutor,
    ) = HostEndpointCoordinator(
        transport = transport,
        background = background,
        postToMain = { it() },
    )

    private fun host(
        uuid: String,
        state: ComputerDetails.State,
        pairState: PairState,
    ) = ComputerDetails().apply {
        this.uuid = uuid
        this.state = state
        this.pairState = pairState
    }

    private class FakeTransport : LegacyComputerRegistryTransport() {
        var available = true
        var stopCount = 0
        var lastStopWait = false
        var addCount = 0
        var wakeCount = 0
        var wakeFails = false
        val removed = mutableListOf<String>()
        private val listeners = mutableListOf<(ComputerDetails) -> Unit>()

        override fun isAvailable(): Boolean = available

        override fun startPolling(onUpdate: (ComputerDetails) -> Unit): Boolean {
            if (!available) return false
            listeners += onUpdate
            return true
        }

        override fun stopPolling(wait: Boolean) {
            stopCount++
            lastStopWait = wait
        }

        override fun add(endpoint: LigaseEndpoint): Pair<Boolean, ComputerDetails> {
            addCount++
            return true to ComputerDetails().apply { uuid = "added" }
        }

        override fun remove(host: ComputerDetails): Boolean {
            removed += host.uuid
            return true
        }

        override fun wake(host: ComputerDetails) {
            wakeCount++
            if (wakeFails) throw IOException("offline")
        }

        fun emit(index: Int, details: ComputerDetails) = listeners[index](details)
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

    companion object {
        private val ENDPOINT =
            LigaseEndpointParser.parseManual("192.0.2.10", "48989", 48989)
    }
}
