package com.limelight.ligase.feature.library.ui.manual

internal data class ManualLibraryEditActions(
    val saveEnabled: Boolean,
    val cancelEnabled: Boolean,
)

internal fun manualLibraryEditActions(
    isDirty: Boolean,
    editingEnabled: Boolean,
    saving: Boolean,
): ManualLibraryEditActions = ManualLibraryEditActions(
    saveEnabled = isDirty && editingEnabled && !saving,
    cancelEnabled = !saving,
)

internal fun manualLibraryMoveDirection(
    dragOffset: Float,
    moveThreshold: Float,
): Int? = when {
    dragOffset >= moveThreshold -> 1
    dragOffset <= -moveThreshold -> -1
    else -> null
}
