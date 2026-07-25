package com.limelight.ligase.feature.stream.application

import com.limelight.ligase.feature.stream.domain.CustomBitrateParseResult
import com.limelight.ligase.feature.stream.domain.StreamBitratePolicy
import com.limelight.ligase.feature.stream.domain.StreamBitratePreset
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId
import com.limelight.ligase.feature.stream.infrastructure.StoredStreamBitrate
import com.limelight.ligase.feature.stream.infrastructure.StreamBitratePreferences
import com.limelight.ligase.feature.stream.infrastructure.StreamBitrateSelectionSource

enum class StreamBitrateSaveError {
    EMPTY,
    NOT_CANONICAL_NUMBER,
    OUT_OF_RANGE,
    WRITE_FAILED,
}

sealed interface StreamBitrateSaveResult {
    data class Success(val state: StreamBitrateUiState) : StreamBitrateSaveResult
    data class Failed(val error: StreamBitrateSaveError) : StreamBitrateSaveResult
}

data class StreamBitrateUiState(
    val currentKbps: Int,
    val currentDisplayMbps: String,
    val selectedPreset: StreamBitratePreset?,
    val recommendedPreset: StreamBitratePreset,
    val presets: List<StreamBitratePreset>,
    val customRangeKbps: IntRange,
    val saving: Boolean = false,
    val error: StreamBitrateSaveError? = null,
) {
    override fun toString(): String =
        "StreamBitrateUiState(currentKbps=$currentKbps," +
            "selectedPreset=${selectedPreset?.id}," +
            "recommendedPreset=${recommendedPreset.id}," +
            "saving=$saving,error=$error)"
}

class StreamBitrateState(
    private val preferences: StreamBitratePreferences,
    defaultKbps: Int,
    private val onStateChanged: (StreamBitrateUiState) -> Unit = {},
) {
    private var recommendedKbps = defaultKbps
    var state: StreamBitrateUiState = toUiState(
        preferences.readOrMigrate(defaultKbps),
        defaultKbps,
    )
        private set

    init {
        onStateChanged(state)
    }

    fun refresh(defaultKbps: Int) {
        recommendedKbps = defaultKbps
        publish(toUiState(preferences.readOrMigrate(defaultKbps), defaultKbps))
    }

    fun updateRecommendation(defaultKbps: Int) {
        recommendedKbps = defaultKbps
        publish(state.copy(recommendedPreset = StreamBitratePolicy.recommendedPreset(defaultKbps)))
    }

    fun setStreamBitrateKbps(kbps: Int): StreamBitrateSaveResult {
        if (!StreamBitratePolicy.isValidKbps(kbps)) {
            return fail(StreamBitrateSaveError.OUT_OF_RANGE)
        }
        if (!preferences.saveKbps(kbps)) {
            return fail(StreamBitrateSaveError.WRITE_FAILED)
        }
        val next = toUiState(
            StoredStreamBitrate(kbps, StreamBitrateSelectionSource.CUSTOM),
            recommendedKbps,
        )
        publish(next)
        return StreamBitrateSaveResult.Success(next)
    }

    fun submitCustomMbps(raw: String): StreamBitrateSaveResult =
        when (val parsed = StreamBitratePolicy.parseCustomMbps(raw)) {
            is CustomBitrateParseResult.Valid -> setStreamBitrateKbps(parsed.kbps)
            CustomBitrateParseResult.Empty -> fail(StreamBitrateSaveError.EMPTY)
            CustomBitrateParseResult.NotCanonicalNumber ->
                fail(StreamBitrateSaveError.NOT_CANONICAL_NUMBER)
            CustomBitrateParseResult.OutOfRange ->
                fail(StreamBitrateSaveError.OUT_OF_RANGE)
        }

    fun setStreamBitratePreset(id: StreamBitratePresetId): StreamBitrateSaveResult {
        if (!preferences.savePreset(id)) {
            return fail(StreamBitrateSaveError.WRITE_FAILED)
        }
        val preset = StreamBitratePolicy.presets.first { it.id == id }
        val next = toUiState(
            StoredStreamBitrate(
                preset.kbps,
                StreamBitrateSelectionSource.PRESET,
                id,
            ),
            recommendedKbps,
        )
        publish(next)
        return StreamBitrateSaveResult.Success(next)
    }

    private fun fail(error: StreamBitrateSaveError): StreamBitrateSaveResult.Failed {
        publish(state.copy(saving = false, error = error))
        return StreamBitrateSaveResult.Failed(error)
    }

    private fun publish(next: StreamBitrateUiState) {
        state = next
        onStateChanged(next)
    }

    private fun toUiState(
        stored: StoredStreamBitrate,
        defaultKbps: Int,
    ): StreamBitrateUiState {
        val selectedPreset = stored.presetId?.let { id ->
            StreamBitratePolicy.presets.firstOrNull { it.id == id }
        }
        return StreamBitrateUiState(
            currentKbps = stored.kbps,
            currentDisplayMbps = StreamBitratePolicy.formatMbps(stored.kbps),
            selectedPreset = selectedPreset,
            recommendedPreset = StreamBitratePolicy.recommendedPreset(defaultKbps),
            presets = StreamBitratePolicy.presets,
            customRangeKbps = StreamBitratePolicy.MIN_KBPS..StreamBitratePolicy.MAX_KBPS,
        )
    }
}
