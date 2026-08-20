package com.limelight.ligase.library

import com.limelight.ligase.feature.library.data.dto.*
import com.limelight.ligase.feature.library.domain.*

import com.limelight.nvstream.http.NvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySessionStoreTest {
    @Test
    fun `same host configuration restore keeps verified access until authoritative update`() {
        val store = LibrarySessionStore()
        store.selectHost(HOST_A, "Host", "operate")
        store.updateConnectivity(HOST_A, LibraryConnectivity.ONLINE)

        assertFalse(store.selectHost(HOST_A, "Host", accessMode = null))
        assertEquals("operate", store.state.accessMode)
        assertEquals(LibraryConnectivity.ONLINE, store.state.connectivity)

        assertTrue(
            store.updateHostAuthority(
                HOST_A,
                LibraryConnectivity.ONLINE,
                accessMode = null,
            ),
        )
        assertNull(store.state.accessMode)
    }
    @Test
    fun `same host page reentry preserves last successful content`() {
        val store = readyStore(HOST_A)
        val content = store.state.content

        assertFalse(store.selectHost(HOST_A.lowercase()))

        assertSame(content, store.state.content)
        assertFalse(store.state.initialLoading)
        assertEquals(LigaseLibraryStatus.READY, store.state.status)
    }

    @Test
    fun `checking and offline polling do not erase content`() {
        val store = readyStore(HOST_A)
        val content = store.state.content

        assertTrue(store.updateConnectivity(HOST_A, LibraryConnectivity.CHECKING))
        assertSame(content, store.state.content)
        assertTrue(store.updateConnectivity(HOST_A, LibraryConnectivity.OFFLINE))

        assertSame(content, store.state.content)
        assertEquals(LibraryConnectivity.OFFLINE, store.state.connectivity)
        assertEquals(LigaseLibraryStatus.READY, store.state.status)
    }

    @Test
    fun `offline initial load stops spinner and retains selected host identity`() {
        val store = LibrarySessionStore()
        assertTrue(store.selectHost(HOST_A, "Living room PC"))

        assertTrue(store.updateConnectivity(HOST_A, LibraryConnectivity.OFFLINE))

        assertEquals(HOST_A.lowercase(), store.state.hostKey)
        assertEquals("Living room PC", store.state.hostDisplayName)
        assertFalse(store.state.initialLoading)
        assertNull(store.state.content)
    }

    @Test
    fun `host operations require realtime online and exact operate permission`() {
        assertTrue(
            LibraryOperationGate.canOperate(
                LibraryConnectivity.ONLINE,
                "operate",
            ),
        )
        assertFalse(
            LibraryOperationGate.canOperate(
                LibraryConnectivity.OFFLINE,
                "operate",
            ),
        )
        assertFalse(
            LibraryOperationGate.canOperate(
                LibraryConnectivity.CHECKING,
                "operate",
            ),
        )
        assertFalse(
            LibraryOperationGate.canOperate(
                LibraryConnectivity.ONLINE,
                "observe",
            ),
        )
    }

    @Test
    fun `refresh failure retains content and exposes independent error`() {
        val store = readyStore(HOST_A)
        val content = store.state.content
        val refresh = store.beginRefresh(HOST_A)!!

        assertTrue(refresh.preservesContent)
        assertTrue(store.state.refreshing)
        assertSame(content, store.state.content)

        assertTrue(store.acceptFailure(refresh, LibrarySessionError.SYNC_FAILED))
        assertSame(content, store.state.content)
        assertFalse(store.state.refreshing)
        assertEquals(LibrarySessionError.SYNC_FAILED, store.state.error)
        assertEquals(LigaseLibraryStatus.SYNC_ERROR, store.state.status)
    }

    @Test
    fun `host switch clears content and rejects old result`() {
        val store = readyStore(HOST_A)
        val oldRefresh = store.beginRefresh(HOST_A)!!

        assertTrue(store.selectHost(HOST_B))
        assertNull(store.state.content)
        assertTrue(store.state.initialLoading)
        assertFalse(store.acceptSuccess(oldRefresh, content(HOST_A)))

        assertEquals(HOST_B.lowercase(), store.state.hostKey)
        assertNull(store.state.content)
    }

    @Test
    fun `applist failure retains snapshot and items`() {
        val store = readyStore(HOST_A)
        val content = store.state.content

        assertTrue(store.markAppListFailure(HOST_A))

        assertSame(content, store.state.content)
        assertEquals(LibrarySessionError.APPLIST_FAILED, store.state.error)
        assertEquals(1, store.state.content?.items?.size)
    }

    @Test
    fun `cancelled initial load can restart on page reentry`() {
        val store = LibrarySessionStore()
        store.selectHost(HOST_A)
        val first = store.beginRefresh(HOST_A)!!

        store.cancelRefresh()

        assertFalse(store.state.initialLoading)
        assertFalse(store.acceptSuccess(first, content(HOST_A)))
        assertTrue(store.beginRefresh(HOST_A) != null)
    }

    private fun readyStore(hostUuid: String): LibrarySessionStore =
        LibrarySessionStore().also { store ->
            store.selectHost(hostUuid)
            val request = store.beginRefresh(hostUuid)!!
            assertTrue(store.acceptSuccess(request, content(hostUuid)))
        }

    private fun content(hostUuid: String): LibraryContentSnapshot {
        val app = NvApp("Desktop", APP_UUID, 1, false)
        val item = LigaseLibraryItem(
            key = LibraryItemKey.HostUuid(APP_UUID),
            name = "监控桌面",
            kind = HostLibraryKind.DESKTOP,
            hostAppUuid = APP_UUID,
            appId = 1,
            steamAppId = null,
            addedAt = "2026-07-24T00:00:00Z",
            updatedAt = "2026-07-24T00:00:00Z",
            lastPlayedAt = null,
            launchApp = app,
        )
        return LibraryContentSnapshot(
            sync = LigaseSyncSnapshotDto(
                schemaVersion = 1,
                capabilities = LigaseCapabilitiesDto(hdrEncodingSupported = true),
                library = LigaseLibrarySyncDto(
                    revision = 1,
                    updatedAt = "2026-07-24T00:00:00Z",
                    sortMode = HostSortMode.NAME_ASCENDING.wireValue,
                    items = emptyList(),
                ),
                streaming = LigaseStreamingSyncDto(
                    schemaVersion = 1,
                    revision = 1,
                    updatedAt = "2026-07-24T00:00:00Z",
                    globalResolution = LigaseResolutionDto(1920, 1080),
                    apps = emptyMap(),
                ),
            ),
            transportApps = listOf(app),
            rawAppList = "<host>$hostUuid</host>",
            items = listOf(item),
            hdr = LibraryHdrState(
                available = true,
                reason = LibraryHdrReason.AVAILABLE,
            ),
        )
    }

    private companion object {
        const val HOST_A = "53BEB7EC-9788-CC23-461A-061F153029A5"
        const val HOST_B = "78B73D4B-B71E-4E2F-271C-7CE13E7B9241"
        const val APP_UUID = "78A25216-F239-45BD-B4AA-F41C814066E9"
    }
}
