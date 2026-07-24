package com.limelight.ligase.feature.layout.data

import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorElement
import com.limelight.ligase.feature.layout.domain.LayoutEditorError
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

internal data class TouchKitLayoutDocument(
    val preferences: MutableMap<String, Any>,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val elements: List<LayoutEditorElement>,
)

internal sealed interface TouchKitCodecResult<out T> {
    data class Success<T>(val value: T) : TouchKitCodecResult<T>
    data class Failure(val error: LayoutEditorError) : TouchKitCodecResult<Nothing>
}

internal class TouchKitLayoutCodec {
    fun decode(raw: Map<String, *>): TouchKitCodecResult<TouchKitLayoutDocument> {
        val copied = mutableMapOf<String, Any>()
        for ((key, value) in raw) {
            val supported = when (value) {
                is String, is Int, is Long, is Float, is Boolean -> value
                is Set<*> -> {
                    if (value.any { it !is String }) {
                        return TouchKitCodecResult.Failure(LayoutEditorError.UNSUPPORTED_VALUE_TYPE)
                    }
                    @Suppress("UNCHECKED_CAST")
                    (value as Set<String>).toSet()
                }
                else -> return TouchKitCodecResult.Failure(LayoutEditorError.UNSUPPORTED_VALUE_TYPE)
            }
            copied[key] = supported
        }

        val width = (copied[CANVAS_WIDTH] as? Int)?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val height = (copied[CANVAS_HEIGHT] as? Int)?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val dynamicKinds = dynamicKinds(copied[DYNAMIC_ELEMENTS] as? String)
            ?: return TouchKitCodecResult.Failure(LayoutEditorError.MALFORMED_LAYOUT)
        val deleted = stringArray(copied[DELETED_BASE_ELEMENTS] as? String)
            ?: return TouchKitCodecResult.Failure(LayoutEditorError.MALFORMED_LAYOUT)
        val elements = mutableListOf<LayoutEditorElement>()

        for ((key, value) in copied) {
            if (key.startsWith("__touchkit_") || value !is String || key in deleted) continue
            val configuration = try {
                JSONObject(value)
            } catch (_: JSONException) {
                continue
            }
            val bounds = configuration.bounds(width, height) ?: continue
            elements += LayoutEditorElement(
                elementId = key,
                kind = dynamicKinds[key] ?: inferBaseKind(key),
                x = bounds[0],
                y = bounds[1],
                width = bounds[2],
                height = bounds[3],
                deletable = true,
            )
        }
        if (elements.map { it.elementId }.toSet().size != elements.size) {
            return TouchKitCodecResult.Failure(LayoutEditorError.DUPLICATE_ELEMENT_ID)
        }
        return TouchKitCodecResult.Success(
            TouchKitLayoutDocument(copied, width, height, elements.sortedBy { it.elementId }),
        )
    }

    fun withGeometry(
        document: TouchKitLayoutDocument,
        elements: List<LayoutEditorElement>,
        deletedElementIds: Set<String>,
        addedDescriptors: Map<String, JSONObject>,
    ): TouchKitCodecResult<Map<String, Any>> {
        if (elements.map { it.elementId }.toSet().size != elements.size) {
            return TouchKitCodecResult.Failure(LayoutEditorError.DUPLICATE_ELEMENT_ID)
        }
        val output = document.preferences.toMutableMap()
        output[FORMAT_VERSION] = 2
        output[CANVAS_WIDTH] = document.canvasWidth
        output[CANVAS_HEIGHT] = document.canvasHeight

        for (element in elements) {
            if (!element.hasValidBounds()) {
                return TouchKitCodecResult.Failure(LayoutEditorError.INVALID_BOUNDS)
            }
            val existing = output[element.elementId] as? String
            val configuration = try {
                if (existing == null) JSONObject() else JSONObject(existing)
            } catch (_: JSONException) {
                return TouchKitCodecResult.Failure(LayoutEditorError.MALFORMED_LAYOUT)
            }
            configuration
                .put("LEFT", (element.x * document.canvasWidth).roundToInt())
                .put("TOP", (element.y * document.canvasHeight).roundToInt())
                .put("WIDTH", (element.width * document.canvasWidth).roundToInt().coerceAtLeast(MIN_SIZE))
                .put("HEIGHT", (element.height * document.canvasHeight).roundToInt().coerceAtLeast(MIN_SIZE))
                .put("ENABLED", true)
                .put("HIDDEN", false)
            if (!configuration.has("BACKGROUND_OPACITY")) configuration.put("BACKGROUND_OPACITY", 42)
            output[element.elementId] = configuration.toString()
        }

        val dynamic = JSONArray(output[DYNAMIC_ELEMENTS] as? String ?: "[]")
        for ((elementId, descriptor) in addedDescriptors) {
            val copy = JSONObject(descriptor.toString()).put("elementId", elementId)
            dynamic.put(copy)
        }
        val retainedDynamic = JSONArray()
        for (index in 0 until dynamic.length()) {
            val descriptor = dynamic.optJSONObject(index) ?: continue
            if (descriptor.optString("elementId") !in deletedElementIds) retainedDynamic.put(descriptor)
        }
        output[DYNAMIC_ELEMENTS] = retainedDynamic.toString()

        val deletedBase = stringArray(output[DELETED_BASE_ELEMENTS] as? String)?.toMutableSet()
            ?: return TouchKitCodecResult.Failure(LayoutEditorError.MALFORMED_LAYOUT)
        for (elementId in deletedElementIds) {
            output.remove(elementId)
            if (!isDynamicElementId(elementId)) deletedBase += elementId
        }
        output[DELETED_BASE_ELEMENTS] = JSONArray(deletedBase.sorted()).toString()
        return TouchKitCodecResult.Success(output)
    }

    private fun dynamicKinds(encoded: String?): Map<String, LayoutControlKind>? {
        val array = try {
            JSONArray(encoded ?: "[]")
        } catch (_: JSONException) {
            return null
        }
        val result = mutableMapOf<String, LayoutControlKind>()
        for (index in 0 until array.length()) {
            val descriptor = array.optJSONObject(index) ?: return null
            val id = descriptor.optString("elementId")
            if (id.isBlank() || result.containsKey(id)) return null
            result[id] = kindForType(descriptor.optInt("type", 0))
        }
        return result
    }

    private fun stringArray(encoded: String?): Set<String>? {
        val array = try {
            JSONArray(encoded ?: "[]")
        } catch (_: JSONException) {
            return null
        }
        return buildSet {
            for (index in 0 until array.length()) {
                val value = array.optString(index, "")
                if (value.isBlank()) return null
                add(value)
            }
        }
    }

    private fun JSONObject.bounds(canvasWidth: Int, canvasHeight: Int): FloatArray? {
        if (!has("LEFT") || !has("TOP") || !has("WIDTH") || !has("HEIGHT")) return null
        val left = optInt("LEFT", -1)
        val top = optInt("TOP", -1)
        val width = optInt("WIDTH", -1)
        val height = optInt("HEIGHT", -1)
        if (left < 0 || top < 0 || width <= 0 || height <= 0) return null
        return floatArrayOf(
            left.toFloat() / canvasWidth,
            top.toFloat() / canvasHeight,
            width.toFloat() / canvasWidth,
            height.toFloat() / canvasHeight,
        )
    }

    private fun inferBaseKind(elementId: String): LayoutControlKind = when {
        elementId.startsWith("key_") -> LayoutControlKind.KEYBOARD_KEY
        elementId.startsWith("m_") -> LayoutControlKind.MOUSE_BUTTON
        elementId.startsWith("rocker_") -> LayoutControlKind.ANALOG_STICK
        elementId.startsWith("dpad_") -> LayoutControlKind.DPAD
        elementId == "touchkit_soft_keyboard" -> LayoutControlKind.SOFT_KEYBOARD
        else -> LayoutControlKind.UNKNOWN
    }

    companion object {
        const val FORMAT_VERSION = "__touchkit_layout_format_version"
        const val CANVAS_WIDTH = "__touchkit_canvas_width"
        const val CANVAS_HEIGHT = "__touchkit_canvas_height"
        const val DYNAMIC_ELEMENTS = "__touchkit_dynamic_elements"
        const val DELETED_BASE_ELEMENTS = "__touchkit_deleted_base_elements"
        const val LIGASE_REVISION = "__ligase_layout_revision"
        const val LIGASE_VARIANT_ID = "__ligase_layout_variant_id"
        const val LIGASE_LEGACY_SOURCE = "__ligase_legacy_source_reference"
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
        private const val MIN_SIZE = 20

        fun isDynamicElementId(elementId: String): Boolean = "__instance_" in elementId

        fun kindForType(type: Int): LayoutControlKind = when (type) {
            0 -> LayoutControlKind.KEYBOARD_KEY
            1 -> LayoutControlKind.MOUSE_BUTTON
            2 -> LayoutControlKind.ANALOG_STICK
            3 -> LayoutControlKind.DPAD
            8 -> LayoutControlKind.SOFT_KEYBOARD
            else -> LayoutControlKind.UNKNOWN
        }
    }
}

private fun LayoutEditorElement.hasValidBounds(): Boolean =
    x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() &&
        x >= 0f && y >= 0f && width > 0f && height > 0f &&
        x + width <= 1f && y + height <= 1f
