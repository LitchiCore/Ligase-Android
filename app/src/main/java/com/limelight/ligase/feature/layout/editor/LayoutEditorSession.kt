package com.limelight.ligase.feature.layout.editor

import com.limelight.ligase.feature.layout.data.LayoutCatalogRepository
import com.limelight.ligase.feature.layout.data.LayoutRepositoryResult
import com.limelight.ligase.feature.layout.data.TouchKitCodecResult
import com.limelight.ligase.feature.layout.data.TouchKitLayoutCodec
import com.limelight.ligase.feature.layout.data.TouchKitLayoutDocument
import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorElement
import com.limelight.ligase.feature.layout.domain.LayoutEditorError
import com.limelight.ligase.feature.layout.domain.LayoutEditorSaveResult
import com.limelight.ligase.feature.layout.domain.LayoutEditorSessionState
import org.json.JSONObject
import java.util.UUID

class LayoutEditorSession(
    private val repository: LayoutCatalogRepository,
) {
    private val codec = TouchKitLayoutCodec()
    var state: LayoutEditorSessionState = LayoutEditorSessionState()
        private set

    private var document: TouchKitLayoutDocument? = null
    private var elements: List<LayoutEditorElement> = emptyList()
    private var deletedElementIds: Set<String> = emptySet()
    private var addedDescriptors: Map<String, JSONObject> = emptyMap()
    private var isNewCopy = false
    private var variantId: String? = null

    fun createEditableCopy(sourceLayoutId: String): Boolean {
        val loaded = repository.loadDocument(sourceLayoutId)
        if (loaded is LayoutRepositoryResult.Failure) return fail(loaded.error)
        loaded as LayoutRepositoryResult.Success
        document = loaded.value
        elements = loaded.value.elements
        deletedElementIds = emptySet()
        addedDescriptors = emptyMap()
        isNewCopy = true
        variantId = UUID.randomUUID().toString().lowercase()
        state = LayoutEditorSessionState(
            draftId = repository.newStableLayoutId(),
            sourceLayoutId = sourceLayoutId,
            revision = 1,
            variantId = variantId,
            displayName = "${repository.displayName(sourceLayoutId) ?: sourceLayoutId} (copy)",
            canvasAspectRatio = loaded.value.canvasWidth.toFloat() / loaded.value.canvasHeight,
            elements = elements,
        )
        return true
    }

    fun openEditor(layoutId: String): Boolean {
        if (!repository.isEditable(layoutId)) {
            return fail(LayoutEditorError.INVALID_LAYOUT_ID)
        }
        val loaded = repository.loadDocument(layoutId)
        if (loaded is LayoutRepositoryResult.Failure) return fail(loaded.error)
        loaded as LayoutRepositoryResult.Success
        document = loaded.value
        elements = loaded.value.elements
        deletedElementIds = emptySet()
        addedDescriptors = emptyMap()
        isNewCopy = false
        variantId = loaded.value.preferences[TouchKitLayoutCodec.LIGASE_VARIANT_ID] as? String
        state = LayoutEditorSessionState(
            draftId = layoutId,
            sourceLayoutId = layoutId,
            revision = (
                (loaded.value.preferences[TouchKitLayoutCodec.LIGASE_REVISION] as? Long)
                    ?: (loaded.value.preferences[TouchKitLayoutCodec.LIGASE_REVISION] as? Int)?.toLong()
                    ?: 1L
                ),
            variantId = variantId,
            displayName = repository.displayName(layoutId) ?: layoutId,
            canvasAspectRatio = loaded.value.canvasWidth.toFloat() / loaded.value.canvasHeight,
            elements = elements,
        )
        return true
    }

    fun moveElement(elementId: String, x: Float, y: Float): Boolean =
        updateElement(elementId) { current ->
            current.copy(x = x, y = y).takeIf { it.hasValidBounds() }
        }

    fun resizeElement(elementId: String, width: Float, height: Float): Boolean =
        updateElement(elementId) { current ->
            current.copy(width = width, height = height).takeIf { it.hasValidBounds() }
        }

    fun deleteElement(elementId: String): Boolean {
        val current = elements.firstOrNull { it.elementId == elementId }
            ?: return fail(LayoutEditorError.LAYOUT_NOT_FOUND)
        if (current.kind == LayoutControlKind.UNKNOWN) {
            return fail(LayoutEditorError.UNKNOWN_CONTROL_MUTATION)
        }
        elements = elements.filterNot { it.elementId == elementId }
        deletedElementIds = deletedElementIds + elementId
        publishDirty()
        return true
    }

    fun addElement(kind: LayoutControlKind): Boolean {
        val descriptor = defaultDescriptor(kind)
            ?: return fail(LayoutEditorError.UNKNOWN_CONTROL_MUTATION)
        val id = "${kind.name.lowercase()}__instance_${UUID.randomUUID().toString().lowercase()}"
        if (elements.any { it.elementId == id }) {
            return fail(LayoutEditorError.DUPLICATE_ELEMENT_ID)
        }
        val offset = (elements.size % 8) * 0.035f
        val size = when (kind) {
            LayoutControlKind.ANALOG_STICK, LayoutControlKind.DPAD -> 0.18f
            LayoutControlKind.SOFT_KEYBOARD -> 0.08f
            else -> 0.07f
        }
        val element = LayoutEditorElement(
            elementId = id,
            kind = kind,
            x = (0.04f + offset).coerceAtMost(1f - size),
            y = (0.06f + offset).coerceAtMost(1f - size),
            width = size,
            height = size,
            deletable = true,
        )
        elements = elements + element
        addedDescriptors = addedDescriptors + (id to descriptor)
        publishDirty()
        return true
    }

    fun saveDraft(): LayoutEditorSaveResult? {
        val activeDocument = document ?: return failAndNull(LayoutEditorError.NO_ACTIVE_DRAFT)
        val layoutId = state.draftId ?: return failAndNull(LayoutEditorError.NO_ACTIVE_DRAFT)
        state = state.copy(saving = true, error = null)
        val encoded = try {
            codec.withGeometry(activeDocument, elements, deletedElementIds, addedDescriptors)
        } catch (_: Exception) {
            TouchKitCodecResult.Failure(LayoutEditorError.MALFORMED_LAYOUT)
        }
        if (encoded is TouchKitCodecResult.Failure) return failAndNull(encoded.error)
        encoded as TouchKitCodecResult.Success
        val savedRevision = if (isNewCopy) 1L else state.revision + 1L
        val persisted = encoded.value.toMutableMap().apply {
            put(TouchKitLayoutCodec.LIGASE_REVISION, savedRevision)
            variantId?.let { put(TouchKitLayoutCodec.LIGASE_VARIANT_ID, it) }
            if (isNewCopy) {
                state.sourceLayoutId?.let {
                    if (!it.matches(CANONICAL_UUID)) {
                        put(TouchKitLayoutCodec.LIGASE_LEGACY_SOURCE, it)
                    }
                }
            }
        }
        val saved = if (isNewCopy) {
            repository.saveNew(layoutId, state.displayName, persisted)
        } else {
            repository.saveExisting(layoutId, persisted)
        }
        if (saved is LayoutRepositoryResult.Failure) return failAndNull(saved.error)
        document = activeDocument.copy(
            preferences = persisted,
            elements = elements,
        )
        deletedElementIds = emptySet()
        addedDescriptors = emptyMap()
        isNewCopy = false
        variantId = null
        state = state.copy(
            revision = savedRevision,
            dirty = false,
            saving = false,
            error = null,
        )
        return LayoutEditorSaveResult(layoutId, state.displayName)
    }

    fun discardDraft() {
        document = null
        elements = emptyList()
        deletedElementIds = emptySet()
        addedDescriptors = emptyMap()
        isNewCopy = false
        state = LayoutEditorSessionState()
    }

    private fun updateElement(
        elementId: String,
        transform: (LayoutEditorElement) -> LayoutEditorElement?,
    ): Boolean {
        val index = elements.indexOfFirst { it.elementId == elementId }
        if (index < 0) return fail(LayoutEditorError.LAYOUT_NOT_FOUND)
        val updated = transform(elements[index]) ?: return fail(LayoutEditorError.INVALID_BOUNDS)
        elements = elements.toMutableList().also { it[index] = updated }
        publishDirty()
        return true
    }

    private fun publishDirty() {
        state = state.copy(elements = elements, dirty = true, saving = false, error = null)
    }

    private fun fail(error: LayoutEditorError): Boolean {
        state = state.copy(saving = false, error = error)
        return false
    }

    private fun failAndNull(error: LayoutEditorError): LayoutEditorSaveResult? {
        fail(error)
        return null
    }

    private fun defaultDescriptor(kind: LayoutControlKind): JSONObject? = when (kind) {
        LayoutControlKind.KEYBOARD_KEY -> JSONObject()
            .put("type", 0).put("name", "A").put("code", 29)
        LayoutControlKind.MOUSE_BUTTON -> JSONObject()
            .put("type", 1).put("name", "ML").put("code", 1)
        LayoutControlKind.ANALOG_STICK -> JSONObject()
            .put("type", 2)
            .put("upCode", 51).put("downCode", 47)
            .put("leftCode", 29).put("rightCode", 32).put("middleCode", 59)
        LayoutControlKind.DPAD -> JSONObject()
            .put("type", 3)
            .put("upCode", 51).put("downCode", 47)
            .put("leftCode", 29).put("rightCode", 32)
        LayoutControlKind.SOFT_KEYBOARD -> JSONObject().put("type", 8)
        LayoutControlKind.UNKNOWN -> null
    }

    companion object {
        private val CANONICAL_UUID = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )
    }
}

private fun LayoutEditorElement.hasValidBounds(): Boolean =
    x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() &&
        x >= 0f && y >= 0f && width > 0f && height > 0f &&
        x + width <= 1f && y + height <= 1f
