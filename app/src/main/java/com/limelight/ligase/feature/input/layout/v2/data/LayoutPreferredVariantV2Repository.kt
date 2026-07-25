package com.limelight.ligase.feature.input.layout.v2.data

import android.content.Context
import android.content.SharedPreferences
import com.limelight.ligase.layout.LayoutContractV1Validator

sealed interface StoredLayoutVariantPreference {
    data object Absent : StoredLayoutVariantPreference
    data class Valid(val revision: Long, val variantId: String) :
        StoredLayoutVariantPreference
    data class Stale(val storedRevision: Long) : StoredLayoutVariantPreference
    data object Invalid : StoredLayoutVariantPreference
}

class LayoutPreferredVariantV2Repository(
    context: Context,
) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(layoutId: String, revision: Long): StoredLayoutVariantPreference {
        if (!validIdentity(layoutId, revision)) return StoredLayoutVariantPreference.Invalid
        val raw = preferences.getString(key(layoutId), null)
            ?: return StoredLayoutVariantPreference.Absent
        val separator = raw.indexOf('|')
        if (separator <= 0 || separator != raw.lastIndexOf('|')) {
            return StoredLayoutVariantPreference.Invalid
        }
        val storedRevision = raw.substring(0, separator).toLongOrNull()
            ?: return StoredLayoutVariantPreference.Invalid
        val variantId = raw.substring(separator + 1)
        val canonicalVariant = LayoutContractV1Validator.normalizeUuid(variantId)
        if (
            !LayoutContractV1Validator.isValidRevision(storedRevision) ||
            canonicalVariant == null ||
            canonicalVariant != variantId
        ) {
            return StoredLayoutVariantPreference.Invalid
        }
        return if (storedRevision == revision) {
            StoredLayoutVariantPreference.Valid(storedRevision, variantId)
        } else {
            StoredLayoutVariantPreference.Stale(storedRevision)
        }
    }

    fun write(layoutId: String, revision: Long, variantId: String): Boolean {
        if (
            !validIdentity(layoutId, revision) ||
            LayoutContractV1Validator.normalizeUuid(variantId) != variantId
        ) {
            return false
        }
        return preferences.edit()
            .putString(key(layoutId), "$revision|$variantId")
            .commit()
    }

    fun clear(layoutId: String): Boolean {
        val canonical = LayoutContractV1Validator.normalizeUuid(layoutId)
        if (canonical == null || canonical != layoutId) return false
        return preferences.edit().remove(key(layoutId)).commit()
    }

    internal fun snapshotForTest(): Map<String, *> = preferences.all.toMap()

    private fun validIdentity(layoutId: String, revision: Long): Boolean =
        LayoutContractV1Validator.normalizeUuid(layoutId) == layoutId &&
            LayoutContractV1Validator.isValidRevision(revision)

    private fun key(layoutId: String) = "preferred:$layoutId"

    private companion object {
        const val PREFERENCES_NAME = "ligase_touch_layout_v2_preferences"
    }
}
