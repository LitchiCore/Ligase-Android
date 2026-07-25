package com.limelight.ligase.feature.input.layout.v2.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v2.data.LayoutCatalogV2LocalRepository
import com.limelight.ligase.feature.input.layout.v2.data.LayoutCatalogV2PackagedSource
import com.limelight.ligase.feature.input.layout.v2.data.LayoutPreferredVariantV2Repository
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Codec
import com.limelight.ligase.layout.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutCatalogV2SourceRegistryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var local: LayoutCatalogV2LocalRepository
    private lateinit var preferences: LayoutPreferredVariantV2Repository
    private lateinit var catalog: LayoutCatalogV2Catalog
    private lateinit var registry: LayoutCatalogV2SourceRegistry
    private lateinit var raw: ByteArray
    private lateinit var descriptor: LayoutDescriptorV1

    @Before
    fun setUp() {
        cleanup()
        raw = positiveFile().readBytes()
        descriptor = descriptorFor(TouchLayoutV2Codec.decode(raw))
        local = LayoutCatalogV2LocalRepository(context)
        preferences = LayoutPreferredVariantV2Repository(context)
        catalog = LayoutCatalogV2Catalog(local, preferences)
        registry = LayoutCatalogV2SourceRegistry(
            catalog,
            LayoutCatalogV2PackagedSource(context),
        )
    }

    @After
    fun tearDown() = cleanup()

    @Test
    fun `missing packaged manifest publishes real empty lifecycle`() {
        val loading = registry.beginRefresh()
        assertTrue(registry.state.lifecycle.initialLoading)
        assertTrue(registry.state.refreshing)
        assertEquals(LayoutCatalogRefreshOutcome.IN_PROGRESS, registry.state.refreshOutcome)

        assertTrue(
            registry.completeRefresh(
                loading,
                emptyList(),
                LayoutCatalogV2HostSnapshot(LayoutHostCatalogState.NOT_CONNECTED),
                null,
            ),
        )
        assertEquals(LayoutCatalogRefreshOutcome.SUCCESS, registry.state.refreshOutcome)
        assertEquals(LayoutCatalogEmptyReason.NO_AUTHORIZED_SOURCES, registry.state.emptyReason)
        assertTrue(registry.state.lifecycle.hasLoaded)
        assertFalse(registry.state.lifecycle.stale)
        assertEquals(
            LayoutCatalogSourcePhase.UNAVAILABLE,
            registry.state.sourceSummaries.single {
                it.origin == LayoutLocalOrigin.PACKAGED_BUILT_IN
            }.phase,
        )
    }

    @Test
    fun `late generation and close discard without publishing`() {
        val first = registry.beginRefresh()
        val second = registry.beginRefresh()
        val before = registry.state
        assertFalse(
            registry.completeRefresh(
                first,
                emptyList(),
                LayoutCatalogV2HostSnapshot(LayoutHostCatalogState.NOT_CONNECTED),
                null,
            ),
        )
        assertEquals(before, registry.state)
        registry.close()
        assertFalse(
            registry.completeRefresh(
                second,
                emptyList(),
                LayoutCatalogV2HostSnapshot(LayoutHostCatalogState.NOT_CONNECTED),
                null,
            ),
        )
        assertEquals(before, registry.state)
    }

    @Test
    fun `host six states map without inventing local content`() {
        val expected = mapOf(
            LayoutHostCatalogState.NOT_CONNECTED to
                (LayoutCatalogSourcePhase.UNAVAILABLE to LayoutCatalogSourceIssueCode.HOST_DISCONNECTED),
            LayoutHostCatalogState.AUTHORIZED_LOADING to
                (LayoutCatalogSourcePhase.LOADING to null),
            LayoutHostCatalogState.AUTHORIZED_EMPTY to
                (LayoutCatalogSourcePhase.READY to LayoutCatalogSourceIssueCode.HOST_AUTHORIZED_EMPTY),
            LayoutHostCatalogState.AUTHORIZED_METADATA_READY to
                (LayoutCatalogSourcePhase.READY to null),
            LayoutHostCatalogState.PERMISSION_DENIED to
                (LayoutCatalogSourcePhase.FAILED to LayoutCatalogSourceIssueCode.PERMISSION_DENIED),
            LayoutHostCatalogState.FAILED to
                (LayoutCatalogSourcePhase.FAILED to LayoutCatalogSourceIssueCode.HOST_SOURCE_FAILED),
        )
        expected.forEach { (hostState, mapping) ->
            val descriptors = if (
                hostState == LayoutHostCatalogState.AUTHORIZED_METADATA_READY
            ) listOf(descriptor) else emptyList()
            registry.refresh(
                hostSnapshot = LayoutCatalogV2HostSnapshot(hostState, descriptors),
            )
            val summary = registry.state.sourceSummaries.single {
                it.origin == LayoutLocalOrigin.HOST_CATALOG
            }
            assertEquals(mapping.first, summary.phase)
            assertEquals(mapping.second, summary.issueCode)
            if (hostState == LayoutHostCatalogState.AUTHORIZED_LOADING) {
                assertTrue(registry.state.refreshing)
                assertEquals(
                    LayoutCatalogRefreshOutcome.IN_PROGRESS,
                    registry.state.refreshOutcome,
                )
            }
            assertTrue(
                registry.state.items
                    .filter { it.local.origin == LayoutLocalOrigin.HOST_CATALOG }
                    .all { it.local.availability == LayoutLocalAvailability.NOT_LOCAL },
            )
        }
    }

    @Test
    fun `revision set coexists and exact host metadata does not duplicate local item`() {
        assertEquals(LayoutCatalogV2WriteCode.SAVED, catalog.saveLocalCopy(descriptor, raw).code)
        registry.refresh(
            registeredRecords = listOf(
                LayoutCatalogV2RegisteredRecord(
                    descriptor,
                    LayoutLocalOrigin.LOCAL_COPY,
                    LayoutWorkspaceState.DRAFT,
                ),
            ),
            hostSnapshot = LayoutCatalogV2HostSnapshot(
                LayoutHostCatalogState.AUTHORIZED_METADATA_READY,
                listOf(descriptor, descriptor.copy(revision = 2)),
            ),
        )
        assertEquals(2, registry.state.items.size)
        assertEquals(
            listOf(1L, 2L),
            registry.state.revisionSets.single().revisions,
        )
        assertEquals(
            1,
            registry.state.items.count {
                it.revision == descriptor.revision
            },
        )
    }

    @Test
    fun `local verified content is ready and no viewport is read only`() {
        assertEquals(LayoutCatalogV2WriteCode.SAVED, catalog.saveLocalCopy(descriptor, raw).code)
        val record = LayoutCatalogV2RegisteredRecord(
            descriptor,
            LayoutLocalOrigin.LOCAL_COPY,
            LayoutWorkspaceState.DRAFT,
        )
        registry.refresh(registeredRecords = listOf(record))
        val item = registry.state.items.single()
        assertEquals(LayoutLocalAvailability.READY, item.local.availability)
        assertEquals(
            LayoutVariantSelectionCode.WAITING_FOR_CONTEXT_VALIDATION,
            item.selection.code,
        )
        val before = preferences.snapshotForTest()
        val variantId = descriptor.variants.single().variantId
        assertEquals(
            LayoutPreferredVariantWriteCode.CONTEXT_UNAVAILABLE,
            registry.selectPreferredVariant(descriptor.layoutId, descriptor.revision, variantId).code,
        )
        assertEquals(
            LayoutPreferredVariantWriteCode.CONTEXT_UNAVAILABLE,
            registry.clearPreferredVariant(descriptor.layoutId).code,
        )
        assertEquals(before, preferences.snapshotForTest())
    }

    @Test
    fun `completed failure retains last success and marks stale`() {
        assertEquals(LayoutCatalogV2WriteCode.SAVED, catalog.saveLocalCopy(descriptor, raw).code)
        val record = LayoutCatalogV2RegisteredRecord(
            descriptor,
            LayoutLocalOrigin.LOCAL_COPY,
            LayoutWorkspaceState.DRAFT,
        )
        registry.refresh(registeredRecords = listOf(record))
        val previousItems = registry.state.items
        val ticket = registry.beginRefresh()
        assertEquals(previousItems, registry.state.items)
        assertFalse(registry.state.lifecycle.stale)
        assertTrue(
            registry.completeRefresh(
                ticket,
                emptyList(),
                LayoutCatalogV2HostSnapshot(LayoutHostCatalogState.FAILED),
                null,
            ),
        )
        assertEquals(previousItems, registry.state.items)
        assertTrue(registry.state.lifecycle.stale)
        assertEquals(
            LayoutCatalogRefreshOutcome.FAILED_USING_LAST_SUCCESS,
            registry.state.refreshOutcome,
        )
    }

    @Test
    fun `corrupt registered local content fails closed`() {
        assertEquals(LayoutCatalogV2WriteCode.SAVED, catalog.saveLocalCopy(descriptor, raw).code)
        checkNotNull(local.fileForTest(descriptor.layoutId, descriptor.revision))
            .writeText("""{"corrupt":true}""")
        registry.refresh(
            registeredRecords = listOf(
                LayoutCatalogV2RegisteredRecord(
                    descriptor,
                    LayoutLocalOrigin.LOCAL_COPY,
                    LayoutWorkspaceState.DRAFT,
                ),
            ),
        )
        assertEquals(
            LayoutLocalAvailability.INVALID,
            registry.state.items.single().local.availability,
        )
        assertEquals(LayoutCatalogRefreshOutcome.PARTIAL_SUCCESS, registry.state.refreshOutcome)
        assertEquals(
            LayoutCatalogSourcePhase.FAILED,
            registry.state.sourceSummaries.single {
                it.origin == LayoutLocalOrigin.LOCAL_COPY
            }.phase,
        )
    }

    private fun descriptorFor(document: TouchLayoutV2Document) = LayoutDescriptorV1(
        schemaVersion = 1,
        layoutId = document.layoutId,
        revision = document.revision,
        portableIdentities = listOf(PortableGameIdentityV1("steam", "123")),
        compatibility = LayoutCompatibilityV1(1, 1),
        publicationStatus = "published",
        variants = document.variants.map { variant ->
            LayoutVariantV1(
                variant.variantId,
                "touch",
                variant.deviceClasses.map { it.name.lowercase() },
                variant.orientations.map { it.name.lowercase() },
            )
        },
    )

    private fun positiveFile(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (!current.resolve("tests/fixtures").isDirectory) {
            current = current.parentFile ?: error("Repository root not found")
        }
        return current.resolve("tests/fixtures/ligase-touch-layout-v2-built-in-all-types.json")
    }

    private fun cleanup() {
        context.getSharedPreferences(
            "ligase_touch_layout_v2_preferences",
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        context.filesDir.resolve("ligase-touch-layout-v2").deleteRecursively()
    }
}
