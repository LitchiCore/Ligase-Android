package com.limelight.ligase.feature.stream.domain

import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import kotlin.math.roundToInt

enum class StreamResolutionPresetId {
    DEVICE_MAX,
    BEST_16_9,
    HD_720,
    FULL_HD_1080,
    QHD_1440,
    UHD_2160,
}

enum class StreamCapabilityReason {
    AVAILABLE,
    EXCEEDS_DEVICE_DISPLAY,
    DEVICE_CAPABILITY_UNKNOWN,
}

data class DeviceStreamCapabilities(
    val maxWidth: Int,
    val maxHeight: Int,
    val maxRefreshRateHz: Float,
    val known: Boolean,
) {
    init {
        require(maxWidth > 0 && maxHeight > 0)
        require(maxRefreshRateHz > 0f && maxRefreshRateHz.isFinite())
    }
}

data class StreamResolutionPreset(
    val id: StreamResolutionPresetId,
    val resolution: LigaseResolutionDto?,
    val reason: StreamCapabilityReason,
)

enum class StreamFrameRateMode(val storedValue: String, val fixedFps: Int?) {
    FOLLOW_DISPLAY("follow_display", null),
    FPS_30("30", 30),
    FPS_60("60", 60),
    FPS_90("90", 90),
    FPS_120("120", 120);

    companion object {
        fun fromStoredValue(value: String?): StreamFrameRateMode =
            entries.firstOrNull { it.storedValue == value } ?: FOLLOW_DISPLAY
    }
}

data class StreamFrameRateChoice(
    val mode: StreamFrameRateMode,
    val reason: StreamCapabilityReason,
    val recommended: Boolean,
)

object StreamDisplayPolicy {
    private val fixedResolutions = linkedMapOf(
        StreamResolutionPresetId.HD_720 to LigaseResolutionDto(1280, 720),
        StreamResolutionPresetId.FULL_HD_1080 to LigaseResolutionDto(1920, 1080),
        StreamResolutionPresetId.QHD_1440 to LigaseResolutionDto(2560, 1440),
        StreamResolutionPresetId.UHD_2160 to LigaseResolutionDto(3840, 2160),
    )

    fun resolutionPresets(capabilities: DeviceStreamCapabilities): List<StreamResolutionPreset> {
        val maximum = LigaseResolutionDto(capabilities.maxWidth, capabilities.maxHeight)
        val best16By9 = fixedResolutions.values.lastOrNull {
            it.width <= capabilities.maxWidth && it.height <= capabilities.maxHeight
        }
        val capabilityReason = if (capabilities.known) {
            StreamCapabilityReason.AVAILABLE
        } else {
            StreamCapabilityReason.DEVICE_CAPABILITY_UNKNOWN
        }
        return buildList {
            add(StreamResolutionPreset(StreamResolutionPresetId.DEVICE_MAX, maximum, capabilityReason))
            add(
                StreamResolutionPreset(
                    StreamResolutionPresetId.BEST_16_9,
                    best16By9,
                    if (best16By9 == null && capabilities.known) {
                        StreamCapabilityReason.EXCEEDS_DEVICE_DISPLAY
                    } else {
                        capabilityReason
                    },
                ),
            )
            fixedResolutions.forEach { (id, resolution) ->
                add(
                    StreamResolutionPreset(
                        id = id,
                        resolution = resolution,
                        reason = when {
                            !capabilities.known -> StreamCapabilityReason.DEVICE_CAPABILITY_UNKNOWN
                            resolution.width <= capabilities.maxWidth &&
                                resolution.height <= capabilities.maxHeight ->
                                StreamCapabilityReason.AVAILABLE
                            else -> StreamCapabilityReason.EXCEEDS_DEVICE_DISPLAY
                        },
                    ),
                )
            }
        }
    }

    fun frameRateChoices(capabilities: DeviceStreamCapabilities): List<StreamFrameRateChoice> {
        val recommendedFixed = StreamFrameRateMode.entries
            .mapNotNull { it.fixedFps }
            .filter { it <= capabilities.maxRefreshRateHz + 0.5f }
            .maxOrNull()
        return StreamFrameRateMode.entries.map { mode ->
            val available = mode.fixedFps == null || mode.fixedFps <= capabilities.maxRefreshRateHz + 0.5f
            StreamFrameRateChoice(
                mode = mode,
                reason = when {
                    !capabilities.known -> StreamCapabilityReason.DEVICE_CAPABILITY_UNKNOWN
                    available -> StreamCapabilityReason.AVAILABLE
                    else -> StreamCapabilityReason.EXCEEDS_DEVICE_DISPLAY
                },
                recommended = mode.fixedFps == recommendedFixed,
            )
        }
    }

    fun launchFps(mode: StreamFrameRateMode, capabilities: DeviceStreamCapabilities): Float =
        mode.fixedFps?.toFloat() ?: (capabilities.maxRefreshRateHz * 1000f).roundToInt() / 1000f
}
