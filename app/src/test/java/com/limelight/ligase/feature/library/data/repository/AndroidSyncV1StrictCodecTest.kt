package com.limelight.ligase.feature.library.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class AndroidSyncV1StrictCodecTest {
    private val repository = LigaseSyncRepository()

    @Test fun `fixed Host vector projects object identity binding and cover authority`() {
        val item = repository.parseSnapshot(valid).library.items.single()
        assertEquals("steam", item.portableIdentity?.provider)
        assertEquals("123", item.portableIdentity?.id)
        assertEquals(7L, item.layoutBinding?.revision)
        assertEquals(SHA, item.coverSha256)
    }

    @Test fun `closed parser rejects alternate identity binding and partial cover shapes`() {
        val invalid = listOf(
            valid.replace("{\"provider\":\"steam\",\"id\":\"123\"}", "\"steam:123\""),
            valid.replace("\"id\":\"123\"", "\"id\":\"0123\""),
            valid.replace("\"revision\":7", "\"revision\":0"),
            valid.replace(",\"coverSourceId\":\"123\"", ""),
            valid.replace("\"name\":\"Example\"", "\"name\":\"Example\",\"command\":\"secret\""),
        )
        invalid.forEach { json -> assertThrows(IOException::class.java) { repository.parseSnapshot(json) } }
    }

    @Test fun `rejected snapshot cannot replace caller authority`() {
        val accepted = repository.parseSnapshot(valid)
        var current = accepted
        runCatching { repository.parseSnapshot(valid.replace("\"id\":\"123\"", "\"id\":\"124\"")) }
            .onSuccess { current = it }
        assertEquals("123", current.library.items.single().portableIdentity?.id)
    }

    private val valid = """{"schemaVersion":1,"capabilities":{"hdrEncodingSupported":true},"library":{"revision":12,"updatedAt":"2026-08-16T00:00:00Z","sortMode":"manual","items":[{"id":"2c42a3d0-79f1-4bb6-98f8-40c18cd5bc91","kind":"steam","name":"Example","steamAppId":123,"portableIdentity":{"provider":"steam","id":"123"},"layoutBinding":{"layoutId":"0b7cd40f-64ae-4eac-845a-fb41dfed80d0","revision":7},"coverSha256":"$SHA","coverSourceKind":"steamClientLibraryCache","coverSourceId":"123","coverUsageRights":"thirdPartyArtworkLocalUseOnlyNoRedistribution","system":false,"publishedToClients":true,"addedAt":"2026-08-16T00:00:00Z","updatedAt":"2026-08-16T00:00:00Z"}]},"streaming":{"schemaVersion":1,"revision":4,"updatedAt":"2026-08-16T00:00:00Z","globalResolution":{"width":1920,"height":1080},"apps":{}}}"""

    companion object { private const val SHA = "431ced6916a2a21a156e38701afe55bbd7f88969fbbfc56d7fe099d47f265460" }
}
