package com.limelight.ligase.feature.layout

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.limelight.TouchKitLayoutNames
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader
import com.limelight.ligase.LigasePreferences
import com.limelight.ligase.feature.layout.data.LayoutCatalogRepository
import com.limelight.ligase.feature.layout.data.LayoutRepositoryResult
import com.limelight.ligase.feature.layout.domain.LayoutCatalogSource
import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorError
import com.limelight.ligase.feature.layout.editor.LayoutEditorSession
import com.limelight.ligase.input.LigaseTouchLayout
import com.limelight.ligase.input.LigaseTouchLayoutRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LayoutCatalogRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: LayoutCatalogRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TouchKitLayoutNames.getValues(context).forEach { layoutId ->
            context.getSharedPreferences(layoutId, Context.MODE_PRIVATE).edit().clear().commit()
        }
        context.getSharedPreferences("not-a-stable-layout-id", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("touchkit_layout_registry", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("touchkit_layout_names", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("ligase_product_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        repository = LayoutCatalogRepository(context)
    }

    @Test
    fun `built in layout is read only and materializes real controls`() {
        val result = repository.catalog() as LayoutRepositoryResult.Success
        val builtIn = result.value.single()

        assertEquals("OSC_Keyboard", builtIn.layoutId)
        assertEquals(LayoutCatalogSource.BUILT_IN, builtIn.source)
        assertFalse(builtIn.editable)
        assertTrue(builtIn.controlCount > 20)
        assertEquals(16f / 9f, builtIn.previewAspectRatio, 0.001f)
    }

    @Test
    fun `copy on write preserves source and unknown fields with stable id`() {
        val source = TouchKitLayoutNames.getValues(context).single()
        context.getSharedPreferences(source, Context.MODE_PRIVATE).edit()
            .putString("future_field", "keep-me")
            .putString(
                "__touchkit_dynamic_elements",
                JSONArray().put(
                    JSONObject()
                        .put("elementId", "future__instance_1")
                        .put("type", 5)
                        .put("vendorDescriptor", "keep-descriptor"),
                ).toString(),
            )
            .putString(
                "future__instance_1",
                JSONObject()
                    .put("LEFT", 300).put("TOP", 300)
                    .put("WIDTH", 180).put("HEIGHT", 180)
                    .put("ENABLED", true)
                    .toString(),
            )
            .putString(
                "future_control",
                JSONObject()
                    .put("LEFT", 100).put("TOP", 100)
                    .put("WIDTH", 120).put("HEIGHT", 120)
                    .put("ENABLED", true).put("vendorFuture", 42)
                    .toString(),
            )
            .commit()
        val sourceBefore = context.getSharedPreferences(source, Context.MODE_PRIVATE).all.toMap()
        val session = LayoutEditorSession(repository)

        assertTrue(session.createEditableCopy(source))
        val draftId = requireNotNull(session.state.draftId)
        assertTrue(draftId.matches(Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )))
        val movable = session.state.elements.first { it.kind != LayoutControlKind.UNKNOWN }
        assertTrue(session.moveElement(movable.elementId, 0.01f, 0.01f))
        val saved = session.saveDraft()

        assertNotNull(saved)
        assertEquals(sourceBefore, context.getSharedPreferences(source, Context.MODE_PRIVATE).all)
        val savedPrefs = context.getSharedPreferences(draftId, Context.MODE_PRIVATE)
        assertEquals("keep-me", savedPrefs.getString("future_field", null))
        assertEquals(
            42,
            JSONObject(savedPrefs.getString("future_control", null)!!).getInt("vendorFuture"),
        )
        assertEquals(
            "keep-descriptor",
            JSONArray(savedPrefs.getString("__touchkit_dynamic_elements", null))
                .getJSONObject(0)
                .getString("vendorDescriptor"),
        )
        val local = (repository.catalog() as LayoutRepositoryResult.Success)
            .value.single { it.layoutId == draftId }
        assertEquals(LayoutCatalogSource.LOCAL_COPY, local.source)
        assertTrue(local.editable)
        assertEquals(1L, local.revision)
        assertTrue(local.variantId!!.matches(Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )))
        assertEquals(source, local.legacySourceReference)
    }

    @Test
    fun `unknown control is retained but destructive mutation fails closed`() {
        val source = TouchKitLayoutNames.getValues(context).single()
        context.getSharedPreferences(source, Context.MODE_PRIVATE).edit()
            .putString(
                "future_control",
                JSONObject()
                    .put("LEFT", 100).put("TOP", 100)
                    .put("WIDTH", 120).put("HEIGHT", 120)
                    .put("ENABLED", true)
                    .toString(),
            )
            .commit()
        val session = LayoutEditorSession(repository)
        assertTrue(session.createEditableCopy(source))

        assertFalse(session.deleteElement("future_control"))
        assertEquals(LayoutEditorError.UNKNOWN_CONTROL_MUTATION, session.state.error)
        assertTrue(session.state.elements.any { it.elementId == "future_control" })
    }

    @Test
    fun `malformed descriptor fails closed before draft creation`() {
        val source = TouchKitLayoutNames.getValues(context).single()
        context.getSharedPreferences(source, Context.MODE_PRIVATE).edit()
            .putString("__touchkit_dynamic_elements", "{not-an-array")
            .commit()
        val session = LayoutEditorSession(repository)

        assertFalse(session.createEditableCopy(source))
        assertNull(session.state.draftId)
        assertEquals(LayoutEditorError.MALFORMED_LAYOUT, session.state.error)
        assertEquals(listOf(source), TouchKitLayoutNames.getValues(context).toList())
    }

    @Test
    fun `move resize and add reject invalid bounds and persist valid controls`() {
        val session = LayoutEditorSession(repository)
        assertTrue(session.createEditableCopy("OSC_Keyboard"))
        val first = session.state.elements.first()

        assertFalse(session.moveElement(first.elementId, 0.99f, 0.99f))
        assertEquals(LayoutEditorError.INVALID_BOUNDS, session.state.error)
        assertFalse(session.resizeElement(first.elementId, 2f, 2f))
        assertTrue(session.addElement(LayoutControlKind.DPAD))
        assertTrue(session.addElement(LayoutControlKind.SOFT_KEYBOARD))
        val result = session.saveDraft()
        assertNotNull(result)

        val reopened = LayoutEditorSession(repository)
        assertTrue(reopened.openEditor(result!!.layoutId))
        assertTrue(reopened.state.elements.any { it.kind == LayoutControlKind.DPAD })
        assertTrue(reopened.state.elements.any { it.kind == LayoutControlKind.SOFT_KEYBOARD })
        val addedDpad = reopened.state.elements.first {
            it.kind == LayoutControlKind.DPAD && "__instance_" in it.elementId
        }
        assertTrue(reopened.deleteElement(addedDpad.elementId))
        assertTrue(reopened.moveElement(reopened.state.elements.first().elementId, 0.01f, 0.01f))
        assertNotNull(reopened.saveDraft())
        assertEquals(2L, reopened.state.revision)
        val afterDelete = LayoutEditorSession(repository)
        assertTrue(afterDelete.openEditor(result.layoutId))
        assertFalse(afterDelete.state.elements.any { it.elementId == addedDpad.elementId })
    }

    @Test
    fun `failed registration removes invisible preference file`() {
        val badId = "not-a-stable-layout-id"
        val beforeIds = TouchKitLayoutNames.getValues(context).toList()
        val result = repository.saveNew(
            badId,
            "bad",
            mapOf("unknown" to "value"),
        )

        assertTrue(result is LayoutRepositoryResult.Failure)
        assertTrue(context.getSharedPreferences(badId, Context.MODE_PRIVATE).all.isEmpty())
        assertEquals(beforeIds, TouchKitLayoutNames.getValues(context).toList())
    }

    @Test
    fun `v1 catalog cannot become product global selection`() {
        val session = LayoutEditorSession(repository)
        assertTrue(session.createEditableCopy("OSC_Keyboard"))
        val saved = requireNotNull(session.saveDraft())

        val catalog = (repository.catalog() as LayoutRepositoryResult.Success).value
        val layouts = catalog.map { LigaseTouchLayout(it.layoutId, it.displayName) }
        assertFalse(LigaseTouchLayoutRepository(context).select(saved.layoutId, layouts))
        assertNull(
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, null),
        )
        assertTrue(
            (LayoutCatalogRepository(context).catalog() as LayoutRepositoryResult.Success)
                .value.any { it.layoutId == saved.layoutId },
        )
    }

    @Test
    fun `cancel leaves catalog and source unchanged`() {
        val source = TouchKitLayoutNames.getValues(context).single()
        val before = context.getSharedPreferences(source, Context.MODE_PRIVATE).all.toMap()
        val session = LayoutEditorSession(repository)
        assertTrue(session.createEditableCopy(source))
        assertTrue(session.addElement(LayoutControlKind.KEYBOARD_KEY))
        session.discardDraft()

        assertNull(session.state.draftId)
        assertEquals(before, context.getSharedPreferences(source, Context.MODE_PRIVATE).all)
        assertEquals(listOf(source), TouchKitLayoutNames.getValues(context).toList())
    }
}
