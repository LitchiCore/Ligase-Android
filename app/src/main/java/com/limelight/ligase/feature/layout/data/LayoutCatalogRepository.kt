package com.limelight.ligase.feature.layout.data

import android.content.Context
import android.content.SharedPreferences
import com.limelight.R
import com.limelight.TouchKitLayoutNames
import com.limelight.ligase.feature.layout.domain.LayoutCatalogItem
import com.limelight.ligase.feature.layout.domain.LayoutCatalogSource
import com.limelight.ligase.feature.layout.domain.LayoutEditorError
import java.util.UUID

sealed interface LayoutRepositoryResult<out T> {
    data class Success<T>(val value: T) : LayoutRepositoryResult<T>
    data class Failure(val error: LayoutEditorError) : LayoutRepositoryResult<Nothing>
}

class LayoutCatalogRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val codec = TouchKitLayoutCodec()
    private val materializer = DefaultTouchKitLayoutMaterializer(appContext)

    fun catalog(): LayoutRepositoryResult<List<LayoutCatalogItem>> {
        val ids = TouchKitLayoutNames.getValues(appContext)
        val names = TouchKitLayoutNames.getNames(appContext)
        val builtIns = appContext.resources.getStringArray(R.array.keyboard_axi_values).toSet()
        val items = mutableListOf<LayoutCatalogItem>()
        for ((index, layoutId) in ids.withIndex()) {
            when (val loaded = loadDocument(layoutId)) {
                is LayoutRepositoryResult.Failure -> return loaded
                is LayoutRepositoryResult.Success -> {
                    val document = loaded.value
                    items += LayoutCatalogItem(
                        layoutId = layoutId,
                        displayName = names.getOrElse(index) { layoutId },
                        source = if (layoutId in builtIns) {
                            LayoutCatalogSource.BUILT_IN
                        } else {
                            LayoutCatalogSource.LOCAL_COPY
                        },
                        editable = layoutId !in builtIns,
                        controlCount = document.elements.size,
                        previewAspectRatio = document.canvasWidth.toFloat() / document.canvasHeight,
                        revision = (document.preferences[TouchKitLayoutCodec.LIGASE_REVISION] as? Long)
                            ?: (document.preferences[TouchKitLayoutCodec.LIGASE_REVISION] as? Int)?.toLong(),
                        variantId = document.preferences[
                            TouchKitLayoutCodec.LIGASE_VARIANT_ID
                        ] as? String,
                        legacySourceReference = if (layoutId in builtIns) layoutId else {
                            document.preferences[TouchKitLayoutCodec.LIGASE_LEGACY_SOURCE] as? String
                        },
                    )
                }
            }
        }
        return LayoutRepositoryResult.Success(items)
    }

    internal fun loadDocument(layoutId: String): LayoutRepositoryResult<TouchKitLayoutDocument> {
        if (!TouchKitLayoutNames.contains(appContext, layoutId)) {
            return LayoutRepositoryResult.Failure(LayoutEditorError.LAYOUT_NOT_FOUND)
        }
        val preferences = appContext.getSharedPreferences(layoutId, Context.MODE_PRIVATE)
        val raw = try {
            materializer.materialize(preferences.all)
        } catch (_: Exception) {
            return LayoutRepositoryResult.Failure(LayoutEditorError.MALFORMED_LAYOUT)
        }
        return when (val decoded = codec.decode(raw)) {
            is TouchKitCodecResult.Success -> LayoutRepositoryResult.Success(decoded.value)
            is TouchKitCodecResult.Failure -> LayoutRepositoryResult.Failure(decoded.error)
        }
    }

    internal fun newStableLayoutId(): String =
        UUID.randomUUID().toString().lowercase()

    internal fun displayName(layoutId: String): String? {
        val ids = TouchKitLayoutNames.getValues(appContext)
        val names = TouchKitLayoutNames.getNames(appContext)
        return ids.indexOf(layoutId).takeIf { it >= 0 }?.let { names.getOrElse(it) { layoutId } }
    }

    internal fun isEditable(layoutId: String): Boolean =
        layoutId !in appContext.resources.getStringArray(R.array.keyboard_axi_values).toSet()

    internal fun saveNew(
        layoutId: String,
        displayName: String,
        preferences: Map<String, Any>,
    ): LayoutRepositoryResult<Unit> {
        if (TouchKitLayoutNames.contains(appContext, layoutId)) {
            return LayoutRepositoryResult.Failure(LayoutEditorError.INVALID_LAYOUT_ID)
        }
        val target = appContext.getSharedPreferences(layoutId, Context.MODE_PRIVATE)
        if (!replacePreferences(target, preferences)) {
            return LayoutRepositoryResult.Failure(LayoutEditorError.SAVE_FAILED)
        }
        if (!TouchKitLayoutNames.addWithStableId(appContext, layoutId, displayName)) {
            target.edit().clear().commit()
            return LayoutRepositoryResult.Failure(LayoutEditorError.SAVE_FAILED)
        }
        return LayoutRepositoryResult.Success(Unit)
    }

    internal fun saveExisting(
        layoutId: String,
        preferences: Map<String, Any>,
    ): LayoutRepositoryResult<Unit> {
        if (!isEditable(layoutId) || !TouchKitLayoutNames.contains(appContext, layoutId)) {
            return LayoutRepositoryResult.Failure(LayoutEditorError.LAYOUT_NOT_FOUND)
        }
        val target = appContext.getSharedPreferences(layoutId, Context.MODE_PRIVATE)
        return if (replacePreferences(target, preferences)) {
            LayoutRepositoryResult.Success(Unit)
        } else {
            LayoutRepositoryResult.Failure(LayoutEditorError.SAVE_FAILED)
        }
    }

    private fun replacePreferences(
        target: SharedPreferences,
        values: Map<String, Any>,
    ): Boolean {
        val editor = target.edit().clear()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> {
                    if (value.any { it !is String }) return false
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, (value as Set<String>).toSet())
                }
                else -> return false
            }
        }
        return editor.commit()
    }
}
