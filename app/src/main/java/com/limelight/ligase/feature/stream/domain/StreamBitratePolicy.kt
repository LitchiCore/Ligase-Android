package com.limelight.ligase.feature.stream.domain

import kotlin.math.abs

enum class StreamBitratePresetId {
    MBPS_1,
    MBPS_2,
    MBPS_5,
    MBPS_10,
    MBPS_15,
    MBPS_20,
    MBPS_30,
    MBPS_40,
    MBPS_60,
    MBPS_80,
    MBPS_100,
    MBPS_150,
    MBPS_200,
    MBPS_300,
}

data class StreamBitratePreset(
    val id: StreamBitratePresetId,
    val kbps: Int,
    val displayMbps: Int,
)

sealed interface CustomBitrateParseResult {
    data class Valid(val kbps: Int) : CustomBitrateParseResult
    data object Empty : CustomBitrateParseResult
    data object NotCanonicalNumber : CustomBitrateParseResult
    data object OutOfRange : CustomBitrateParseResult
}

object StreamBitratePolicy {
    const val MIN_KBPS = 500
    const val MAX_KBPS = 300_000
    const val STEP_KBPS = 500

    val presets: List<StreamBitratePreset> = listOf(
        preset(StreamBitratePresetId.MBPS_5, 5),
        preset(StreamBitratePresetId.MBPS_10, 10),
        preset(StreamBitratePresetId.MBPS_20, 20),
        preset(StreamBitratePresetId.MBPS_40, 40),
        preset(StreamBitratePresetId.MBPS_80, 80),
    )

    fun isValidKbps(kbps: Int): Boolean =
        kbps in MIN_KBPS..MAX_KBPS && kbps % STEP_KBPS == 0

    fun parseCustomMbps(raw: String): CustomBitrateParseResult {
        val value = raw.trim()
        if (value.isEmpty()) return CustomBitrateParseResult.Empty
        if (!CANONICAL_MBPS.matches(value)) {
            return CustomBitrateParseResult.NotCanonicalNumber
        }
        val parts = value.split('.', limit = 2)
        val whole = parts[0].toIntOrNull()
            ?: return CustomBitrateParseResult.OutOfRange
        if (whole > MAX_KBPS / 1000) {
            return CustomBitrateParseResult.OutOfRange
        }
        val kbps = whole * 1000 + if (parts.size == 2) STEP_KBPS else 0
        return if (isValidKbps(kbps)) {
            CustomBitrateParseResult.Valid(kbps)
        } else {
            CustomBitrateParseResult.OutOfRange
        }
    }

    fun formatMbps(kbps: Int): String {
        require(isValidKbps(kbps)) { "bitrateOutOfRange" }
        val whole = kbps / 1000
        return if (kbps % 1000 == 0) "$whole" else "$whole.5"
    }

    fun presetFor(kbps: Int): StreamBitratePreset? =
        presets.firstOrNull { it.kbps == kbps }

    fun recommendedPreset(defaultKbps: Int): StreamBitratePreset =
        presets.minWith(
            compareBy<StreamBitratePreset> { abs(it.kbps.toLong() - defaultKbps.toLong()) }
                .thenBy { it.kbps },
        )

    private fun preset(id: StreamBitratePresetId, mbps: Int) =
        StreamBitratePreset(id, mbps * 1000, mbps)

    private val CANONICAL_MBPS = Regex("""(?:0|[1-9][0-9]*)(?:\.5)?""")
}
