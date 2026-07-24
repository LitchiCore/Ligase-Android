package com.limelight.ligase.feature.layout.domain

enum class LayoutCatalogSource {
    BUILT_IN,
    LOCAL_COPY,
}

enum class LayoutControlKind(val touchKitType: Int?) {
    KEYBOARD_KEY(0),
    MOUSE_BUTTON(1),
    ANALOG_STICK(2),
    DPAD(3),
    SOFT_KEYBOARD(8),
    UNKNOWN(null),
}

enum class LayoutEditorError {
    LAYOUT_NOT_FOUND,
    INVALID_LAYOUT_ID,
    UNSUPPORTED_VALUE_TYPE,
    MALFORMED_LAYOUT,
    UNKNOWN_CONTROL_MUTATION,
    INVALID_BOUNDS,
    DUPLICATE_ELEMENT_ID,
    SAVE_FAILED,
    NO_ACTIVE_DRAFT,
}

data class LayoutCatalogItem(
    val layoutId: String,
    val displayName: String,
    val source: LayoutCatalogSource,
    val editable: Boolean,
    val controlCount: Int,
    val previewAspectRatio: Float,
    val revision: Long?,
    val variantId: String?,
    val legacySourceReference: String?,
)

data class LayoutCatalogUiState(
    val items: List<LayoutCatalogItem> = emptyList(),
    val selectedLayoutId: String? = null,
    val loading: Boolean = false,
    val error: LayoutEditorError? = null,
)

data class LayoutEditorElement(
    val elementId: String,
    val kind: LayoutControlKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val deletable: Boolean,
)

data class LayoutEditorSessionState(
    val draftId: String? = null,
    val sourceLayoutId: String? = null,
    val revision: Long = 1,
    val variantId: String? = null,
    val displayName: String = "",
    val canvasAspectRatio: Float = 16f / 9f,
    val elements: List<LayoutEditorElement> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: LayoutEditorError? = null,
)

data class LayoutEditorSaveResult(
    val layoutId: String,
    val displayName: String,
)
