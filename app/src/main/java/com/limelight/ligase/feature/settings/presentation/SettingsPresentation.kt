package com.limelight.ligase.feature.settings.presentation

import androidx.annotation.StringRes
import com.limelight.R
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigaseLanguageMode
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.stream.application.StreamBitrateUiState
import com.limelight.ligase.feature.stream.application.StreamNetworkTestUiState
import com.limelight.ligase.feature.stream.domain.CustomBitrateParseResult
import com.limelight.ligase.feature.stream.domain.StreamBitratePolicy
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId
import com.limelight.ligase.feature.stream.domain.DeviceStreamCapabilities
import com.limelight.ligase.feature.stream.domain.StreamDisplayPolicy
import com.limelight.ligase.feature.stream.domain.StreamFrameRateChoice
import com.limelight.ligase.feature.stream.domain.StreamFrameRateMode

data class SettingsUiState(
    val selectedInput: InputDeviceMode,
    val themeMode: LigaseThemeMode,
    val languageMode: LigaseLanguageMode,
    val globalResolution: LigaseResolutionDto?,
    val hdrState: LibraryHdrState,
    val canOperate: Boolean,
    val streamBitrate: StreamBitrateUiState,
    val deviceStreamCapabilities: DeviceStreamCapabilities =
        DeviceStreamCapabilities(1920, 1080, 60f, false),
    val streamFrameRateMode: StreamFrameRateMode = StreamFrameRateMode.FOLLOW_DISPLAY,
    val streamNetworkTest: StreamNetworkTestUiState =
        StreamNetworkTestUiState.productionDefault,
) {
    val inputEnabled: Boolean
        get() = canOperate

    val resolutionEnabled: Boolean
        get() = canOperate && globalResolution != null

    val hasResolutionContent: Boolean
        get() = globalResolution != null

    val frameRateChoices: List<StreamFrameRateChoice>
        get() = StreamDisplayPolicy.frameRateChoices(deviceStreamCapabilities)
}

sealed interface StreamBitrateDraftValidation {
    data class Ready(val kbps: Int) : StreamBitrateDraftValidation
    data object Empty : StreamBitrateDraftValidation
    data object NotCanonicalNumber : StreamBitrateDraftValidation
    data object OutOfRange : StreamBitrateDraftValidation
}

fun validateStreamBitrateDraft(
    state: StreamBitrateUiState,
    selectedPresetId: StreamBitratePresetId?,
    customMbps: String,
): StreamBitrateDraftValidation {
    if (selectedPresetId != null) {
        return state.presets
            .firstOrNull { it.id == selectedPresetId }
            ?.let { StreamBitrateDraftValidation.Ready(it.kbps) }
            ?: StreamBitrateDraftValidation.OutOfRange
    }
    return when (val parsed = StreamBitratePolicy.parseCustomMbps(customMbps)) {
        is CustomBitrateParseResult.Valid -> StreamBitrateDraftValidation.Ready(parsed.kbps)
        CustomBitrateParseResult.Empty -> StreamBitrateDraftValidation.Empty
        CustomBitrateParseResult.NotCanonicalNumber ->
            StreamBitrateDraftValidation.NotCanonicalNumber
        CustomBitrateParseResult.OutOfRange -> StreamBitrateDraftValidation.OutOfRange
    }
}

enum class StreamBitrateSaveNavigation {
    IDLE,
    WAITING,
    CLOSE,
    KEEP_OPEN,
}

fun streamBitrateSaveNavigation(
    pendingKbps: Int?,
    state: StreamBitrateUiState,
): StreamBitrateSaveNavigation = when {
    pendingKbps == null -> StreamBitrateSaveNavigation.IDLE
    state.saving -> StreamBitrateSaveNavigation.WAITING
    state.error != null -> StreamBitrateSaveNavigation.KEEP_OPEN
    state.currentKbps == pendingKbps -> StreamBitrateSaveNavigation.CLOSE
    else -> StreamBitrateSaveNavigation.WAITING
}

data class StreamBitratePresetPresentation(
    @StringRes val description: Int,
)

fun streamBitratePresetPresentation(
    id: StreamBitratePresetId,
): StreamBitratePresetPresentation? = when (id) {
    StreamBitratePresetId.MBPS_5 ->
        StreamBitratePresetPresentation(R.string.ligase_stream_bitrate_preset_5)
    StreamBitratePresetId.MBPS_10 ->
        StreamBitratePresetPresentation(R.string.ligase_stream_bitrate_preset_10)
    StreamBitratePresetId.MBPS_20 ->
        StreamBitratePresetPresentation(R.string.ligase_stream_bitrate_preset_20)
    StreamBitratePresetId.MBPS_40 ->
        StreamBitratePresetPresentation(R.string.ligase_stream_bitrate_preset_40)
    StreamBitratePresetId.MBPS_80 ->
        StreamBitratePresetPresentation(R.string.ligase_stream_bitrate_preset_80)
    else -> null
}
