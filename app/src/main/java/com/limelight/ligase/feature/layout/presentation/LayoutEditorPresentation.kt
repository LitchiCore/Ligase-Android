package com.limelight.ligase.feature.layout.presentation

import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorElement

enum class LayoutHallPaneMode {
    SINGLE_COLUMN,
    TWO_COLUMN,
}

enum class LayoutSaveNavigation {
    WAIT,
    HALL,
    STAY_EDITOR,
}

fun layoutSaveNavigation(
    pendingSave: Boolean,
    saving: Boolean,
    dirty: Boolean,
    hasError: Boolean,
): LayoutSaveNavigation = when {
    !pendingSave || saving -> LayoutSaveNavigation.WAIT
    hasError -> LayoutSaveNavigation.STAY_EDITOR
    !dirty -> LayoutSaveNavigation.HALL
    else -> LayoutSaveNavigation.WAIT
}

fun layoutHallPaneMode(availableWidthDp: Int): LayoutHallPaneMode =
    if (availableWidthDp >= 720) {
        LayoutHallPaneMode.TWO_COLUMN
    } else {
        LayoutHallPaneMode.SINGLE_COLUMN
    }

fun LayoutEditorElement.moveBy(
    deltaX: Float,
    deltaY: Float,
): LayoutEditorElement {
    if (kind == LayoutControlKind.UNKNOWN) return this
    return copy(
        x = (x + deltaX).coerceIn(0f, 1f - width),
        y = (y + deltaY).coerceIn(0f, 1f - height),
    )
}

fun LayoutEditorElement.resizeBy(zoom: Float): LayoutEditorElement {
    if (kind == LayoutControlKind.UNKNOWN || !zoom.isFinite() || zoom <= 0f) return this
    return copy(
        width = (width * zoom).coerceIn(MIN_CONTROL_SIZE, 1f - x),
        height = (height * zoom).coerceIn(MIN_CONTROL_SIZE, 1f - y),
    )
}

fun LayoutEditorElement.canDelete(): Boolean =
    deletable && kind != LayoutControlKind.UNKNOWN

private const val MIN_CONTROL_SIZE = 0.05f
