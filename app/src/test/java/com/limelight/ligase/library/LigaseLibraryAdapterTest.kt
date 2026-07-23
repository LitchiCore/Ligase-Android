package com.limelight.ligase.library

import com.limelight.nvstream.http.NvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LigaseLibraryAdapterTest {
    @Test
    fun `Sync adapter recognizes system kinds and uses canonical labels`() {
        val snapshot = syncSnapshot(
            item(LigaseLibraryAdapter.VIRTUAL_DESKTOP_UUID, HostLibraryKind.VIRTUAL_DESKTOP, "Host supplied name"),
            item(LigaseLibraryAdapter.DESKTOP_UUID, HostLibraryKind.DESKTOP, "Another host name"),
        )
        val items = LigaseLibraryAdapter.fromSyncSnapshot(
            snapshot,
            listOf(
                app("Protocol virtual", LigaseLibraryAdapter.VIRTUAL_DESKTOP_UUID, 9),
                app("Protocol desktop", LigaseLibraryAdapter.DESKTOP_UUID.lowercase(), 8),
            ),
        )

        assertEquals(HostLibraryKind.VIRTUAL_DESKTOP, items[0].kind)
        assertEquals("虚拟桌面", items[0].name)
        assertEquals(HostLibraryKind.DESKTOP, items[1].kind)
        assertEquals("监控桌面", items[1].name)
        assertEquals(
            "uuid:${LigaseLibraryAdapter.DESKTOP_UUID}",
            items[1].key.stableValue,
        )
    }

    @Test
    fun `Sync adapter never uses display name as fallback identity`() {
        val syncUuid = "A0000000-0000-0000-0000-000000000001"
        val snapshot = syncSnapshot(item(syncUuid, HostLibraryKind.STEAM, "Same name"))
        val mapped = LigaseLibraryAdapter.fromSyncSnapshot(
            snapshot,
            listOf(app("Same name", "B0000000-0000-0000-0000-000000000002", 12)),
        ).single()

        assertEquals("uuid:$syncUuid", mapped.key.stableValue)
        assertFalse(mapped.isLaunchable)
        assertNull(mapped.appId)
    }

    @Test
    fun `system entries remain first and fixed while ordinary games are sorted`() {
        val snapshot = syncSnapshot(
            item("A0000000-0000-0000-0000-000000000001", HostLibraryKind.STEAM, "Zeta"),
            item(LigaseLibraryAdapter.VIRTUAL_DESKTOP_UUID, HostLibraryKind.VIRTUAL_DESKTOP, "Virtual"),
            item("A0000000-0000-0000-0000-000000000002", HostLibraryKind.EXECUTABLE, "Alpha"),
            item(LigaseLibraryAdapter.DESKTOP_UUID, HostLibraryKind.DESKTOP, "Desktop"),
        )
        val items = LigaseLibraryAdapter.fromSyncSnapshot(
            snapshot,
            listOf(
                app("Zeta", "A0000000-0000-0000-0000-000000000001", 1),
                app("Virtual", LigaseLibraryAdapter.VIRTUAL_DESKTOP_UUID, 2),
                app("Alpha", "A0000000-0000-0000-0000-000000000002", 3),
                app("Desktop", LigaseLibraryAdapter.DESKTOP_UUID, 4),
            ),
        )

        val sorted = LigaseLibraryAdapter.visibleItems(
            items,
            query = "",
            sortMode = HostSortMode.NAME_DESCENDING,
        )

        assertEquals(
            listOf("监控桌面", "虚拟桌面", "Zeta", "Alpha"),
            sorted.map(LigaseLibraryItem::name),
        )
    }

    @Test
    fun `future snapshot metadata is authoritative but launch app remains GameStream`() {
        val launchApp = app("Protocol name", "A0000000-0000-0000-0000-000000000001", 42)
        val snapshot = syncSnapshot(
            item(
                id = launchApp.appUUID,
                kind = HostLibraryKind.STEAM,
                name = "Host library name",
                steamAppId = 123456,
            ),
        )

        val mapped = LigaseLibraryAdapter.fromSyncSnapshot(snapshot, listOf(launchApp)).single()

        assertEquals("Host library name", mapped.name)
        assertEquals(HostLibraryKind.STEAM, mapped.kind)
        assertEquals(123456L, mapped.steamAppId)
        assertTrue(mapped.launchApp === launchApp)
        assertEquals(42, mapped.appId)
    }

    @Test
    fun `sync snapshot retains unavailable item but disables launch`() {
        val snapshot = syncSnapshot(
            item(
                LigaseLibraryAdapter.VIRTUAL_DESKTOP_UUID,
                HostLibraryKind.VIRTUAL_DESKTOP,
                "Virtual",
            ),
        )

        val item = LigaseLibraryAdapter.fromSyncSnapshot(snapshot, emptyList()).single()
        assertEquals(HostLibraryKind.VIRTUAL_DESKTOP, item.kind)
        assertFalse(item.isLaunchable)
        assertNull(item.launchApp)
        assertNull(item.appId)
    }

    @Test
    fun `streaming resolution uses case insensitive app override then global`() {
        val streaming = LigaseStreamingSyncDto(
            schemaVersion = 1,
            revision = 8,
            updatedAt = "2026-07-23T04:30:00Z",
            globalResolution = LigaseResolutionDto(1920, 1080),
            apps = mapOf(
                "A0000000-0000-0000-0000-000000000001" to LigaseAppStreamingDto(
                    LigaseResolutionDto(2560, 1440),
                ),
            ),
        )

        assertEquals(
            LigaseResolutionDto(2560, 1440),
            streaming.resolutionFor("a0000000-0000-0000-0000-000000000001"),
        )
        assertEquals(
            LigaseResolutionDto(1920, 1080),
            streaming.resolutionFor("B0000000-0000-0000-0000-000000000002"),
        )
    }

    @Test
    fun `clearing app override serializes explicit null`() {
        val json = LigaseSyncRepository().encodeAppResolutionWrite(
            baseRevision = 9,
            appUuid = "A0000000-0000-0000-0000-000000000001",
            resolution = null,
        )

        assertTrue(json.contains("\"baseRevision\":9"))
        assertTrue(json.contains("\"resolution\":null"))
    }

    @Test
    fun `unknown wire values fail closed`() {
        assertNull(HostLibraryKind.fromWireValue("Desktop"))
        assertEquals(
            HostSortMode.NAME_ASCENDING,
            HostSortMode.fromWireValue("名称升序"),
        )
    }

    private fun app(name: String, uuid: String, id: Int): NvApp =
        NvApp(name, uuid, id, false)

    private fun item(
        id: String,
        kind: HostLibraryKind,
        name: String,
        steamAppId: Long? = null,
    ) = HostLibraryItemDto(
        id = id,
        kind = kind.wireValue,
        name = name,
        steamAppId = steamAppId,
        addedAt = "2026-07-22T04:30:00Z",
        updatedAt = "2026-07-23T04:30:00Z",
        lastPlayedAt = null,
        system = kind.isSystem,
    )

    private fun syncSnapshot(vararg items: HostLibraryItemDto) = LigaseSyncSnapshotDto(
        schemaVersion = 1,
        capabilities = LigaseCapabilitiesDto(hdrEncodingSupported = true),
        library = LigaseLibrarySyncDto(
            revision = 12,
            updatedAt = "2026-07-23T04:30:00Z",
            sortMode = HostSortMode.NAME_ASCENDING.wireValue,
            items = items.toList(),
        ),
        streaming = LigaseStreamingSyncDto(
            schemaVersion = 1,
            revision = 4,
            updatedAt = "2026-07-23T04:30:00Z",
            globalResolution = LigaseResolutionDto(1920, 1080),
            apps = emptyMap(),
        ),
    )
}
