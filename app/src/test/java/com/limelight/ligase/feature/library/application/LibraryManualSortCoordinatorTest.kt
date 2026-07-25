package com.limelight.ligase.feature.library.application

import com.google.gson.JsonParser
import com.limelight.ligase.feature.library.data.dto.HostLibraryItemDto
import com.limelight.ligase.feature.library.data.dto.LigaseCapabilitiesDto
import com.limelight.ligase.feature.library.data.dto.LigaseLibrarySyncDto
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.data.dto.LigaseStreamingSyncDto
import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.ligase.feature.library.data.repository.LigaseSyncRepository
import com.limelight.ligase.feature.library.domain.HostLibraryKind
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LigaseLibraryAdapter
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.LibraryContentSnapshot
import com.limelight.ligase.library.LibrarySessionViewModel
import com.limelight.ligase.library.ManualLibrarySortAction
import com.limelight.ligase.library.ManualLibrarySortResult
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Executor

class LibraryManualSortCoordinatorTest {
    @Test
    fun `success sends exact request and applies Host response order`() {
        val fixture = fixture()
        val results = mutableListOf<ManualLibrarySortResult>()
        var successes = 0
        val requestedOrder = listOf(APP_B, APP_A)

        assertTrue(
            fixture.coordinator(
                result = { results += it },
                onSuccess = { successes++ },
            ).submit(fixture.hostA, fixture.snapshot, requestedOrder),
        )

        val body = ArgumentCaptor.forClass(String::class.java)
        verify(fixture.http).postLigaseJson(
            org.mockito.ArgumentMatchers.eq(LigaseSyncRepository.LIBRARY_SORT_PATH),
            body.capture(),
        )
        val json = JsonParser.parseString(body.value).asJsonObject
        assertEquals(REVISION, json["baseRevision"].asLong)
        assertEquals(requestedOrder, json["orderedAppUuids"].asJsonArray.map { it.asString })
        assertEquals(1, successes)
        assertEquals(1, results.size)
        assertTrue(results.single() is ManualLibrarySortResult.Success)
        assertEquals(
            requestedOrder,
            fixture.session.state.content!!.sync.library.items.map(HostLibraryItemDto::id),
        )
        assertEquals(REVISION + 1, fixture.session.state.content!!.sync.library.revision)
    }

    @Test
    fun `same Host operation is single flight and writes once`() {
        val queue = QueueExecutor()
        val fixture = fixture(queue)
        val coordinator = fixture.coordinator()

        assertTrue(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A)))
        assertFalse(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_A, APP_B)))
        assertEquals(1, queue.size)

        queue.runNext()

        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
        assertFalse(fixture.session.manualSortState.saving)
    }

    @Test
    fun `Host switch rejects stale completion and suppresses callbacks`() {
        val queue = QueueExecutor()
        val fixture = fixture(queue)
        val results = mutableListOf<ManualLibrarySortResult>()
        val coordinator = fixture.coordinator(result = { results += it })

        assertTrue(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A)))
        fixture.session.selectHost(HOST_B, "Host B")
        queue.runNext()

        assertTrue(results.isEmpty())
        assertEquals(HOST_B.lowercase(), fixture.session.state.hostKey)
        assertFalse(fixture.session.manualSortState.saving)
        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
    }

    @Test
    fun `close suppresses late Activity callbacks but settles retained session`() {
        val queue = QueueExecutor()
        val fixture = fixture(queue)
        val results = mutableListOf<ManualLibrarySortResult>()
        val coordinator = fixture.coordinator(result = { results += it })

        assertTrue(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A)))
        coordinator.close()
        queue.runNext()

        assertTrue(results.isEmpty())
        assertFalse(fixture.session.manualSortState.saving)
        assertEquals(REVISION + 1, fixture.session.state.content!!.sync.library.revision)
    }

    @Test
    fun `closed coordinator rejects submission without acquiring session ticket`() {
        val fixture = fixture()
        val coordinator = fixture.coordinator()
        coordinator.close()

        assertFalse(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A)))
        assertFalse(fixture.session.manualSortState.saving)
        verify(fixture.http, never()).postLigaseJson(anyString(), anyString())
    }

    @Test
    fun `revision conflict refreshes once and never replays`() {
        val fixture = fixture()
        doThrow(
            HostHttpResponseException(
                409,
                "conflict",
                """{"error":"revisionConflict","currentRevision":19}""",
            ),
        )
            .`when`(fixture.http).postLigaseJson(anyString(), anyString())
        val results = mutableListOf<ManualLibrarySortResult>()
        var refreshes = 0

        fixture.coordinator(
            result = { results += it },
            onRevisionConflict = { refreshes++ },
        ).submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A))

        assertEquals(listOf(ManualLibrarySortResult.RevisionConflict(19)), results)
        assertEquals(1, refreshes)
        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
        assertEquals(REVISION, fixture.session.state.content!!.sync.library.revision)
    }

    @Test
    fun `permission denied refreshes permission once without replay`() {
        val fixture = fixture()
        doThrow(HostHttpResponseException(403, "permissionDenied"))
            .`when`(fixture.http).postLigaseJson(anyString(), anyString())
        val results = mutableListOf<ManualLibrarySortResult>()
        var permissionRefreshes = 0

        fixture.coordinator(
            result = { results += it },
            onPermissionDenied = { permissionRefreshes++ },
        ).submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A))

        assertEquals(listOf(ManualLibrarySortResult.PermissionDenied), results)
        assertEquals(1, permissionRefreshes)
        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
    }

    @Test
    fun `offline and observe gates issue zero POSTs`() {
        val fixture = fixture()
        val coordinator = fixture.coordinator()
        fixture.session.updateConnectivity(HOST_A, LibraryConnectivity.OFFLINE)
        assertFalse(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A)))

        fixture.session.updateConnectivity(HOST_A, LibraryConnectivity.ONLINE)
        fixture.hostA.ligaseClientAccessMode = "observe"
        assertFalse(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A)))

        verify(fixture.http, never()).postLigaseJson(anyString(), anyString())
        assertFalse(fixture.session.manualSortState.saving)
    }

    @Test
    fun `invalid order and generic failure are distributed once`() {
        val fixture = fixture()
        val results = mutableListOf<ManualLibrarySortResult>()
        val coordinator = fixture.coordinator(result = { results += it })

        assertTrue(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_A)))
        assertEquals(listOf(ManualLibrarySortResult.InvalidOrder), results)
        verify(fixture.http, never()).postLigaseJson(anyString(), anyString())

        doThrow(IOException("offline"))
            .`when`(fixture.http).postLigaseJson(anyString(), anyString())
        assertTrue(coordinator.submit(fixture.hostA, fixture.snapshot, listOf(APP_B, APP_A)))
        assertEquals(2, results.size)
        assertEquals(ManualLibrarySortResult.Failed, results.last())
        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
    }

    private fun fixture(executor: Executor = DirectExecutor): Fixture {
        val session = LibrarySessionViewModel()
        session.selectHost(HOST_A, "Host A")
        val snapshot = snapshot()
        val ticket = requireNotNull(session.beginRefresh(HOST_A))
        val transportApps = snapshot.library.items.mapIndexed { index, item ->
            NvApp(item.name, item.id, index + 1, false)
        }
        assertTrue(
            session.acceptSuccess(
                ticket,
                LibraryContentSnapshot(
                    sync = snapshot,
                    transportApps = transportApps,
                    rawAppList = "<root/>",
                    items = LigaseLibraryAdapter.fromSyncSnapshot(snapshot, transportApps),
                    hdr = LibraryHdrState.UNKNOWN,
                ),
            ),
        )
        assertTrue(session.updateConnectivity(HOST_A, LibraryConnectivity.ONLINE))
        val http = mock(NvHTTP::class.java)
        `when`(http.postLigaseJson(anyString(), anyString()))
            .thenReturn(responseJson(listOf(APP_B, APP_A)))
        return Fixture(session, snapshot, http, executor)
    }

    private fun snapshot() = LigaseSyncSnapshotDto(
        schemaVersion = 1,
        capabilities = LigaseCapabilitiesDto(hdrEncodingSupported = true),
        library = LigaseLibrarySyncDto(
            revision = REVISION,
            updatedAt = NOW,
            sortMode = HostSortMode.MANUAL.wireValue,
            items = listOf(item(APP_A), item(APP_B)),
        ),
        streaming = LigaseStreamingSyncDto(
            schemaVersion = 1,
            revision = 1,
            updatedAt = NOW,
            globalResolution = LigaseResolutionDto(1920, 1080),
            apps = emptyMap(),
        ),
    )

    private fun item(id: String) = HostLibraryItemDto(
        id = id,
        kind = HostLibraryKind.EXECUTABLE.wireValue,
        name = id,
        steamAppId = null,
        addedAt = NOW,
        updatedAt = NOW,
        lastPlayedAt = null,
        system = false,
        publishedToClients = true,
    )

    private fun responseJson(order: List<String>) =
        """{"revision":${REVISION + 1},"sortMode":"manual","orderedAppUuids":["${order.joinToString("\",\"")}"]}"""

    private inner class Fixture(
        val session: LibrarySessionViewModel,
        val snapshot: LigaseSyncSnapshotDto,
        val http: NvHTTP,
        val executor: Executor,
    ) {
        val hostA = ComputerDetails().apply {
            uuid = HOST_A
            name = "Host A"
            state = ComputerDetails.State.ONLINE
            ligaseClientAccessMode = "operate"
        }

        fun coordinator(
            result: (ManualLibrarySortResult) -> Unit = {},
            onRevisionConflict: () -> Unit = {},
            onPermissionDenied: () -> Unit = {},
            onSuccess: () -> Unit = {},
        ) = LibraryManualSortCoordinator(
            session = session,
            action = ManualLibrarySortAction(),
            repository = LigaseSyncRepository(),
            httpFactory = { http },
            background = executor,
            postToMain = { it() },
            onRevisionConflict = { onRevisionConflict() },
            onPermissionDenied = { onPermissionDenied() },
            onSuccess = { onSuccess() },
            onResult = { _, value -> result(value) },
        )
    }

    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    private class QueueExecutor : Executor {
        private val commands = ArrayDeque<Runnable>()
        val size: Int get() = commands.size
        override fun execute(command: Runnable) {
            commands += command
        }
        fun runNext() = commands.removeFirst().run()
    }

    private companion object {
        const val HOST_A = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"
        const val HOST_B = "11111111-2222-3333-4444-555555555555"
        const val APP_A = "2c42a3d0-79f1-4bb6-98f8-40c18cd5bc91"
        const val APP_B = "9af5103b-1dc0-4562-8421-62f95d855a8a"
        const val REVISION = 18L
        const val NOW = "2026-07-25T00:00:00Z"
    }
}
