package com.limelight.ligase.feature.layout.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

internal class DefaultTouchKitLayoutMaterializer(
    private val context: Context,
) {
    fun materialize(raw: Map<String, *>): Map<String, Any> {
        val result = raw.mapValuesTo(mutableMapOf()) { (_, value) ->
            @Suppress("UNCHECKED_CAST")
            if (value is Set<*>) (value as Set<String>).toSet() else value as Any
        }
        val width = (result[TouchKitLayoutCodec.CANVAS_WIDTH] as? Int)
            ?.takeIf { it > 0 } ?: TouchKitLayoutCodec.DEFAULT_WIDTH
        val height = (result[TouchKitLayoutCodec.CANVAS_HEIGHT] as? Int)
            ?.takeIf { it > 0 } ?: TouchKitLayoutCodec.DEFAULT_HEIGHT
        result[TouchKitLayoutCodec.CANVAS_WIDTH] = width
        result[TouchKitLayoutCodec.CANVAS_HEIGHT] = height
        result[TouchKitLayoutCodec.FORMAT_VERSION] = 2
        result.putIfAbsent(TouchKitLayoutCodec.DYNAMIC_ELEMENTS, "[]")
        result.putIfAbsent(TouchKitLayoutCodec.DELETED_BASE_ELEMENTS, "[]")

        val deleted = JSONArray(result[TouchKitLayoutCodec.DELETED_BASE_ELEMENTS] as String)
        val deletedIds = buildSet {
            for (index in 0 until deleted.length()) add(deleted.optString(index))
        }
        val data = JSONObject(
            context.assets.open("config/keyboard.json").bufferedReader().use { it.readText() },
        ).getJSONObject("data")
        val buttonSize = minOf(scale(10, height), width / 18)
        val unit = (buttonSize.toFloat() * 72f / height).toInt().coerceAtLeast(1)
        val normalizedButtonSize = scale(unit, height)
        val rightDisplacement = width - height * 16 / 9

        addPads(
            result = result,
            descriptors = data.getJSONArray("dpad"),
            x = scale(92, height) + rightDisplacement,
            y = scale(41, height),
            size = (normalizedButtonSize * 2.5f).roundToInt(),
            deletedIds = deletedIds,
        )
        addPads(
            result = result,
            descriptors = data.getJSONArray("rocker"),
            x = scale(4, height),
            y = scale(41, height),
            size = (normalizedButtonSize * 2.5f).roundToInt(),
            deletedIds = deletedIds,
        )

        val buttons = JSONArray()
        val keystrokes = data.getJSONArray("keystroke")
        val mouse = data.getJSONArray("mouse")
        for (index in 0 until keystrokes.length()) {
            buttons.put(JSONObject(keystrokes.getJSONObject(index).toString()).put("type", 0))
        }
        for (index in 0 until mouse.length()) {
            buttons.put(JSONObject(mouse.getJSONObject(index).toString()).put("type", 1))
        }
        for (index in 0 until buttons.length()) {
            val descriptor = buttons.getJSONObject(index)
            val type = descriptor.optInt("type")
            val code = descriptor.getInt("code")
            if ((type == 1 && code in 4..5) || (type == 0 && code in 144..153)) continue
            val switchButton = descriptor.optInt("switchButton") == 1
            val id = when (type) {
                0 -> if (switchButton) "key_s_$code" else "key_$code"
                else -> if (switchButton) "m_s_$code" else "m_$code"
            }
            if (id in deletedIds || result.containsKey(id)) continue
            val column = index % 14
            val row = index / 14
            result[id] = defaultConfiguration(
                left = scale(1 + column * unit, height),
                top = scale(unit + row * unit, height),
                width = normalizedButtonSize,
                height = normalizedButtonSize,
            )
        }
        return result
    }

    private fun addPads(
        result: MutableMap<String, Any>,
        descriptors: JSONArray,
        x: Int,
        y: Int,
        size: Int,
        deletedIds: Set<String>,
    ) {
        for (index in 0 until descriptors.length()) {
            val id = descriptors.getJSONObject(index).getString("elementId")
            if (id !in deletedIds && !result.containsKey(id)) {
                result[id] = defaultConfiguration(x, y, size, size)
            }
        }
    }

    private fun defaultConfiguration(left: Int, top: Int, width: Int, height: Int): String =
        JSONObject()
            .put("LEFT", left)
            .put("TOP", top)
            .put("WIDTH", width)
            .put("HEIGHT", height)
            .put("ENABLED", true)
            .put("HIDDEN", false)
            .put("BACKGROUND_OPACITY", 42)
            .toString()

    private fun scale(units: Int, height: Int): Int = (height.toFloat() / 72f * units).toInt()
}
