package com.limelight.ligase.feature.library.application

import com.limelight.grid.assets.CachedAppAssetLoader
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
import com.limelight.ligase.feature.library.domain.LibraryItemKey
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.infrastructure.LegacyGameStreamLibraryTransport
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.LibraryContentSnapshot
import com.limelight.ligase.library.LibrarySessionViewModel
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.ArrayDeque
import java.util.concurrent.Executor

class LibraryHostCoordinatorTest {
    @Test
    fun `refresh accepts content and keeps transport outside activity`() {
        val session = LibrarySessionViewModel()
        val transport = FakeTransport(content(HOST_A))
        val accepted = mutableListOf<Boolean>()
        val coordinator = coordinator(session, transport, DirectExecutor, accepted::add)
        val host = host(HOST_A)

        coordinator.selectHost(host)
        assertTrue(coordinator.fetch(host, force = true))

        assertEquals(LibraryConnectivity.ONLINE, session.state.connectivity)
        assertEquals(HOST_A.lowercase(), session.state.hostKey)
        assertEquals(1, session.state.content?.items?.size)
        assertEquals(listOf(false), accepted)
    }

    @Test
    fun `same host refresh is single flight`() {
        val session = LibrarySessionViewModel()
        val queue = QueueExecutor()
        val coordinator = coordinator(session, FakeTransport(content(HOST_A)), queue)
        val host = host(HOST_A)
        coordinator.selectHost(host)

        assertTrue(coordinator.fetch(host, force = true))
        assertFalse(coordinator.fetch(host, force = true))
        assertEquals(1, queue.size)

        queue.runNext()
        assertEquals(1, session.state.content?.items?.size)
    }

    @Test
    fun `host switch rejects stale transport result`() {
        val session = LibrarySessionViewModel()
        val queue = QueueExecutor()
        val coordinator = coordinator(session, FakeTransport(content(HOST_A)), queue)
        val hostA = host(HOST_A)
        val hostB = host(HOST_B)
        coordinator.selectHost(hostA)
        assertTrue(coordinator.fetch(hostA, force = true))

        coordinator.selectHost(hostB)
        queue.runNext()

        assertEquals(HOST_B.lowercase(), session.state.hostKey)
        assertNull(session.state.content)
        assertTrue(session.state.initialLoading)
    }

    @Test
    fun `offline transition retains last successful content`() {
        val session = LibrarySessionViewModel()
        val coordinator = coordinator(session, FakeTransport(content(HOST_A)), DirectExecutor)
        val online = host(HOST_A)
        coordinator.selectHost(online)
        coordinator.fetch(online, force = true)
        val content = session.state.content

        val offline = host(HOST_A).apply { state = ComputerDetails.State.OFFLINE }
        coordinator.updateConnectivity(offline)

        assertSame(content, session.state.content)
        assertEquals(LibraryConnectivity.OFFLINE, session.state.connectivity)
        assertFalse(session.state.initialLoading)
    }

    private fun coordinator(
        session: LibrarySessionViewModel,
        transport: LegacyGameStreamLibraryTransport,
        executor: Executor,
        onAccepted: (Boolean) -> Unit = {},
    ) = LibraryHostCoordinator(
        session = session,
        repository = LigaseSyncRepository(),
        transport = transport,
        hdrState = { HDR },
        background = executor,
        postToMain = { it() },
        onRefreshAccepted = onAccepted,
    )

    private fun host(uuid: String) = ComputerDetails().apply {
        this.uuid = uuid
        name = "Host $uuid"
        state = ComputerDetails.State.ONLINE
        ligaseSyncVersion = LigaseSyncRepository.SUPPORTED_SYNC_VERSION
        ligaseSyncPath = "/ligase/v1/sync"
    }

    private fun content(hostUuid: String): LibraryContentSnapshot {
        val app = NvApp("Desktop", APP_UUID, 1, false)
        val dto = HostLibraryItemDto(
            id = APP_UUID,
            kind = HostLibraryKind.DESKTOP.wireValue,
            name = "Desktop",
            steamAppId = null,
            system = true,
            addedAt = "2026-07-25T00:00:00Z",
            updatedAt = "2026-07-25T00:00:00Z",
            lastPlayedAt = null,
            publishedToClients = true,
        )
        return LibraryContentSnapshot(
            sync = LigaseSyncSnapshotDto(
                schemaVersion = 1,
                capabilities = LigaseCapabilitiesDto(hdrEncodingSupported = true),
                library = LigaseLibrarySyncDto(
                    revision = 1,
                    updatedAt = "2026-07-25T00:00:00Z",
                    sortMode = HostSortMode.MANUAL.wireValue,
                    items = listOf(dto),
                ),
                streaming = LigaseStreamingSyncDto(
                    schemaVersion = 1,
                    revision = 1,
                    updatedAt = "2026-07-25T00:00:00Z",
                    globalResolution = LigaseResolutionDto(1920, 1080),
                    apps = emptyMap(),
                ),
            ),
            transportApps = listOf(app),
            rawAppList = "<root host=\"$hostUuid\"/>",
            items = listOf(
                LigaseLibraryItem(
                    key = LibraryItemKey.HostUuid(APP_UUID),
                    name = "Desktop",
                    kind = HostLibraryKind.DESKTOP,
                    hostAppUuid = APP_UUID,
                    appId = 1,
                    steamAppId = null,
                    addedAt = dto.addedAt,
                    updatedAt = dto.updatedAt,
                    lastPlayedAt = null,
                    launchApp = app,
                ),
            ),
            hdr = HDR,
        )
    }

    private class FakeTransport(
        private val content: LibraryContentSnapshot,
    ) : LegacyGameStreamLibraryTransport() {
        override fun createAssetLoader(host: ComputerDetails): CachedAppAssetLoader =
            mock(CachedAppAssetLoader::class.java)

        override fun fetchContent(
            host: ComputerDetails,
            syncPath: String,
            repository: LigaseSyncRepository,
            hdrState: (Boolean?) -> LibraryHdrState,
        ): LibraryContentSnapshot = content
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
        private const val APP_UUID = "78a25216-f239-45bd-b4aa-f41c814066e9"
        private val HDR = LibraryHdrState(false, LibraryHdrReason.USER_DISABLED)
    }
}
