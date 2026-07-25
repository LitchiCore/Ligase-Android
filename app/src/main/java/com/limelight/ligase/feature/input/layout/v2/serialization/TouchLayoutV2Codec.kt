package com.limelight.ligase.feature.input.layout.v2.serialization

import com.limelight.ligase.feature.input.layout.v2.domain.*

object TouchLayoutV2Codec {
    private val uuidPattern =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val hashPattern = Regex("^sha256:[0-9a-f]{64}$")

    fun decode(raw: ByteArray): TouchLayoutV2Document {
        val root = try {
            StrictJson.parse(raw).obj("")
        } catch (error: TouchLayoutV2Exception) {
            if (error.code in setOf("unsafeInteger", "fractionNotAllowed")) {
                fail("schemaRejected", error.path)
            }
            throw error
        }
        root.exactKeys(
            setOf(
                "format", "schemaVersion", "layoutId", "revision", "displayName",
                "extensions", "variants", "contentHash",
            ),
            "",
        )
        root.string("format", "").requireExact("ligase-touch-layout", "invalidFormat")
        root.long("schemaVersion", "").requireExact(2L, "invalidFormat")
        val layoutId = root.string("layoutId", "").uuid("/layoutId")
        val revision = root.long("revision", "").range(1, SAFE_MAX, "/revision")
        val displayName = root.string("displayName", "").clean(1, 80, "/displayName")
        val extensions = root.obj("extensions", "").fields.also {
            validateExtensions(it, "/extensions", 0)
        }
        val variants = root.array("variants", "").bounded(1, 32, "/variants")
            .mapIndexed(::parseVariant)
        val contentHash = root.string("contentHash", "")
        if (!hashPattern.matches(contentHash)) fail("schemaRejected", "/contentHash")
        val document = TouchLayoutV2Document(
            layoutId,
            revision,
            displayName,
            extensions,
            variants,
            contentHash,
        )
        TouchLayoutV2Validator.validate(document)
        if (TouchLayoutV2Jcs.contentHash(root) != contentHash) fail("contentHashMismatch")
        return document
    }

    private fun parseVariant(index: Int, value: StrictJsonValue): TouchLayoutV2Variant {
        val path = "/variants/$index"
        val obj = value.obj(path)
        obj.exactKeys(
            setOf("variantId", "deviceClasses", "orientations", "recommendation", "canvas", "elements"),
            path,
        )
        val deviceClasses = obj.array("deviceClasses", path).bounded(1, 2, "$path/deviceClasses")
            .mapIndexed { item, entry ->
                when (entry.string("$path/deviceClasses/$item")) {
                    "phone" -> DeviceClass.PHONE
                    "tablet" -> DeviceClass.TABLET
                    else -> fail("schemaRejected", "$path/deviceClasses/$item")
                }
            }
        val orientations = obj.array("orientations", path).bounded(1, 2, "$path/orientations")
            .mapIndexed { item, entry ->
                when (entry.string("$path/orientations/$item")) {
                    "portrait" -> LayoutOrientation.PORTRAIT
                    "landscape" -> LayoutOrientation.LANDSCAPE
                    else -> fail("schemaRejected", "$path/orientations/$item")
                }
            }
        val canvas = parseSize(obj.obj("canvas", path), "$path/canvas")
        val elements = obj.array("elements", path).bounded(1, 512, "$path/elements")
            .mapIndexed { item, entry -> parseElement(item, entry, canvas, path) }
        return TouchLayoutV2Variant(
            variantId = obj.string("variantId", path).uuid("$path/variantId"),
            deviceClasses = deviceClasses,
            orientations = orientations,
            recommendation = parseRecommendation(obj.obj("recommendation", path), "$path/recommendation"),
            canvas = canvas,
            elements = elements,
        )
    }

    private fun parseRecommendation(
        obj: StrictJsonValue.ObjectValue,
        path: String,
    ): LayoutRecommendation {
        obj.exactKeys(
            setOf(
                "preferredAspectRatio", "minAspectRatio", "maxAspectRatio",
                "minShortestSideDp", "minTouchTargetDp", "safeAreaPolicy",
                "referenceDensityDpi", "referenceResolution",
            ),
            path,
            optional = setOf("referenceDensityDpi"),
        )
        return LayoutRecommendation(
            preferredAspectRatio = parseRatio(obj.obj("preferredAspectRatio", path), "$path/preferredAspectRatio"),
            minAspectRatio = parseRatio(obj.obj("minAspectRatio", path), "$path/minAspectRatio"),
            maxAspectRatio = parseRatio(obj.obj("maxAspectRatio", path), "$path/maxAspectRatio"),
            minShortestSideDp = obj.int("minShortestSideDp", path).range(1, 4096, "$path/minShortestSideDp"),
            minTouchTargetDp = obj.int("minTouchTargetDp", path).range(1, 256, "$path/minTouchTargetDp"),
            safeAreaPolicy = when (obj.string("safeAreaPolicy", path)) {
                "videoContent" -> SafeAreaPolicy.VIDEO_CONTENT
                "videoContentAndSystemInsets" -> SafeAreaPolicy.VIDEO_CONTENT_AND_SYSTEM_INSETS
                else -> fail("schemaRejected", "$path/safeAreaPolicy")
            },
            referenceDensityDpi = obj.optionalInt("referenceDensityDpi", path)
                ?.range(1, 4096, "$path/referenceDensityDpi"),
            referenceResolution = parseSize(obj.obj("referenceResolution", path), "$path/referenceResolution"),
        )
    }

    private fun parseRatio(obj: StrictJsonValue.ObjectValue, path: String): AspectRatio {
        obj.exactKeys(setOf("numerator", "denominator"), path)
        return AspectRatio(
            obj.int("numerator", path).range(1, 32768, "$path/numerator"),
            obj.int("denominator", path).range(1, 32768, "$path/denominator"),
        )
    }

    private fun parseSize(obj: StrictJsonValue.ObjectValue, path: String): IntSize {
        obj.exactKeys(setOf("width", "height"), path)
        return IntSize(
            obj.int("width", path).range(1, 32768, "$path/width"),
            obj.int("height", path).range(1, 32768, "$path/height"),
        )
    }

    private fun parseElement(
        index: Int,
        value: StrictJsonValue,
        canvas: IntSize,
        variantPath: String,
    ): TouchLayoutV2Element {
        val path = "$variantPath/elements/$index"
        val obj = value.obj(path)
        obj.exactKeys(
            setOf(
                "elementId", "kind", "rect", "horizontalAnchor", "verticalAnchor",
                "zOrder", "enabled", "hidden", "opacityPermille", "payload", "sourceReference",
            ),
            path,
            optional = setOf("sourceReference"),
        )
        val kind = parseKind(obj.string("kind", path), "$path/kind")
        val rectObj = obj.obj("rect", path)
        rectObj.exactKeys(setOf("x", "y", "width", "height"), "$path/rect")
        val rect = IntRect(
            rectObj.int("x", "$path/rect").range(0, 32767, "$path/rect/x"),
            rectObj.int("y", "$path/rect").range(0, 32767, "$path/rect/y"),
            rectObj.int("width", "$path/rect").range(1, 32768, "$path/rect/width"),
            rectObj.int("height", "$path/rect").range(1, 32768, "$path/rect/height"),
        )
        if (rect.right > canvas.width || rect.bottom > canvas.height) fail("rectOutsideCanvas", "$path/rect")
        val payload = parsePayload(kind, obj.obj("payload", path), "$path/payload")
        if (payload.kind != kind) fail("payloadKindMismatch", "$path/payload")
        return TouchLayoutV2Element(
            elementId = obj.string("elementId", path).uuid("$path/elementId"),
            kind = kind,
            rect = rect,
            horizontalAnchor = when (obj.string("horizontalAnchor", path)) {
                "left" -> HorizontalAnchor.LEFT
                "center" -> HorizontalAnchor.CENTER
                "right" -> HorizontalAnchor.RIGHT
                else -> fail("schemaRejected", "$path/horizontalAnchor")
            },
            verticalAnchor = when (obj.string("verticalAnchor", path)) {
                "top" -> VerticalAnchor.TOP
                "center" -> VerticalAnchor.CENTER
                "bottom" -> VerticalAnchor.BOTTOM
                else -> fail("schemaRejected", "$path/verticalAnchor")
            },
            zOrder = obj.int("zOrder", path).range(-32768, 32767, "$path/zOrder"),
            enabled = obj.boolean("enabled", path),
            hidden = obj.boolean("hidden", path),
            opacityPermille = obj.int("opacityPermille", path).range(0, 1000, "$path/opacityPermille"),
            payload = payload,
            sourceReference = obj.optionalString("sourceReference", path)?.clean(1, 160, "$path/sourceReference"),
        )
    }

    private fun parsePayload(
        kind: ControlKind,
        obj: StrictJsonValue.ObjectValue,
        path: String,
    ): ControlPayload = when (kind) {
        ControlKind.KEYBOARD -> {
            obj.exactKeys(setOf("payloadKind", "inputCode", "appearance", "trigger", "timedHoldMs"), path)
            obj.payloadKind("keyboard", path)
            val (trigger, timed) = parseTrigger(obj, path)
            KeyboardPayload(
                parseInputCode(obj.obj("inputCode", path), "$path/inputCode"),
                parseAppearance(obj.obj("appearance", path), "$path/appearance"),
                trigger,
                timed,
            )
        }
        ControlKind.MOUSE -> {
            obj.exactKeys(setOf("payloadKind", "button", "appearance", "trigger", "timedHoldMs"), path)
            obj.payloadKind("mouse", path)
            val button = obj.string("button", path)
            if (button !in setOf("primary", "secondary", "middle", "back", "forward")) {
                fail("invalidMouseButton", "$path/button")
            }
            val (trigger, timed) = parseTrigger(obj, path)
            MousePayload(button, parseAppearance(obj.obj("appearance", path), "$path/appearance"), trigger, timed)
        }
        ControlKind.ANALOG, ControlKind.DPAD -> parseDirectional(kind, obj, path)
        ControlKind.CUSTOM_KEYS, ControlKind.COMBO -> parseChord(kind, obj, path)
        ControlKind.RADIAL -> parseRadial(obj, path)
        ControlKind.SCROLL -> parseScroll(obj, path)
        ControlKind.SOFT_KEYBOARD -> {
            obj.exactKeys(setOf("payloadKind", "action", "executionScope"), path)
            obj.payloadKind("softKeyboard", path)
            obj.string("action", path).requireExact("openSystemIme", "invalidSoftKeyboard")
            obj.string("executionScope", path).requireExact("androidLocal", "invalidSoftKeyboard")
            SoftKeyboardPayload
        }
        ControlKind.GYRO -> parseGyro(obj, path)
    }

    private fun parseDirectional(
        kind: ControlKind,
        obj: StrictJsonValue.ObjectValue,
        path: String,
    ): DirectionalPayload {
        val optional = if (kind == ControlKind.ANALOG) setOf("press") else emptySet()
        obj.exactKeys(
            setOf("payloadKind", "up", "down", "left", "right", "press", "diagonalPolicy"),
            path,
            optional = optional,
            forbidden = if (kind == ControlKind.DPAD) setOf("press") else emptySet(),
        )
        obj.payloadKind(if (kind == ControlKind.ANALOG) "analog" else "dpad", path)
        val diagonal = obj.string("diagonalPolicy", path)
        if (
            (kind == ControlKind.ANALOG && diagonal != "vector") ||
            (kind == ControlKind.DPAD && diagonal !in setOf("allowTwoDirections", "dominantAxis"))
        ) fail("invalidDiagonalPolicy", "$path/diagonalPolicy")
        return DirectionalPayload(
            kind,
            parseInputCodeList(obj.array("up", path), "$path/up"),
            parseInputCodeList(obj.array("down", path), "$path/down"),
            parseInputCodeList(obj.array("left", path), "$path/left"),
            parseInputCodeList(obj.array("right", path), "$path/right"),
            obj.optionalArray("press", path)?.let { parseInputCodeList(it, "$path/press") },
            diagonal,
        )
    }

    private fun parseChord(
        kind: ControlKind,
        obj: StrictJsonValue.ObjectValue,
        path: String,
    ): ChordPayload {
        val isCustom = kind == ControlKind.CUSTOM_KEYS
        obj.exactKeys(
            setOf(
                "payloadKind", "keys", "pressOrder", "releaseOrder", "trigger",
                "timedHoldMs", "appearance", "sticky",
            ),
            path,
            optional = if (isCustom) emptySet() else setOf("sticky"),
            forbidden = if (isCustom) emptySet() else setOf("sticky"),
        )
        obj.payloadKind(if (isCustom) "customKeys" else "combo", path)
        obj.string("pressOrder", path).requireExact("listed", "invalidChordOrder")
        obj.string("releaseOrder", path).requireExact("reverseListed", "invalidChordOrder")
        val (trigger, timed) = parseTrigger(obj, path)
        val sticky = if (isCustom) obj.boolean("sticky", path) else null
        if (sticky == true && trigger != Trigger.TOGGLE) fail("stickyTriggerConflict", "$path/sticky")
        return ChordPayload(
            kind,
            parseInputCodeList(obj.array("keys", path), "$path/keys"),
            trigger,
            timed,
            parseAppearance(obj.obj("appearance", path), "$path/appearance"),
            sticky,
        )
    }

    private fun parseRadial(obj: StrictJsonValue.ObjectValue, path: String): RadialPayload {
        obj.exactKeys(
            setOf("payloadKind", "label", "startAngleMilliDegrees", "direction", "boundaryPolicy", "actions"),
            path,
        )
        obj.payloadKind("radial", path)
        obj.long("startAngleMilliDegrees", path).requireExact(-90000L, "invalidRadialGeometry")
        obj.string("direction", path).requireExact("clockwise", "invalidRadialGeometry")
        obj.string("boundaryPolicy", path).requireExact("clockwiseInclusive", "invalidRadialBoundary")
        val actions = obj.array("actions", path).bounded(2, 16, "$path/actions").mapIndexed { index, item ->
            val actionPath = "$path/actions/$index"
            val action = item.obj(actionPath)
            action.exactKeys(setOf("keys", "label"), actionPath)
            RadialAction(
                parseInputCodeList(action.array("keys", actionPath), "$actionPath/keys"),
                action.string("label", actionPath).clean(1, 32, "$actionPath/label"),
            )
        }
        return RadialPayload(obj.string("label", path).clean(1, 32, "$path/label"), actions)
    }

    private fun parseScroll(obj: StrictJsonValue.ObjectValue, path: String): ScrollPayload {
        obj.exactKeys(
            setOf(
                "payloadKind", "direction", "stepUnit", "step", "continuous", "cadenceMs",
                "appearance", "trigger", "timedHoldMs",
            ),
            path,
        )
        obj.payloadKind("scroll", path)
        val direction = obj.string("direction", path)
        if (direction !in setOf("verticalPositive", "verticalNegative", "horizontalPositive", "horizontalNegative")) {
            fail("invalidScrollDirection", "$path/direction")
        }
        obj.string("stepUnit", path).requireExact("wheelDetent", "invalidScrollUnit")
        val continuous = obj.boolean("continuous", path)
        val cadence = obj.optionalInt("cadenceMs", path)
        if (continuous) {
            if (cadence == null || cadence !in 16..1000) fail("invalidScrollCadence", "$path/cadenceMs")
        } else if (cadence != null) {
            fail("unexpectedScrollCadence", "$path/cadenceMs")
        }
        val (trigger, timed) = parseTrigger(obj, path)
        return ScrollPayload(
            direction,
            obj.int("step", path).range(1, 120, "$path/step"),
            continuous,
            cadence,
            parseAppearance(obj.obj("appearance", path), "$path/appearance"),
            trigger,
            timed,
        )
    }

    private fun parseGyro(obj: StrictJsonValue.ObjectValue, path: String): GyroPayload {
        obj.exactKeys(
            setOf(
                "payloadKind", "target", "frame", "axes", "scaleUnit", "scale",
                "deadzoneMilliDegreesPerSecond", "invertX", "invertY",
            ),
            path,
        )
        obj.payloadKind("gyro", path)
        val target = obj.string("target", path)
        if (target !in setOf("mouse", "rightStick")) fail("invalidGyroTarget", "$path/target")
        val frame = obj.string("frame", path)
        if (frame !in setOf("device", "screen")) fail("invalidGyroFrame", "$path/frame")
        val axes = obj.string("axes", path)
        if (axes !in setOf("yawPitch", "yawPitchRoll")) fail("invalidGyroAxes", "$path/axes")
        obj.string("scaleUnit", path).requireExact(
            "milliUnitsPerDegreePerSecond",
            "invalidGyroScale",
        )
        return GyroPayload(
            target,
            frame,
            axes,
            obj.int("scale", path).range(1, 100000, "$path/scale"),
            obj.int("deadzoneMilliDegreesPerSecond", path)
                .range(0, 100000, "$path/deadzoneMilliDegreesPerSecond"),
            obj.boolean("invertX", path),
            obj.boolean("invertY", path),
        )
    }

    private fun parseTrigger(obj: StrictJsonValue.ObjectValue, path: String): Pair<Trigger, Int?> {
        val trigger = when (obj.string("trigger", path)) {
            "hold" -> Trigger.HOLD
            "toggle" -> Trigger.TOGGLE
            "tap" -> Trigger.TAP
            "timedHold" -> Trigger.TIMED_HOLD
            else -> fail("invalidTrigger", "$path/trigger")
        }
        val timed = obj.optionalInt("timedHoldMs", path)
        if (trigger == Trigger.TIMED_HOLD) {
            if (timed == null || timed !in 1..60000) fail("invalidTimedHold", "$path/timedHoldMs")
        } else if (timed != null) {
            fail("unexpectedTimedHold", "$path/timedHoldMs")
        }
        return trigger to timed
    }

    private fun parseAppearance(obj: StrictJsonValue.ObjectValue, path: String): Appearance {
        obj.exactKeys(setOf("label", "description", "shape", "showPhysicalKeyNames"), path)
        val shape = obj.string("shape", path)
        if (shape !in setOf("circle", "rectangle", "roundedrectangle")) fail("schemaRejected", "$path/shape")
        return Appearance(
            obj.string("label", path).clean(0, 32, "$path/label"),
            obj.string("description", path).clean(0, 80, "$path/description"),
            shape,
            obj.boolean("showPhysicalKeyNames", path),
        )
    }

    private fun parseInputCodeList(
        array: StrictJsonValue.ArrayValue,
        path: String,
    ): List<InputCode> = array.bounded(1, 16, path)
        .mapIndexed { index, value -> parseInputCode(value.obj("$path/$index"), "$path/$index") }

    private fun parseInputCode(obj: StrictJsonValue.ObjectValue, path: String): InputCode {
        obj.exactKeys(setOf("namespace", "code"), path)
        val namespace = when (obj.string("namespace", path)) {
            "androidKeyCode" -> InputCodeNamespace.ANDROID_KEY_CODE
            "usbHidKeyboardUsage" -> InputCodeNamespace.USB_HID_KEYBOARD_USAGE
            else -> fail("unsupportedInputNamespace", "$path/namespace")
        }
        return InputCode(namespace, obj.int("code", path).range(0, 65535, "$path/code"))
    }

    private fun parseKind(value: String, path: String): ControlKind = when (value) {
        "keyboard" -> ControlKind.KEYBOARD
        "mouse" -> ControlKind.MOUSE
        "analog" -> ControlKind.ANALOG
        "dpad" -> ControlKind.DPAD
        "customKeys" -> ControlKind.CUSTOM_KEYS
        "radial" -> ControlKind.RADIAL
        "scroll" -> ControlKind.SCROLL
        "combo" -> ControlKind.COMBO
        "softKeyboard" -> ControlKind.SOFT_KEYBOARD
        "gyro" -> ControlKind.GYRO
        else -> fail("unknownKind", path)
    }

    private fun validateExtensions(
        fields: Map<String, StrictJsonValue>,
        path: String,
        depth: Int,
    ) {
        if (fields.size > 32) fail("extensionPropertiesExceeded", path)
        fields.forEach { (key, value) ->
            key.clean(1, 64, "$path/$key")
            validateExtensionValue(value, "$path/$key", depth)
        }
    }

    private fun validateExtensionValue(value: StrictJsonValue, path: String, depth: Int) {
        when (value) {
            StrictJsonValue.NullValue, is StrictJsonValue.BooleanValue -> Unit
            is StrictJsonValue.IntegerValue ->
                if (value.value !in -SAFE_MAX..SAFE_MAX) fail("unsafeInteger", path)
            is StrictJsonValue.StringValue -> value.value.clean(0, 256, path)
            is StrictJsonValue.ArrayValue -> {
                if (depth >= 6) fail("extensionDepthExceeded", path)
                value.bounded(0, 32, path).forEachIndexed { index, child ->
                    validateExtensionValue(child, "$path/$index", depth + 1)
                }
            }
            is StrictJsonValue.ObjectValue -> {
                if (depth >= 6) fail("extensionDepthExceeded", path)
                validateExtensions(value.fields, path, depth + 1)
            }
        }
    }

    private fun StrictJsonValue.ObjectValue.payloadKind(expected: String, path: String) {
        string("payloadKind", path).requireExact(expected, "payloadKindMismatch")
    }

    private fun StrictJsonValue.ObjectValue.exactKeys(
        allowed: Set<String>,
        path: String,
        optional: Set<String> = emptySet(),
        forbidden: Set<String> = emptySet(),
    ) {
        if (fields.keys.any { it !in allowed } || fields.keys.any { it in forbidden }) {
            fail("schemaRejected", path)
        }
        val required = allowed - optional - forbidden
        if (!fields.keys.containsAll(required)) fail("schemaRejected", path)
    }

    private fun StrictJsonValue.ObjectValue.value(name: String, path: String): StrictJsonValue =
        fields[name] ?: fail("schemaRejected", "$path/$name")
    private fun StrictJsonValue.ObjectValue.string(name: String, path: String) = value(name, path).string("$path/$name")
    private fun StrictJsonValue.ObjectValue.optionalString(name: String, path: String): String? =
        fields[name]?.string("$path/$name")
    private fun StrictJsonValue.ObjectValue.long(name: String, path: String) = value(name, path).long("$path/$name")
    private fun StrictJsonValue.ObjectValue.int(name: String, path: String) = value(name, path).int("$path/$name")
    private fun StrictJsonValue.ObjectValue.optionalInt(name: String, path: String): Int? =
        when (val value = fields[name]) {
            null, StrictJsonValue.NullValue -> null
            else -> value.int("$path/$name")
        }
    private fun StrictJsonValue.ObjectValue.boolean(name: String, path: String) =
        (value(name, path) as? StrictJsonValue.BooleanValue)?.value ?: fail("schemaRejected", "$path/$name")
    private fun StrictJsonValue.ObjectValue.obj(name: String, path: String) = value(name, path).obj("$path/$name")
    private fun StrictJsonValue.ObjectValue.array(name: String, path: String) = value(name, path).array("$path/$name")
    private fun StrictJsonValue.ObjectValue.optionalArray(name: String, path: String) =
        fields[name]?.array("$path/$name")

    private fun StrictJsonValue.obj(path: String) =
        this as? StrictJsonValue.ObjectValue ?: fail("schemaRejected", path)
    private fun StrictJsonValue.array(path: String) =
        this as? StrictJsonValue.ArrayValue ?: fail("schemaRejected", path)
    private fun StrictJsonValue.string(path: String) =
        (this as? StrictJsonValue.StringValue)?.value ?: fail("schemaRejected", path)
    private fun StrictJsonValue.long(path: String) =
        (this as? StrictJsonValue.IntegerValue)?.value ?: fail("schemaRejected", path)
    private fun StrictJsonValue.int(path: String): Int {
        val value = long(path)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) fail("schemaRejected", path)
        return value.toInt()
    }

    private fun StrictJsonValue.ArrayValue.bounded(min: Int, max: Int, path: String): List<StrictJsonValue> {
        if (values.size !in min..max) fail("schemaRejected", path)
        return values
    }

    private fun String.clean(min: Int, max: Int, path: String): String {
        StrictJson.validateScalarString(this)
        if (any { it.code in 0x00..0x1f || it.code in 0x7f..0x9f }) fail("invalidString", path)
        val count = codePointCount(0, length)
        if (count !in min..max) fail("schemaRejected", path)
        return this
    }
    private fun String.uuid(path: String): String {
        if (!uuidPattern.matches(this)) fail("schemaRejected", path)
        return this
    }
    private fun Int.range(min: Int, max: Int, path: String): Int {
        if (this !in min..max) fail("schemaRejected", path)
        return this
    }
    private fun Long.range(min: Long, max: Long, path: String): Long {
        if (this !in min..max) fail("schemaRejected", path)
        return this
    }
    private fun <T> T.requireExact(expected: T, code: String): T {
        if (this != expected) fail(code)
        return this
    }

    private const val SAFE_MAX = 9_007_199_254_740_991L
    private fun fail(code: String, path: String = ""): Nothing = StrictJson.fail(code, path)
}
