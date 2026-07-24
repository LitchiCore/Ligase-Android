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
import com.limelight.ligase.feature.library.domain.LibraryHdrReason
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.library.LibraryContentSnapshot
import com.limelight.ligase.library.LibrarySessionViewModel
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Executor

class LibraryStreamingSettingsCoordinatorTest {
    @Test
    fun `global success writes exact base revision and updates session`() {
        val fixture = fixture()
        val results = mutableListOf<LibraryStreamingSettingsResult>()
        val coordinator = fixture.coordinator(results::add)

        assertTrue(
            coordinator.updateGlobal(
                fixture.hostA,
                fixture.snapshot,
                LigaseResolutionDto(1600, 900),
            ),
        )

        val body = captureSingleBody(fixture.http)
        val json = JsonParser.parseString(body).asJsonObject
        assertEquals(REVISION, json["baseRevision"].asLong)
        assertEquals(1600, json["globalResolution"].asJsonObject["width"].asInt)
        assertEquals(900, json["globalResolution"].asJsonObject["height"].asInt)
        assertEquals(1, results.size)
        assertTrue(results.single() is LibraryStreamingSettingsResult.Success)
        assertEquals(REVISION + 1, fixture.session.state.content?.sync?.streaming?.revision)
    }

    @Test
    fun `app success preserves exact canonical UUID and nullable override`() {
        val fixture = fixture()
        val coordinator = fixture.coordinator()

        assertTrue(coordinator.updateApp(fixture.hostA, fixture.snapshot, APP_UUID, null))

        val json = JsonParser.parseString(captureSingleBody(fixture.http)).asJsonObject
        assertEquals(REVISION, json["baseRevision"].asLong)
        assertEquals(APP_UUID, json["app"].asJsonObject["id"].asString)
        assertTrue(json["app"].asJsonObject["resolution"].isJsonNull)
    }

    @Test
    fun `same operation is single flight and callback happens once`() {
        val fixture = fixture(QueueExecutor())
        val results = mutableListOf<LibraryStreamingSettingsResult>()
        val coordinator = fixture.coordinator(results::add)

        assertTrue(
            coordinator.updateGlobal(
                fixture.hostA,
                fixture.snapshot,
                LigaseResolutionDto(1600, 900),
            ),
        )
        assertFalse(
            coordinator.updateGlobal(
                fixture.hostA,
                fixture.snapshot,
                LigaseResolutionDto(1280, 720),
            ),
        )
        assertEquals(1, fixture.queue.size)

        fixture.queue.runNext()

        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
        assertEquals(1, results.size)
    }

    @Test
    fun `host switch rejects stale completion without callback`() {
        val fixture = fixture(QueueExecutor())
        val results = mutableListOf<LibraryStreamingSettingsResult>()
        val coordinator = fixture.coordinator(results::add)
        assertTrue(
            coordinator.updateGlobal(
                fixture.hostA,
                fixture.snapshot,
                LigaseResolutionDto(1600, 900),
            ),
        )

        fixture.session.selectHost(HOST_B, "Host B")
        fixture.queue.runNext()

        assertTrue(results.isEmpty())
        assertEquals(HOST_B.lowercase(), fixture.session.state.hostKey)
        assertNull(fixture.session.state.content)
        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
    }

    @Test
    fun `revision conflict is typed once and never replayed`() {
        val fixture = fixture()
        doThrow(HostHttpResponseException(409, "conflict", """{"currentRevision":9}"""))
            .`when`(fixture.http)
            .postLigaseJson(anyString(), anyString())
        val results = mutableListOf<LibraryStreamingSettingsResult>()
        var refreshes = 0
        val coordinator = fixture.coordinator(
            result = results::add,
            onRevisionConflict = { refreshes++ },
        )

        assertTrue(
            coordinator.updateGlobal(
                fixture.hostA,
                fixture.snapshot,
                LigaseResolutionDto(1600, 900),
            ),
        )

        assertEquals(listOf(LibraryStreamingSettingsResult.RevisionConflict), results)
        assertEquals(1, refreshes)
        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
        assertEquals(REVISION, fixture.session.state.content?.sync?.streaming?.revision)
    }

    @Test
    fun `permission denied is typed once`() {
        val fixture = fixture()
        doThrow(HostHttpResponseException(403, "permissionDenied"))
            .`when`(fixture.http)
            .postLigaseJson(anyString(), anyString())
        val results = mutableListOf<LibraryStreamingSettingsResult>()
        var permissionRefreshes = 0

        fixture.coordinator(
            result = results::add,
            onPermissionDenied = { permissionRefreshes++ },
        ).updateApp(
            fixture.hostA,
            fixture.snapshot,
            APP_UUID,
            LigaseResolutionDto(1280, 720),
        )

        assertEquals(listOf(LibraryStreamingSettingsResult.PermissionDenied), results)
        assertEquals(1, permissionRefreshes)
        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
    }

    @Test
    fun `generic failure is typed and callback is not doubled`() {
        val fixture = fixture()
        doThrow(IOException("offline"))
            .`when`(fixture.http)
            .postLigaseJson(anyString(), anyString())
        val results = mutableListOf<LibraryStreamingSettingsResult>()

        fixture.coordinator(results::add).updateGlobal(
            fixture.hostA,
            fixture.snapshot,
            LigaseResolutionDto(1600, 900),
        )

        assertEquals(1, results.size)
        assertTrue(results.single() is LibraryStreamingSettingsResult.Failed)
        verify(fixture.http, times(1)).postLigaseJson(anyString(), anyString())
    }

    private fun fixture(executor: Executor = DirectExecutor): Fixture {
        val session = LibrarySessionViewModel()
        session.selectHost(HOST_A, "Host A")
        val snapshot = snapshot()
        val ticket = requireNotNull(session.beginRefresh(HOST_A))
        assertTrue(
            session.acceptSuccess(
                ticket,
                LibraryContentSnapshot(
                    sync = snapshot,
                    transportApps = emptyList(),
                    rawAppList = "<root/>",
                    items = emptyList(),
                    hdr = LibraryHdrState(false, LibraryHdrReason.USER_DISABLED),
                ),
            ),
        )
        val http = mock(NvHTTP::class.java)
        `when`(http.postLigaseJson(anyString(), anyString())).thenReturn(
            streamingJson(REVISION + 1),
        )
        return Fixture(session, snapshot, http, executor)
    }

    private fun captureSingleBody(http: NvHTTP): String {
        val body = ArgumentCaptor.forClass(String::class.java)
        verify(http, times(1)).postLigaseJson(
            org.mockito.ArgumentMatchers.eq(LigaseSyncRepository.STREAMING_PATH),
            body.capture(),
        )
        return body.value
    }

    private fun snapshot() = LigaseSyncSnapshotDto(
        schemaVersion = 1,
        capabilities = LigaseCapabilitiesDto(hdrEncodingSupported = true),
        library = LigaseLibrarySyncDto(
            revision = 1,
            updatedAt = NOW,
            sortMode = HostSortMode.MANUAL.wireValue,
            items = listOf(
                HostLibraryItemDto(
                    id = APP_UUID,
                    kind = HostLibraryKind.STEAM.wireValue,
                    name = "Game",
                    steamAppId = null,
                    addedAt = NOW,
                    updatedAt = NOW,
                    lastPlayedAt = null,
                    system = false,
                    publishedToClients = true,
                ),
            ),
        ),
        streaming = LigaseStreamingSyncDto(
            schemaVersion = 1,
            revision = REVISION,
            updatedAt = NOW,
            globalResolution = LigaseResolutionDto(1920, 1080),
            apps = emptyMap(),
        ),
    )

    private fun streamingJson(revision: Long) =
        """{"schemaVersion":1,"revision":$revision,"updatedAt":"$NOW","globalResolution":{"width":1600,"height":900},"apps":{}}"""

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
        }
        val queue: QueueExecutor
            get() = executor as QueueExecutor

        fun coordinator(
            result: (LibraryStreamingSettingsResult) -> Unit = {},
            onRevisionConflict: () -> Unit = {},
            onPermissionDenied: () -> Unit = {},
        ) = LibraryStreamingSettingsCoordinator(
            session = session,
            repository = LigaseSyncRepository(),
            httpFactory = { http },
            background = executor,
            postToMain = { it() },
            onRevisionConflict = { onRevisionConflict() },
            onPermissionDenied = { onPermissionDenied() },
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

    companion object {
        private const val HOST_A = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"
        private const val HOST_B = "11111111-2222-3333-4444-555555555555"
        private const val APP_UUID = "f3d67f4d-b1fe-4c5d-a77e-b78a51051c1a"
        private const val REVISION = 7L
        private const val NOW = "2026-07-25T00:00:00Z"
    }
}
