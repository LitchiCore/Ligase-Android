package com.limelight.ligase.feature.input.layout.v2.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v2.data.*
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
class LayoutCatalogV2CatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var local: LayoutCatalogV2LocalRepository
    private lateinit var preferences: LayoutPreferredVariantV2Repository
    private lateinit var catalog: LayoutCatalogV2Catalog
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
    }

    @After
    fun tearDown() = cleanup()

    @Test
    fun `packaged verified content projects safe display fields`() {
        val state = catalog.refresh(
            listOf(source(LayoutLocalOrigin.PACKAGED_BUILT_IN, raw)),
            compatibleContext(),
        )

        val item = state.items.single()
        assertEquals(descriptor.layoutId, item.layoutId)
        assertEquals("Built-in all-types conversion fixture", item.displayName)
        assertEquals(LayoutPublication.PUBLISHED, item.publication)
        assertEquals(LayoutLocalAvailability.READY, item.local.availability)
        assertTrue(item.contentVerified)
        assertEquals(
            LayoutVariantSelectionSource.ONLY_ELIGIBLE,
            item.selection.source,
        )
        val variant = item.variants.single()
        assertEquals(listOf(DeviceClass.PHONE), variant.deviceClasses)
        assertEquals(listOf(LayoutOrientation.LANDSCAPE), variant.orientations)
        assertEquals(SafeAreaPolicy.VIDEO_CONTENT, variant.compatibility.safeAreaPolicy)
        assertNotNull(variant.compatibility.designReferenceHint.resolution)
        assertFalse(item.toString().contains("contentHash"))
    }

    @Test
    fun `host catalog remains not local and never invents content`() {
        val state = catalog.refresh(
            listOf(source(LayoutLocalOrigin.HOST_CATALOG, raw)),
            compatibleContext(),
        )

        val item = state.items.single()
        assertEquals(LayoutLocalAvailability.NOT_LOCAL, item.local.availability)
        assertFalse(item.contentVerified)
        assertNull(item.displayName)
        assertTrue(item.variants.isEmpty())
        assertTrue(state.issues.isEmpty())
    }

    @Test
    fun `corrupt and hash mismatch content fail closed`() {
        val corrupt = raw.copyOfRange(0, 20)
        val corruptState = catalog.refresh(
            listOf(source(LayoutLocalOrigin.PACKAGED_BUILT_IN, corrupt)),
            compatibleContext(),
        )
        assertEquals(LayoutLocalAvailability.INVALID, corruptState.items.single().local.availability)
        assertEquals(LayoutCatalogV2IssueCode.CONTENT_CORRUPT, corruptState.issues.single().code)

        val hashMismatch = raw.toString(Charsets.UTF_8)
            .replace(
                "Built-in all-types conversion fixture",
                "Built-in all-types conversion fixturz",
            )
            .toByteArray()
        val mismatchState = catalog.refresh(
            listOf(source(LayoutLocalOrigin.PACKAGED_BUILT_IN, hashMismatch)),
            compatibleContext(),
        )
        assertEquals(
            LayoutCatalogV2IssueCode.CONTENT_HASH_MISMATCH,
            mismatchState.issues.single().code,
        )
        assertFalse(mismatchState.items.single().contentVerified)
    }

    @Test
    fun `local copy save validates identity content and alignment`() {
        assertEquals(
            LayoutCatalogV2WriteCode.SAVED,
            catalog.saveLocalCopy(descriptor, raw).code,
        )
        assertEquals(
            LayoutLocalAvailability.READY,
            catalog.refresh(
                listOf(source(LayoutLocalOrigin.LOCAL_COPY)),
                compatibleContext(),
            ).items.single().local.availability,
        )
        assertEquals(
            LayoutCatalogV2WriteCode.INVALID_DESCRIPTOR,
            catalog.saveLocalCopy(descriptor.copy(layoutId = "../escape"), raw).code,
        )
        assertEquals(
            LayoutCatalogV2WriteCode.CONTENT_ALIGNMENT_INVALID,
            catalog.saveLocalCopy(descriptor.copy(revision = 2), raw).code,
        )
        assertEquals(
            LayoutCatalogV2LocalWriteResult.INVALID_IDENTITY,
            local.write("../escape", descriptor.revision, raw),
        )
        assertEquals(
            LayoutCatalogV2LocalWriteResult.WRITE_FAILED,
            local.write(
                descriptor.layoutId,
                descriptor.revision,
                ByteArray(1_048_577),
            ),
        )
        assertTrue(
            context.filesDir.resolve("ligase-touch-layout-v2")
                .listFiles()
                .orEmpty()
                .none { it.name.contains("escape") },
        )
    }

    @Test
    fun `atomic readback failure restores existing artifact byte for byte`() {
        assertEquals(LayoutCatalogV2WriteCode.SAVED, catalog.saveLocalCopy(descriptor, raw).code)
        val target = checkNotNull(local.fileForTest(descriptor.layoutId, descriptor.revision))
        val before = target.readBytes()
        val failingStore = LayoutCatalogV2LocalRepository(context) { file ->
            file.writeText("""{"corrupt":true}""")
        }
        val failingCatalog = LayoutCatalogV2Catalog(failingStore, preferences)

        assertEquals(
            LayoutCatalogV2WriteCode.READBACK_FAILED,
            failingCatalog.saveLocalCopy(descriptor, raw).code,
        )
        assertArrayEquals(before, target.readBytes())
    }

    @Test
    fun `refresh and context changes never write or clear preference`() {
        val variantId = descriptor.variants.single().variantId
        assertTrue(preferences.write(descriptor.layoutId, descriptor.revision, variantId))
        val before = preferences.snapshotForTest()

        val first = catalog.refresh(
            listOf(source(LayoutLocalOrigin.PACKAGED_BUILT_IN, raw)),
            compatibleContext(),
        )
        assertEquals(variantId, first.items.single().preferredVariantId)
        assertEquals(before, preferences.snapshotForTest())

        val ineligible = catalog.refresh(
            listOf(source(LayoutLocalOrigin.PACKAGED_BUILT_IN, raw)),
            compatibleContext().copy(shortestSideDp = 1),
        )
        assertNull(ineligible.items.single().preferredVariantId)
        assertEquals(
            LayoutCatalogV2IssueCode.INVALID_STORED_PREFERENCE,
            ineligible.issues.single().code,
        )
        assertEquals(before, preferences.snapshotForTest())
    }

    @Test
    fun `stale and unknown stored preferences are typed and retained`() {
        val variantId = descriptor.variants.single().variantId
        assertTrue(preferences.write(descriptor.layoutId, 2, variantId))
        val beforeStale = preferences.snapshotForTest()
        val stale = catalog.refresh(
            listOf(source(LayoutLocalOrigin.HOST_CATALOG)),
            compatibleContext(),
        )
        assertEquals(
            LayoutCatalogV2IssueCode.STALE_STORED_PREFERENCE,
            stale.issues.single().code,
        )
        assertEquals(beforeStale, preferences.snapshotForTest())

        preferences.clear(descriptor.layoutId)
        val unknown = "00000000-0000-0000-0000-000000000099"
        assertTrue(preferences.write(descriptor.layoutId, descriptor.revision, unknown))
        val beforeUnknown = preferences.snapshotForTest()
        val invalid = catalog.refresh(
            listOf(source(LayoutLocalOrigin.PACKAGED_BUILT_IN, raw)),
            compatibleContext(),
        )
        assertEquals(
            LayoutCatalogV2IssueCode.INVALID_STORED_PREFERENCE,
            invalid.issues.single().code,
        )
        assertEquals(beforeUnknown, preferences.snapshotForTest())
    }

    @Test
    fun `only explicit select and clear actions mutate preference`() {
        catalog.refresh(
            listOf(source(LayoutLocalOrigin.PACKAGED_BUILT_IN, raw)),
            compatibleContext(),
        )
        val variantId = descriptor.variants.single().variantId
        assertEquals(
            LayoutPreferredVariantWriteCode.SAVED,
            catalog.selectPreferredVariant(
                descriptor.layoutId,
                descriptor.revision,
                variantId,
            ).code,
        )
        assertEquals(
            variantId,
            catalog.state.items.single().preferredVariantId,
        )
        val saved = preferences.snapshotForTest()
        assertEquals(
            LayoutPreferredVariantWriteCode.STALE_REVISION,
            catalog.selectPreferredVariant(descriptor.layoutId, 2, variantId).code,
        )
        assertEquals(saved, preferences.snapshotForTest())
        assertEquals(
            LayoutPreferredVariantWriteCode.CLEARED,
            catalog.clearPreferredVariant(descriptor.layoutId).code,
        )
        assertTrue(preferences.snapshotForTest().isEmpty())
    }

    @Test
    fun `retired layout rejects explicit selection with zero writes`() {
        val retired = descriptor.copy(publicationStatus = "retired")
        catalog.refresh(
            listOf(
                LayoutCatalogV2Source(
                    retired,
                    LayoutLocalOrigin.PACKAGED_BUILT_IN,
                    LayoutWorkspaceState.NONE,
                    raw,
                ),
            ),
            compatibleContext(),
        )
        val before = preferences.snapshotForTest()
        assertEquals(
            LayoutPreferredVariantWriteCode.INELIGIBLE_VARIANT,
            catalog.selectPreferredVariant(
                retired.layoutId,
                retired.revision,
                retired.variants.single().variantId,
            ).code,
        )
        assertEquals(before, preferences.snapshotForTest())
    }

    private fun source(
        origin: LayoutLocalOrigin,
        packaged: ByteArray? = null,
    ) = LayoutCatalogV2Source(
        descriptor,
        origin,
        LayoutWorkspaceState.NONE,
        packaged,
    )

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

    private fun compatibleContext() = LayoutCatalogV2Context(
        clientContractVersion = 1,
        layoutRuntimeVersion = 1,
        deviceClass = DeviceClass.PHONE,
        orientation = LayoutOrientation.LANDSCAPE,
        videoAspectRatio = AspectRatio(16, 9),
        shortestSideDp = 720,
        touchTargetDp = 48,
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
