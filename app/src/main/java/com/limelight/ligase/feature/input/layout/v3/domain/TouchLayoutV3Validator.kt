package com.limelight.ligase.feature.input.layout.v3.domain

import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Exception
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object TouchLayoutV3Validator {
    fun validate(document: TouchLayoutV3Document) {
        if (document.nextKeyboardBatchOrdinal < 1L) fail("invalidNextKeyboardBatchOrdinal")
        val ids = document.variants.map { it.variantId }
        if (ids != ids.sorted()) fail("nonCanonicalVariantOrder")
        if (ids.toSet().size != ids.size) fail("invalidVariantId")
        document.variants.forEachIndexed { index, variant -> validateVariant(variant, "/variants/$index") }
    }

    fun validateDescriptorAlignment(document: TouchLayoutV3Document, descriptor: LayoutDescriptorProjection) {
        if (descriptor.layoutId != document.layoutId || descriptor.revision != document.revision) {
            fail("descriptorIdentityMismatch")
        }
        val content = document.variants.associateBy { it.variantId }
        val projected = descriptor.variants.associateBy { it.variantId }
        if (projected.size != descriptor.variants.size) fail("duplicateDescriptorVariant")
        if (content.keys != projected.keys) fail("descriptorVariantSetMismatch")
        content.forEach { (id, variant) ->
            val expected = projected.getValue(id)
            if (variant.deviceClasses != expected.deviceClasses || variant.orientations != expected.orientations) {
                fail("descriptorEligibilityMismatch", id)
            }
        }
    }

    private fun validateVariant(variant: TouchLayoutV3Variant, path: String) {
        val classes = listOf(DeviceClass.PHONE, DeviceClass.TABLET)
        val orientations = listOf(LayoutOrientation.PORTRAIT, LayoutOrientation.LANDSCAPE)
        if (variant.deviceClasses != variant.deviceClasses.distinct().sortedBy(classes::indexOf)) {
            fail("nonCanonicalSetOrder", "$path/deviceClasses")
        }
        if (variant.orientations != variant.orientations.distinct().sortedBy(orientations::indexOf)) {
            fail("nonCanonicalSetOrder", "$path/orientations")
        }
        val recommendation = variant.recommendation
        if (
            compare(recommendation.minAspectRatio, recommendation.preferredAspectRatio) > 0 ||
            compare(recommendation.preferredAspectRatio, recommendation.maxAspectRatio) > 0
        ) fail("invalidAspectRange", "$path/recommendation")
        val ids = variant.elements.map { it.elementId }
        if (ids.toSet().size != ids.size) fail("duplicateElementId", "$path/elements")
        val zOrders = variant.elements.map { it.zOrder }
        if (zOrders.toSet().size != zOrders.size) fail("duplicateZOrder", "$path/elements")
        if (variant.elements != variant.elements.sortedBy { it.zOrder }) {
            fail("nonCanonicalElementOrder", "$path/elements")
        }
        variant.elements.forEachIndexed { index, element ->
            val rect = LayoutV3Geometry.resolve(variant.canvas, element)
            if (!LayoutV3Geometry.isVisible(rect, IntRect(0, 0, variant.canvas.width, variant.canvas.height))) {
                fail("rectNotVisible", "$path/elements/$index/rect")
            }
        }
    }

    private fun compare(left: AspectRatio, right: AspectRatio): Int =
        (left.numerator.toLong() * right.denominator)
            .compareTo(right.numerator.toLong() * left.denominator)

    private fun fail(code: String, path: String = ""): Nothing =
        throw TouchLayoutV3Exception(code, path)
}

object LayoutV3Geometry {
    fun roundHalfUp(numerator: Long, denominator: Long): Int {
        if (denominator <= 0) fail("invalidScale")
        val doubled = Math.multiplyExact(numerator, 2L)
        val adjusted = Math.addExact(doubled, denominator)
        return Math.floorDiv(adjusted, Math.multiplyExact(denominator, 2L)).checkedInt()
    }

    fun resolve(canvas: IntSize, element: TouchLayoutV3Element): IntRect {
        val rect = element.rect
        val x = anchorOrigin(canvas.width, rect.width, rect.horizontalOffset, element.anchorX)
        val y = anchorOrigin(canvas.height, rect.height, rect.verticalOffset, element.anchorY)
        return IntRect(x, y, rect.width, rect.height)
    }

    fun rebase(canvas: IntSize, rect: IntRect): Pair<Pair<HorizontalAnchor, VerticalAnchor>, AnchoredRect> {
        val centerXTwice = 2L * rect.x + rect.width
        val anchorX = when {
            centerXTwice * 5L < canvas.width.toLong() * 4L -> HorizontalAnchor.LEFT
            centerXTwice * 5L <= canvas.width.toLong() * 6L -> HorizontalAnchor.CENTER
            else -> HorizontalAnchor.RIGHT
        }
        val anchorY =
            if (2L * rect.y + rect.height < canvas.height) VerticalAnchor.TOP else VerticalAnchor.BOTTOM
        val horizontalOffset = when (anchorX) {
            HorizontalAnchor.LEFT -> rect.x
            HorizontalAnchor.CENTER -> rect.x - roundHalfUp(canvas.width.toLong() - rect.width, 2)
            HorizontalAnchor.RIGHT -> (canvas.width.toLong() - rect.x - rect.width).checkedInt()
        }
        val verticalOffset = when (anchorY) {
            VerticalAnchor.TOP -> rect.y
            VerticalAnchor.BOTTOM -> (canvas.height.toLong() - rect.y - rect.height).checkedInt()
        }
        return (anchorX to anchorY) to AnchoredRect(horizontalOffset, verticalOffset, rect.width, rect.height)
    }

    fun map(
        canvas: IntSize,
        element: TouchLayoutV3Element,
        overlay: IntRect,
        minimumTargetPx: Int = 1,
    ): IntRect = mapAnchoredRect(
        canvas,
        element.rect,
        element.anchorX,
        element.anchorY,
        overlay,
        minimumTargetPx,
    )

    fun mapAnchoredRect(
        canvas: IntSize,
        source: AnchoredRect,
        anchorX: HorizontalAnchor,
        anchorY: VerticalAnchor,
        overlay: IntRect,
        minimumTargetPx: Int = 1,
    ): IntRect {
        if (canvas.width <= 0 || canvas.height <= 0) fail("invalidCanvas")
        if (overlay.width <= 0 || overlay.height <= 0) fail("invalidOverlayBounds")
        if (source.width <= 0 || source.height <= 0 || minimumTargetPx <= 0) {
            fail("invalidElementRect")
        }
        val width = roundHalfUp(source.width.toLong() * overlay.height, canvas.height.toLong())
        val height = roundHalfUp(source.height.toLong() * overlay.height, canvas.height.toLong())
        val offsetX = roundHalfUp(source.horizontalOffset.toLong() * overlay.width, canvas.width.toLong())
        val offsetY = roundHalfUp(source.verticalOffset.toLong() * overlay.height, canvas.height.toLong())
        if (width < minimumTargetPx || height < minimumTargetPx) fail("targetBelowMinimum")
        val x = Math.addExact(
            overlay.x.toLong(),
            anchorOrigin(overlay.width, width, offsetX, anchorX).toLong(),
        ).checkedInt()
        val y = Math.addExact(
            overlay.y.toLong(),
            anchorOrigin(overlay.height, height, offsetY, anchorY).toLong(),
        ).checkedInt()
        return IntRect(x, y, width, height).also {
            if (!isVisible(it, overlay)) fail("targetNotVisible")
        }
    }

    fun mapEditorElement(
        canvas: IntSize,
        element: LayoutV3AnchoredElementGeometry,
        overlay: IntRect,
        minimumTargetPx: Int = 1,
    ): IntRect = mapAnchoredRect(
        canvas,
        element.rect,
        element.anchorX,
        element.anchorY,
        overlay,
        minimumTargetPx,
    )

    /**
     * Authoritative inverse for editor gesture commits. Element endpoints are
     * intentionally not clamped; callers validate the resulting canonical
     * visibility before mutating a draft.
     */
    fun unmapResolvedRect(
        canvas: IntSize,
        anchorX: HorizontalAnchor,
        anchorY: VerticalAnchor,
        overlay: IntRect,
        target: IntRect,
    ): IntRect {
        if (overlay.width <= 0 || overlay.height <= 0 || target.width <= 0 || target.height <= 0) {
            fail("invalidOverlayBounds")
        }
        val width = roundHalfUp(target.width.toLong() * canvas.height, overlay.height.toLong())
        val height = roundHalfUp(target.height.toLong() * canvas.height, overlay.height.toLong())
        if (width <= 0 || height <= 0) fail("targetBelowMinimum")
        val targetX = target.x.toLong() - overlay.x
        val targetY = target.y.toLong() - overlay.y
        val horizontalOffsetPx = when (anchorX) {
            HorizontalAnchor.LEFT -> targetX
            HorizontalAnchor.CENTER ->
                targetX - roundHalfUp(overlay.width.toLong() - target.width, 2)
            HorizontalAnchor.RIGHT -> overlay.width.toLong() - targetX - target.width
        }
        val verticalOffsetPx = when (anchorY) {
            VerticalAnchor.TOP -> targetY
            VerticalAnchor.BOTTOM -> overlay.height.toLong() - targetY - target.height
        }
        val horizontalOffset = roundHalfUp(
            horizontalOffsetPx * canvas.width,
            overlay.width.toLong(),
        )
        val verticalOffset = roundHalfUp(
            verticalOffsetPx * canvas.height,
            overlay.height.toLong(),
        )
        val x = anchorOrigin(canvas.width, width, horizontalOffset, anchorX)
        val y = anchorOrigin(canvas.height, height, verticalOffset, anchorY)
        return IntRect(x, y, width, height)
    }

    fun isVisible(rect: IntRect, bounds: IntRect): Boolean {
        val width = minOf(rect.right, bounds.right) - maxOf(rect.x.toLong(), bounds.x.toLong())
        val height = minOf(rect.bottom, bounds.bottom) - maxOf(rect.y.toLong(), bounds.y.toLong())
        return width >= 1L && height >= 1L
    }

    fun hitTest(elements: List<Pair<TouchLayoutV3Element, IntRect>>, x: Int, y: Int): String? =
        elements.asSequence()
            .sortedByDescending { it.first.zOrder }
            .firstOrNull { (element, rect) ->
                element.enabled && !element.hidden &&
                    x.toLong() >= rect.x && x.toLong() < rect.right &&
                    y.toLong() >= rect.y && y.toLong() < rect.bottom
            }?.first?.elementId

    private fun anchorOrigin(span: Int, size: Int, offset: Int, anchor: Any): Int = when (anchor) {
        HorizontalAnchor.LEFT, VerticalAnchor.TOP -> offset
        HorizontalAnchor.CENTER -> roundHalfUp(span.toLong() - size, 2) + offset
        HorizontalAnchor.RIGHT, VerticalAnchor.BOTTOM -> (span.toLong() - offset - size).checkedInt()
        else -> fail("invalidAnchor")
    }

    private fun Long.checkedInt(): Int {
        if (this !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) fail("integerOverflow")
        return toInt()
    }

    private fun fail(code: String): Nothing = throw TouchLayoutV3Exception(code)
}

object LayoutV3KeyboardBatchIdentity {
    fun canonicalKeys(keys: Set<InputCode>): List<InputCode> {
        if (keys.isEmpty()) fail("empty")
        if (keys.size > 32) fail("tooManyKeys")
        return keys.sortedWith(
            compareBy<InputCode> {
                when (it.namespace) {
                    InputCodeNamespace.ANDROID_KEY_CODE -> 0
                    InputCodeNamespace.USB_HID_KEYBOARD_USAGE -> 1
                }
            }.thenBy { it.code },
        )
    }

    fun elementId(layoutId: String, batchOrdinal: Long, inputCode: InputCode): String {
        if (batchOrdinal < 1L) fail("invalidNextKeyboardBatchOrdinal")
        val namespace = UUID.fromString(layoutId)
        val namespaceBytes = ByteBuffer.allocate(16)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val inputNamespace = when (inputCode.namespace) {
            InputCodeNamespace.ANDROID_KEY_CODE -> "androidKeyCode"
            InputCodeNamespace.USB_HID_KEYBOARD_USAGE -> "usbHidKeyboardUsage"
        }
        val name = "keyboard-batch:$batchOrdinal:$inputNamespace:${inputCode.code}"
            .toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1").digest(namespaceBytes + name).copyOf(16)
        digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
        val bytes = ByteBuffer.wrap(digest)
        return UUID(bytes.long, bytes.long).toString()
    }

    private fun fail(code: String): Nothing = throw TouchLayoutV3Exception(code)
}
