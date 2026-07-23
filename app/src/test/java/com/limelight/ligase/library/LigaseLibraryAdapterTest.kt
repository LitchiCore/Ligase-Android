package com.limelight.ligase.library

import com.limelight.nvstream.http.NvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LigaseLibraryAdapterTest {
    @Test
    fun `GameStream adapter recognizes fixed system UUIDs and canonical labels`() {
        val items = LigaseLibraryAdapter.fromGameStream(
            hostUniqueId = "host-a",
            apps = listOf(
                app("Host supplied name", LigaseLibraryAdapter.VIRTUAL_DESKTOP_UUID, 9),
                app("Another host name", LigaseLibraryAdapter.DESKTOP_UUID.lowercase(), 8),
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
    fun `GameStream adapter never uses display name as fallback identity`() {
        val first = LigaseLibraryAdapter.fromGameStream(
            "host-a",
            listOf(app("Same name", "", 11)),
        ).single()
        val second = LigaseLibraryAdapter.fromGameStream(
            "host-a",
            listOf(app("Same name", "", 12)),
        ).single()

        assertEquals("gamestream:host-a:11", first.key.stableValue)
        assertEquals("gamestream:host-a:12", second.key.stableValue)
        assertFalse(first.key == second.key)
    }

    @Test
    fun `system entries remain first and fixed while ordinary games are sorted`() {
        val items = LigaseLibraryAdapter.fromGameStream(
            "host-a",
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
        val snapshot = HostLibrarySnapshotDto(
            schemaVersion = 1,
            revision = 12,
            updatedAt = "2026-07-23T04:30:00Z",
            sortMode = HostSortMode.ADDED_NEWEST.wireValue,
            items = listOf(
                HostLibraryItemDto(
                    id = launchApp.appUUID,
                    kind = HostLibraryKind.STEAM.wireValue,
                    name = "Host library name",
                    steamAppId = 123456,
                    addedAt = "2026-07-22T04:30:00Z",
                    updatedAt = "2026-07-23T04:30:00Z",
                    lastPlayedAt = null,
                    system = false,
                ),
            ),
        )

        val mapped = LigaseLibraryAdapter.fromHostSnapshot(snapshot, listOf(launchApp)).single()

        assertEquals("Host library name", mapped.name)
        assertEquals(HostLibraryKind.STEAM, mapped.kind)
        assertEquals(123456L, mapped.steamAppId)
        assertTrue(mapped.launchApp === launchApp)
        assertEquals(42, mapped.appId)
    }

    @Test
    fun `sync snapshot retains unavailable item but disables launch`() {
        val snapshot = HostLibrarySnapshotDto(
            schemaVersion = 1,
            revision = 1,
            updatedAt = "2026-07-23T04:30:00Z",
            sortMode = HostSortMode.NAME_ASCENDING.wireValue,
            items = listOf(
                HostLibraryItemDto(
                    id = LigaseLibraryAdapter.VIRTUAL_DESKTOP_UUID,
                    kind = HostLibraryKind.VIRTUAL_DESKTOP.wireValue,
                    name = "Virtual",
                    steamAppId = null,
                    addedAt = "2026-07-23T04:30:00Z",
                    updatedAt = "2026-07-23T04:30:00Z",
                    lastPlayedAt = null,
                    system = true,
                ),
            ),
        )

        val item = LigaseLibraryAdapter.fromHostSnapshot(snapshot, emptyList()).single()
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
}
