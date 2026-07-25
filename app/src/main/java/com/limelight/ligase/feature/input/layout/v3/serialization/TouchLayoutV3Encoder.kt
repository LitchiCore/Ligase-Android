package com.limelight.ligase.feature.input.layout.v3.serialization

import com.limelight.ligase.feature.input.layout.v3.domain.*

object TouchLayoutV3Encoder {
    fun encode(document: TouchLayoutV3Document): ByteArray {
        val withoutHash = documentObject(document, "")
        val hash = TouchLayoutV3Jcs.contentHash(withoutHash)
        val complete = StrictJsonV3Value.ObjectValue(
            withoutHash.fields + ("contentHash" to str(hash)),
        )
        return TouchLayoutV3Jcs.canonicalBytes(complete)
    }

    internal fun encodeDraft(document: TouchLayoutV3Document): ByteArray = encode(document)

    private fun documentObject(
        document: TouchLayoutV3Document,
        contentHash: String,
    ) = obj(
        "format" to str("ligase-touch-layout"),
        "schemaVersion" to int(3),
        "layoutId" to str(document.layoutId),
        "revision" to int(document.revision),
        "nextKeyboardBatchOrdinal" to int(document.nextKeyboardBatchOrdinal),
        "displayName" to str(document.displayName),
        "extensions" to StrictJsonV3Value.ObjectValue(document.extensions),
        "variants" to array(document.variants.map(::variant)),
        "contentHash" to str(contentHash),
    )

    private fun variant(value: TouchLayoutV3Variant) = obj(
        "variantId" to str(value.variantId),
        "deviceClasses" to array(value.deviceClasses.map { str(it.name.lowercase()) }),
        "orientations" to array(value.orientations.map { str(it.name.lowercase()) }),
        "recommendation" to recommendation(value.recommendation),
        "canvas" to size(value.canvas),
        "elements" to array(value.elements.map(::element)),
    )

    private fun recommendation(value: LayoutRecommendation): StrictJsonV3Value {
        val fields = linkedMapOf<String, StrictJsonV3Value>(
            "preferredAspectRatio" to ratio(value.preferredAspectRatio),
            "minAspectRatio" to ratio(value.minAspectRatio),
            "maxAspectRatio" to ratio(value.maxAspectRatio),
            "minShortestSideDp" to int(value.minShortestSideDp),
            "minTouchTargetDp" to int(value.minTouchTargetDp),
        )
        value.referenceDensityDpi?.let { fields["referenceDensityDpi"] = int(it) }
        fields["referenceResolution"] = size(value.referenceResolution)
        return StrictJsonV3Value.ObjectValue(fields)
    }

    private fun element(value: TouchLayoutV3Element): StrictJsonV3Value {
        val fields = linkedMapOf<String, StrictJsonV3Value>(
            "elementId" to str(value.elementId),
            "kind" to str(kind(value.kind)),
            "rect" to rect(value.rect),
            "anchorX" to str(value.anchorX.name),
            "anchorY" to str(value.anchorY.name),
            "zOrder" to int(value.zOrder),
            "enabled" to bool(value.enabled),
            "hidden" to bool(value.hidden),
            "opacityPermille" to int(value.opacityPermille),
            "payload" to payload(value.payload),
        )
        value.sourceReference?.let { fields["sourceReference"] = str(it) }
        return StrictJsonV3Value.ObjectValue(fields)
    }

    private fun payload(value: ControlPayload): StrictJsonV3Value = when (value) {
        is KeyboardPayload -> obj(
            "payloadKind" to str("keyboard"),
            "inputCode" to inputCode(value.inputCode),
            "appearance" to appearance(value.appearance),
            "trigger" to str(trigger(value.trigger)),
            "timedHoldMs" to nullable(value.timedHoldMs),
        )
        is MousePayload -> obj(
            "payloadKind" to str("mouse"),
            "button" to str(value.button),
            "appearance" to appearance(value.appearance),
            "trigger" to str(trigger(value.trigger)),
            "timedHoldMs" to nullable(value.timedHoldMs),
        )
        is DirectionalPayload -> {
            val fields = linkedMapOf<String, StrictJsonV3Value>(
                "payloadKind" to str(kind(value.kind)),
                "up" to inputCodes(value.up),
                "down" to inputCodes(value.down),
                "left" to inputCodes(value.left),
                "right" to inputCodes(value.right),
            )
            if (value.kind == ControlKind.ANALOG) {
                value.press?.let { fields["press"] = inputCodes(it) }
            }
            fields["diagonalPolicy"] = str(value.diagonalPolicy)
            StrictJsonV3Value.ObjectValue(fields)
        }
        is ChordPayload -> {
            val fields = linkedMapOf<String, StrictJsonV3Value>(
                "payloadKind" to str(kind(value.kind)),
                "keys" to inputCodes(value.keys),
                "pressOrder" to str("listed"),
                "releaseOrder" to str("reverseListed"),
                "trigger" to str(trigger(value.trigger)),
                "timedHoldMs" to nullable(value.timedHoldMs),
                "appearance" to appearance(value.appearance),
            )
            value.sticky?.let { fields["sticky"] = bool(it) }
            StrictJsonV3Value.ObjectValue(fields)
        }
        is RadialPayload -> obj(
            "payloadKind" to str("radial"),
            "label" to str(value.label),
            "startAngleMilliDegrees" to int(-90_000),
            "direction" to str("clockwise"),
            "boundaryPolicy" to str("clockwiseInclusive"),
            "actions" to array(value.actions.map {
                obj("keys" to inputCodes(it.keys), "label" to str(it.label))
            }),
        )
        is ScrollPayload -> obj(
            "payloadKind" to str("scroll"),
            "direction" to str(value.direction),
            "stepUnit" to str("wheelDetent"),
            "step" to int(value.step),
            "continuous" to bool(value.continuous),
            "cadenceMs" to nullable(value.cadenceMs),
            "appearance" to appearance(value.appearance),
            "trigger" to str(trigger(value.trigger)),
            "timedHoldMs" to nullable(value.timedHoldMs),
        )
        SoftKeyboardPayload -> obj(
            "payloadKind" to str("softKeyboard"),
            "action" to str("openSystemIme"),
            "executionScope" to str("androidLocal"),
        )
        is GyroPayload -> obj(
            "payloadKind" to str("gyro"),
            "target" to str(value.target),
            "frame" to str(value.frame),
            "axes" to str(value.axes),
            "scaleUnit" to str("milliUnitsPerDegreePerSecond"),
            "scale" to int(value.scale),
            "deadzoneMilliDegreesPerSecond" to int(value.deadzoneMilliDegreesPerSecond),
            "invertX" to bool(value.invertX),
            "invertY" to bool(value.invertY),
        )
    }

    private fun appearance(value: Appearance) = obj(
        "label" to str(value.label),
        "description" to str(value.description),
        "shape" to str(value.shape),
        "showPhysicalKeyNames" to bool(value.showPhysicalKeyNames),
    )
    private fun inputCodes(values: List<InputCode>) = array(values.map(::inputCode))
    private fun inputCode(value: InputCode) = obj(
        "namespace" to str(
            when (value.namespace) {
                InputCodeNamespace.ANDROID_KEY_CODE -> "androidKeyCode"
                InputCodeNamespace.USB_HID_KEYBOARD_USAGE -> "usbHidKeyboardUsage"
            },
        ),
        "code" to int(value.code),
    )
    private fun ratio(value: AspectRatio) = obj(
        "numerator" to int(value.numerator),
        "denominator" to int(value.denominator),
    )
    private fun size(value: IntSize) = obj("width" to int(value.width), "height" to int(value.height))
    private fun rect(value: AnchoredRect) = obj(
        "horizontalOffset" to int(value.horizontalOffset),
        "verticalOffset" to int(value.verticalOffset),
        "width" to int(value.width), "height" to int(value.height),
    )
    private fun kind(value: ControlKind) = when (value) {
        ControlKind.SOFT_KEYBOARD -> "softKeyboard"
        ControlKind.CUSTOM_KEYS -> "customKeys"
        else -> value.name.lowercase()
    }
    private fun trigger(value: Trigger) = when (value) {
        Trigger.TIMED_HOLD -> "timedHold"
        else -> value.name.lowercase()
    }
    private fun nullable(value: String?) =
        value?.let(::str) ?: StrictJsonV3Value.NullValue
    private fun nullable(value: Int?) =
        value?.let(::int) ?: StrictJsonV3Value.NullValue
    private fun obj(vararg values: Pair<String, StrictJsonV3Value>) =
        StrictJsonV3Value.ObjectValue(linkedMapOf(*values))
    private fun array(values: List<StrictJsonV3Value>) = StrictJsonV3Value.ArrayValue(values)
    private fun str(value: String) = StrictJsonV3Value.StringValue(value)
    private fun int(value: Int) = StrictJsonV3Value.IntegerValue(value.toLong())
    private fun int(value: Long) = StrictJsonV3Value.IntegerValue(value)
    private fun bool(value: Boolean) = StrictJsonV3Value.BooleanValue(value)
}
