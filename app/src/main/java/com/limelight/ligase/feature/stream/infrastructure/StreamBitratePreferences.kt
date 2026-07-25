package com.limelight.ligase.feature.stream.infrastructure

import android.content.SharedPreferences
import com.limelight.ligase.feature.stream.domain.CustomBitrateParseResult
import com.limelight.ligase.feature.stream.domain.StreamBitratePolicy
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId

enum class StreamBitrateSelectionSource {
    DEFAULT,
    PRESET,
    CUSTOM,
}

data class StoredStreamBitrate(
    val kbps: Int,
    val source: StreamBitrateSelectionSource,
    val presetId: StreamBitratePresetId? = null,
)

class StreamBitratePreferences(
    private val preferences: SharedPreferences,
) {
    fun readOrMigrate(defaultKbps: Int): StoredStreamBitrate {
        val current = readInteger(CURRENT_KBPS_KEY)
        if (current != null && StreamBitratePolicy.isValidKbps(current)) {
            val preset = readPreset(current)
            return if (preset == null) {
                StoredStreamBitrate(current, StreamBitrateSelectionSource.CUSTOM)
            } else {
                StoredStreamBitrate(current, StreamBitrateSelectionSource.PRESET, preset)
            }
        }

        val legacyMbps = readInteger(LEGACY_MBPS_KEY)
        val migratedKbps = legacyMbps?.let { safeLegacyKbps(it) }
        if (migratedKbps != null) {
            preferences.edit()
                .putInt(CURRENT_KBPS_KEY, migratedKbps)
                .putString(SELECTION_KEY, CUSTOM_SELECTION)
                .remove(LEGACY_MBPS_KEY)
                .apply()
            return StoredStreamBitrate(
                migratedKbps,
                StreamBitrateSelectionSource.CUSTOM,
            )
        }

        val normalizedDefault = normalizeDefault(defaultKbps)
        preferences.edit()
            .putInt(CURRENT_KBPS_KEY, normalizedDefault)
            .putString(SELECTION_KEY, DEFAULT_SELECTION)
            .apply()
        return StoredStreamBitrate(
            normalizedDefault,
            StreamBitrateSelectionSource.DEFAULT,
        )
    }

    fun saveKbps(kbps: Int): Boolean {
        if (!StreamBitratePolicy.isValidKbps(kbps)) return false
        return preferences.edit()
            .putInt(CURRENT_KBPS_KEY, kbps)
            .putString(SELECTION_KEY, CUSTOM_SELECTION)
            .commit()
    }

    fun savePreset(id: StreamBitratePresetId): Boolean {
        val preset = StreamBitratePolicy.presets.first { it.id == id }
        return preferences.edit()
            .putInt(CURRENT_KBPS_KEY, preset.kbps)
            .putString(SELECTION_KEY, PRESET_PREFIX + id.name)
            .commit()
    }

    private fun readPreset(kbps: Int): StreamBitratePresetId? {
        val raw = preferences.getString(SELECTION_KEY, null) ?: return null
        if (!raw.startsWith(PRESET_PREFIX)) return null
        val id = runCatching {
            StreamBitratePresetId.valueOf(raw.removePrefix(PRESET_PREFIX))
        }.getOrNull() ?: return null
        return id.takeIf {
            StreamBitratePolicy.presets.first { preset -> preset.id == id }.kbps == kbps
        }
    }

    private fun readInteger(key: String): Int? =
        runCatching {
            if (preferences.contains(key)) preferences.getInt(key, 0) else null
        }.getOrNull()

    private fun safeLegacyKbps(mbps: Int): Int? {
        if (mbps <= 0 || mbps > StreamBitratePolicy.MAX_KBPS / 1000) return null
        val kbps = mbps * 1000
        return kbps.takeIf(StreamBitratePolicy::isValidKbps)
    }

    private fun normalizeDefault(defaultKbps: Int): Int {
        if (StreamBitratePolicy.isValidKbps(defaultKbps)) return defaultKbps
        return StreamBitratePolicy.recommendedPreset(defaultKbps).kbps
    }

    companion object {
        const val CURRENT_KBPS_KEY = "seekbar_bitrate_kbps"
        const val LEGACY_MBPS_KEY = "seekbar_bitrate"
        private const val SELECTION_KEY = "ligase_stream_bitrate_selection"
        private const val DEFAULT_SELECTION = "default"
        private const val CUSTOM_SELECTION = "custom"
        private const val PRESET_PREFIX = "preset:"

        @JvmStatic
        fun readLaunchKbps(
            preferences: SharedPreferences,
            defaultKbps: Int,
        ): Int = StreamBitratePreferences(preferences)
            .readOrMigrate(defaultKbps)
            .kbps

        @JvmStatic
        fun saveCustomKbps(
            preferences: SharedPreferences,
            kbps: Int,
        ): Boolean = StreamBitratePreferences(preferences).saveKbps(kbps)

        @JvmStatic
        fun saveCustomMbpsText(
            preferences: SharedPreferences,
            rawMbps: String,
        ): Boolean = when (val parsed = StreamBitratePolicy.parseCustomMbps(rawMbps)) {
            is CustomBitrateParseResult.Valid ->
                StreamBitratePreferences(preferences).saveKbps(parsed.kbps)
            else -> false
        }

        @JvmStatic
        fun initializeDefaultIfMissing(
            preferences: SharedPreferences,
            defaultKbps: Int,
        ): Int = StreamBitratePreferences(preferences)
            .readOrMigrate(defaultKbps)
            .kbps
    }
}
