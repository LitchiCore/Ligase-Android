package com.limelight.ligase.feature.input.layout.v2.domain

import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Exception

object TouchLayoutV2Validator {
    fun validate(document: TouchLayoutV2Document) {
        if (document.variants.map { it.variantId } != document.variants.map { it.variantId }.sorted()) {
            fail("nonCanonicalVariantOrder")
        }
        if (document.variants.map { it.variantId }.toSet().size != document.variants.size) {
            fail("invalidVariantId")
        }
        document.variants.forEachIndexed { index, variant -> validateVariant(variant, "/variants/$index") }
    }

    fun validateDescriptorAlignment(
        document: TouchLayoutV2Document,
        descriptor: LayoutDescriptorProjection,
    ) {
        if (descriptor.layoutId != document.layoutId || descriptor.revision != document.revision) {
            fail("descriptorIdentityMismatch")
        }
        val contentById = document.variants.associateBy { it.variantId }
        val descriptorById = descriptor.variants.associateBy { it.variantId }
        if (descriptorById.size != descriptor.variants.size) fail("duplicateDescriptorVariant")
        if (contentById.keys != descriptorById.keys) fail("descriptorVariantSetMismatch")
        contentById.forEach { (id, content) ->
            val projected = descriptorById.getValue(id)
            if (
                content.deviceClasses != projected.deviceClasses ||
                content.orientations != projected.orientations
            ) {
                fail("descriptorEligibilityMismatch", id)
            }
        }
    }

    private fun validateVariant(variant: TouchLayoutV2Variant, path: String) {
        if (variant.deviceClasses != variant.deviceClasses.distinct()) fail("nonCanonicalSetOrder", "$path/deviceClasses")
        if (variant.orientations != variant.orientations.distinct()) fail("nonCanonicalSetOrder", "$path/orientations")
        val classOrder = listOf(DeviceClass.PHONE, DeviceClass.TABLET)
        val orientationOrder = listOf(LayoutOrientation.PORTRAIT, LayoutOrientation.LANDSCAPE)
        if (variant.deviceClasses != variant.deviceClasses.sortedBy(classOrder::indexOf)) {
            fail("nonCanonicalSetOrder", "$path/deviceClasses")
        }
        if (variant.orientations != variant.orientations.sortedBy(orientationOrder::indexOf)) {
            fail("nonCanonicalSetOrder", "$path/orientations")
        }
        val recommendation = variant.recommendation
        if (
            compare(recommendation.minAspectRatio, recommendation.preferredAspectRatio) > 0 ||
            compare(recommendation.preferredAspectRatio, recommendation.maxAspectRatio) > 0
        ) {
            fail("invalidAspectRange", "$path/recommendation")
        }
        if (variant.elements.map { it.elementId }.toSet().size != variant.elements.size) {
            fail("duplicateElementId", "$path/elements")
        }
        val expectedOrder = variant.elements.sortedWith(compareBy<TouchLayoutV2Element> { it.zOrder }.thenBy { it.elementId })
        if (variant.elements != expectedOrder) fail("nonCanonicalElementOrder", "$path/elements")
        val active = variant.elements.filter { it.enabled && !it.hidden }
        for (leftIndex in active.indices) {
            for (rightIndex in leftIndex + 1 until active.size) {
                val left = active[leftIndex]
                val right = active[rightIndex]
                if (left.zOrder == right.zOrder && intersects(left.rect, right.rect)) {
                    fail("ambiguousCollision")
                }
            }
        }
    }

    private fun compare(left: AspectRatio, right: AspectRatio): Int =
        (left.numerator.toLong() * right.denominator)
            .compareTo(right.numerator.toLong() * left.denominator)

    private fun intersects(left: IntRect, right: IntRect): Boolean =
        maxOf(left.x, right.x) < minOf(left.right, right.right) &&
            maxOf(left.y, right.y) < minOf(left.bottom, right.bottom)

    private fun fail(code: String, path: String = ""): Nothing =
        throw TouchLayoutV2Exception(code, path)
}

object TouchLayoutV2ViewportMapper {
    fun map(
        canvas: IntSize,
        element: TouchLayoutV2Element,
        policy: SafeAreaPolicy,
        input: ViewportInput,
    ): MappedElementRect {
        val safe = when (policy) {
            SafeAreaPolicy.VIDEO_CONTENT -> input.videoContent
            SafeAreaPolicy.VIDEO_CONTENT_AND_SYSTEM_INSETS ->
                intersection(input.videoContent, input.systemSafeArea ?: fail())
        }
        if (safe.width <= 0 || safe.height <= 0) fail()

        val useWidth = safe.width.toLong() * canvas.height <= safe.height.toLong() * canvas.width
        val numerator = if (useWidth) safe.width else safe.height
        val denominator = if (useWidth) canvas.width else canvas.height
        val mappedWidth = round(canvas.width.toLong() * numerator, denominator)
        val mappedHeight = round(canvas.height.toLong() * numerator, denominator)
        val residualX = safe.width - mappedWidth
        val residualY = safe.height - mappedHeight
        val offsetX = when (element.horizontalAnchor) {
            HorizontalAnchor.LEFT -> 0
            HorizontalAnchor.CENTER -> round(residualX.toLong(), 2)
            HorizontalAnchor.RIGHT -> residualX
        }
        val offsetY = when (element.verticalAnchor) {
            VerticalAnchor.TOP -> 0
            VerticalAnchor.CENTER -> round(residualY.toLong(), 2)
            VerticalAnchor.BOTTOM -> residualY
        }
        val left = round(element.rect.x.toLong() * numerator, denominator)
        val top = round(element.rect.y.toLong() * numerator, denominator)
        val right = round(element.rect.right.toLong() * numerator, denominator)
        val bottom = round(element.rect.bottom.toLong() * numerator, denominator)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) fail()
        return MappedElementRect(
            element.elementId,
            IntRect(
                safe.x + offsetX + left,
                safe.y + offsetY + top,
                width,
                height,
            ),
        )
    }

    private fun intersection(left: IntRect, right: IntRect): IntRect {
        val x = maxOf(left.x, right.x)
        val y = maxOf(left.y, right.y)
        val endX = minOf(left.right, right.right)
        val endY = minOf(left.bottom, right.bottom)
        if (endX <= x || endY <= y) fail()
        return IntRect(x, y, endX - x, endY - y)
    }

    private fun round(numerator: Long, denominator: Int): Int =
        ((2L * numerator + denominator) / (2L * denominator)).toInt()

    private fun fail(): Nothing = throw TouchLayoutV2Exception("incompatibleSafeArea")
}
