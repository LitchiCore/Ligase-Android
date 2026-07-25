package com.limelight.ligase.feature.input.layout.v3.domain

import com.limelight.ligase.feature.input.layout.v3.serialization.StrictJsonV3Value

data class TouchLayoutV3Document(
    val layoutId: String,
    val revision: Long,
    val nextKeyboardBatchOrdinal: Long,
    val displayName: String,
    val extensions: Map<String, StrictJsonV3Value>,
    val variants: List<TouchLayoutV3Variant>,
    val contentHash: String,
)

data class TouchLayoutV3Variant(
    val variantId: String,
    val deviceClasses: List<DeviceClass>,
    val orientations: List<LayoutOrientation>,
    val recommendation: LayoutRecommendation,
    val canvas: IntSize,
    val elements: List<TouchLayoutV3Element>,
)

enum class DeviceClass { PHONE, TABLET }
enum class LayoutOrientation { PORTRAIT, LANDSCAPE }
enum class HorizontalAnchor { LEFT, CENTER, RIGHT }
enum class VerticalAnchor { TOP, BOTTOM }

data class IntSize(val width: Int, val height: Int)
data class IntRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Long get() = x.toLong() + width
    val bottom: Long get() = y.toLong() + height
}
data class AnchoredRect(
    val horizontalOffset: Int,
    val verticalOffset: Int,
    val width: Int,
    val height: Int,
)
data class AspectRatio(val numerator: Int, val denominator: Int)

data class LayoutRecommendation(
    val preferredAspectRatio: AspectRatio,
    val minAspectRatio: AspectRatio,
    val maxAspectRatio: AspectRatio,
    val minShortestSideDp: Int,
    val minTouchTargetDp: Int,
    val referenceDensityDpi: Int?,
    val referenceResolution: IntSize,
)

data class TouchLayoutV3Element(
    val elementId: String,
    val kind: ControlKind,
    val rect: AnchoredRect,
    val anchorX: HorizontalAnchor,
    val anchorY: VerticalAnchor,
    val zOrder: Int,
    val enabled: Boolean,
    val hidden: Boolean,
    val opacityPermille: Int,
    val payload: ControlPayload,
    val sourceReference: String?,
)

enum class ControlKind {
    KEYBOARD, MOUSE, ANALOG, DPAD, CUSTOM_KEYS, RADIAL, SCROLL, COMBO, SOFT_KEYBOARD, GYRO,
}

enum class InputCodeNamespace { ANDROID_KEY_CODE, USB_HID_KEYBOARD_USAGE }
data class InputCode(val namespace: InputCodeNamespace, val code: Int)
enum class Trigger { HOLD, TOGGLE, TAP, TIMED_HOLD }

data class Appearance(
    val label: String,
    val description: String,
    val shape: String,
    val showPhysicalKeyNames: Boolean,
)

sealed interface ControlPayload {
    val kind: ControlKind
}

data class KeyboardPayload(
    val inputCode: InputCode,
    val appearance: Appearance,
    val trigger: Trigger,
    val timedHoldMs: Int?,
) : ControlPayload {
    override val kind = ControlKind.KEYBOARD
}

data class MousePayload(
    val button: String,
    val appearance: Appearance,
    val trigger: Trigger,
    val timedHoldMs: Int?,
) : ControlPayload {
    override val kind = ControlKind.MOUSE
}

data class DirectionalPayload(
    override val kind: ControlKind,
    val up: List<InputCode>,
    val down: List<InputCode>,
    val left: List<InputCode>,
    val right: List<InputCode>,
    val press: List<InputCode>?,
    val diagonalPolicy: String,
) : ControlPayload

data class ChordPayload(
    override val kind: ControlKind,
    val keys: List<InputCode>,
    val trigger: Trigger,
    val timedHoldMs: Int?,
    val appearance: Appearance,
    val sticky: Boolean?,
) : ControlPayload

data class RadialAction(val keys: List<InputCode>, val label: String)
data class RadialPayload(
    val label: String,
    val actions: List<RadialAction>,
) : ControlPayload {
    override val kind = ControlKind.RADIAL
}

data class ScrollPayload(
    val direction: String,
    val step: Int,
    val continuous: Boolean,
    val cadenceMs: Int?,
    val appearance: Appearance,
    val trigger: Trigger,
    val timedHoldMs: Int?,
) : ControlPayload {
    override val kind = ControlKind.SCROLL
}

data object SoftKeyboardPayload : ControlPayload {
    override val kind = ControlKind.SOFT_KEYBOARD
}

data class GyroPayload(
    val target: String,
    val frame: String,
    val axes: String,
    val scale: Int,
    val deadzoneMilliDegreesPerSecond: Int,
    val invertX: Boolean,
    val invertY: Boolean,
) : ControlPayload {
    override val kind = ControlKind.GYRO
}

data class LayoutDescriptorProjection(
    val layoutId: String,
    val revision: Long,
    val variants: List<DescriptorVariantProjection>,
)

data class DescriptorVariantProjection(
    val variantId: String,
    val deviceClasses: List<DeviceClass>,
    val orientations: List<LayoutOrientation>,
)

data class EditorTargetViewport(
    val widthPx: Int,
    val heightPx: Int,
    val orientation: LayoutOrientation,
)

data class MappedElementRect(
    val elementId: String,
    val rect: IntRect,
)
