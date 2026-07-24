package com.limelight.ligase.library

import com.google.gson.GsonBuilder
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvApp
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLibrarySortTest {
    @Test
    fun `request requires exact published canonical UUID set including system entries`() {
        val snapshot = snapshot()
        val order = listOf(APP_B, DESKTOP, APP_A, VIRTUAL_DESKTOP)

        val valid = ManualLibrarySortValidator.validateRequest(
            snapshot,
            order,
        )

        assertEquals(snapshot.library.revision, valid?.baseRevision)
        assertEquals(order, valid?.orderedAppUuids)
        assertEquals(
            null,
            ManualLibrarySortValidator.validateRequest(
                snapshot,
                listOf(APP_A, APP_B),
            ),
        )
        assertEquals(
            null,
            ManualLibrarySortValidator.validateRequest(snapshot, listOf(APP_A)),
        )
        assertEquals(
            null,
            ManualLibrarySortValidator.validateRequest(
                snapshot,
                listOf(APP_A, APP_A, DESKTOP, VIRTUAL_DESKTOP),
            ),
        )
        assertEquals(
            null,
            ManualLibrarySortValidator.validateRequest(
                snapshot,
                listOf(APP_A.uppercase(), APP_B, DESKTOP, VIRTUAL_DESKTOP),
            ),
        )
    }

    @Test
    fun `revision uses JSON safe integer range`() {
        assertTrue(ManualLibrarySortValidator.isSafeRevision(1))
        assertTrue(
            ManualLibrarySortValidator.isSafeRevision(
                ManualLibrarySortValidator.MAX_SAFE_REVISION,
            ),
        )
        assertFalse(ManualLibrarySortValidator.isSafeRevision(0))
        assertFalse(
            ManualLibrarySortValidator.isSafeRevision(
                ManualLibrarySortValidator.MAX_SAFE_REVISION + 1,
            ),
        )
    }

    @Test
    fun `response parser rejects non-integer revision unknown fields and changed order`() {
        assertThrows(IOException::class.java) {
            ManualLibrarySortCodec.parseResponse(
                """{"revision":13.0,"sortMode":"manual","orderedAppUuids":["$APP_A","$APP_B"]}""",
                listOf(APP_A, APP_B),
            )
        }
        assertThrows(IOException::class.java) {
            ManualLibrarySortCodec.parseResponse(
                """{"revision":13,"sortMode":"manual","orderedAppUuids":["$APP_A","$APP_B"],"extra":true}""",
                listOf(APP_A, APP_B),
            )
        }
        assertThrows(IOException::class.java) {
            ManualLibrarySortCodec.parseResponse(
                """{"revision":13,"sortMode":"manual","orderedAppUuids":["$APP_B","$APP_A"]}""",
                listOf(APP_A, APP_B),
            )
        }
    }

    @Test
    fun `request encoder emits integer token and exact fields`() {
        val json = ManualLibrarySortCodec.encodeRequest(
            ManualLibrarySortRequest(12, listOf(APP_B, APP_A)),
            GsonBuilder().create(),
        )

        assertEquals(
            """{"baseRevision":12,"orderedAppUuids":["$APP_B","$APP_A"]}""",
            json,
        )
    }

    @Test
    fun `revision conflict calls writer once and never replays`() {
        var writes = 0
        val result = ManualLibrarySortAction().submit(
            snapshot(),
            listOf(APP_B, DESKTOP, APP_A, VIRTUAL_DESKTOP),
        ) {
            writes++
            throw HostHttpResponseException(
                409,
                "Conflict",
                """{"error":"revisionConflict","currentRevision":13}""",
            )
        }

        assertEquals(1, writes)
        assertEquals(
            ManualLibrarySortResult.RevisionConflict(currentRevision = 13),
            result,
        )
    }

    @Test
    fun `invalid order is rejected before writer`() {
        var writes = 0

        val result = ManualLibrarySortAction().submit(
            snapshot(),
            listOf(APP_A),
        ) {
            writes++
            response(listOf(APP_A))
        }

        assertEquals(0, writes)
        assertEquals(ManualLibrarySortResult.InvalidOrder, result)
    }

    @Test
    fun `success response must advance revision`() {
        val result = ManualLibrarySortAction().submit(
            snapshot(),
            listOf(APP_B, DESKTOP, APP_A, VIRTUAL_DESKTOP),
        ) {
            response(listOf(APP_B, DESKTOP, APP_A, VIRTUAL_DESKTOP)).copy(revision = 12)
        }

        assertEquals(ManualLibrarySortResult.Failed, result)
    }

    @Test
    fun `success applies Host response order to sync and merged items`() {
        val store = LibrarySessionStore()
        store.selectHost(HOST)
        val request = store.beginRefresh(HOST)!!
        assertTrue(store.acceptSuccess(request, content()))

        assertTrue(
            store.applyManualOrder(
                HOST,
                response(listOf(APP_B, DESKTOP, APP_A, VIRTUAL_DESKTOP)),
            ),
        )

        val updated = store.state.content!!
        assertEquals(
            listOf(APP_B, DESKTOP, APP_A, VIRTUAL_DESKTOP, HIDDEN),
            updated.sync.library.items.map(HostLibraryItemDto::id),
        )
        assertEquals(
            listOf(APP_B, DESKTOP, APP_A, VIRTUAL_DESKTOP),
            updated.items.mapNotNull(LigaseLibraryItem::hostAppUuid).map(String::lowercase),
        )
        assertEquals(13, updated.sync.library.revision)
        assertEquals(HostSortMode.MANUAL.wireValue, updated.sync.library.sortMode)
    }

    @Test
    fun `manual display mode preserves complete Host published order`() {
        val itemsByUuid = content().items.associateBy {
            it.hostAppUuid!!.lowercase()
        }
        val expectedOrder = listOf(VIRTUAL_DESKTOP, APP_B, DESKTOP, APP_A)
        val items = expectedOrder.map(itemsByUuid::getValue)

        val visible = LigaseLibraryAdapter.visibleItems(
            items = items,
            query = "",
            sortMode = HostSortMode.MANUAL,
        )

        assertEquals(
            expectedOrder,
            visible.mapNotNull(LigaseLibraryItem::hostAppUuid).map(String::lowercase),
        )
    }

    @Test
    fun `manual coordinator is single flight and rejects stale Host result`() {
        val coordinator = ManualLibrarySortCoordinator()
        coordinator.selectHost(HOST)
        val ticket = coordinator.begin(HOST)!!

        assertTrue(coordinator.state.saving)
        assertEquals(null, coordinator.begin(HOST))

        coordinator.selectHost("78b73d4b-b71e-4e2f-271c-7ce13e7b9241")

        assertFalse(
            coordinator.accept(
                ticket,
                ManualLibrarySortResult.Success(
                    response(listOf(APP_B, DESKTOP, APP_A, VIRTUAL_DESKTOP)),
                ),
            ),
        )
        assertFalse(coordinator.state.saving)
        assertEquals(null, coordinator.state.appliedRevision)
    }

    @Test
    fun `manual coordinator exposes typed conflict without replay state`() {
        val coordinator = ManualLibrarySortCoordinator()
        coordinator.selectHost(HOST)
        val ticket = coordinator.begin(HOST)!!

        assertTrue(
            coordinator.accept(
                ticket,
                ManualLibrarySortResult.RevisionConflict(currentRevision = 13),
            ),
        )

        assertFalse(coordinator.state.saving)
        assertEquals(ManualLibrarySortError.REVISION_CONFLICT, coordinator.state.error)
        assertEquals(null, coordinator.state.appliedRevision)
    }

    private fun response(order: List<String>) = ManualLibrarySortResponse(
        revision = 13,
        sortMode = HostSortMode.MANUAL.wireValue,
        orderedAppUuids = order,
    )

    private fun content(): LibraryContentSnapshot {
        val snapshot = snapshot()
        val apps = snapshot.library.items.mapIndexed { index, item ->
            NvApp(item.name, item.id, index + 1, false)
        }
        return LibraryContentSnapshot(
            sync = snapshot,
            transportApps = apps,
            rawAppList = "<root/>",
            items = LigaseLibraryAdapter.fromSyncSnapshot(snapshot, apps),
            hdr = LibraryHdrState.UNKNOWN,
        )
    }

    private fun snapshot(
        revision: Long = 12,
    ): LigaseSyncSnapshotDto = LigaseSyncSnapshotDto(
        schemaVersion = 1,
        capabilities = LigaseCapabilitiesDto(hdrEncodingSupported = true),
        library = LigaseLibrarySyncDto(
            revision = revision,
            updatedAt = "2026-07-24T00:00:00Z",
            sortMode = HostSortMode.MANUAL.wireValue,
            items = listOf(
                item(DESKTOP, HostLibraryKind.DESKTOP, system = true),
                item(VIRTUAL_DESKTOP, HostLibraryKind.VIRTUAL_DESKTOP, system = true),
                item(APP_A, HostLibraryKind.STEAM),
                item(APP_B, HostLibraryKind.EXECUTABLE),
                item(HIDDEN, HostLibraryKind.EXECUTABLE, published = false),
            ),
        ),
        streaming = LigaseStreamingSyncDto(
            schemaVersion = 1,
            revision = 6,
            updatedAt = "2026-07-24T00:00:00Z",
            globalResolution = LigaseResolutionDto(1920, 1080),
            apps = emptyMap(),
        ),
    )

    private fun item(
        id: String,
        kind: HostLibraryKind,
        system: Boolean = false,
        published: Boolean? = true,
    ) = HostLibraryItemDto(
        id = id,
        kind = kind.wireValue,
        name = id,
        steamAppId = null,
        addedAt = "2026-07-24T00:00:00Z",
        updatedAt = "2026-07-24T00:00:00Z",
        lastPlayedAt = null,
        system = system,
        publishedToClients = published,
    )

    private companion object {
        const val HOST = "53beb7ec-9788-cc23-461a-061f153029a5"
        const val DESKTOP = "78a25216-f239-45bd-b4aa-f41c814066e9"
        const val VIRTUAL_DESKTOP = "8902cb19-674a-403d-a587-41b092e900ba"
        const val APP_A = "2c42a3d0-79f1-4bb6-98f8-40c18cd5bc91"
        const val APP_B = "9af5103b-1dc0-4562-8421-62f95d855a8a"
        const val HIDDEN = "4ea9df2d-33d0-48c9-b057-4790016e7d8a"
    }
}
