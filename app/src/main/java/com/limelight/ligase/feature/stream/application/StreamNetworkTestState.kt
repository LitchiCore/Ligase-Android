package com.limelight.ligase.feature.stream.application

import com.limelight.ligase.feature.stream.domain.StreamBitratePolicy

enum class StreamNetworkTestUnavailableReason {
    HOST_CONTRACT_NOT_READY,
}

data class StreamNetworkTestMetrics private constructor(
    val rttMs: Int,
    val jitterMs: Int,
    val lossPermille: Int,
    val estimatedBitrateKbps: Int,
    val recommendedBitrateKbps: Int,
) {
    companion object {
        fun validated(
            rttMs: Int,
            jitterMs: Int,
            lossPermille: Int,
            estimatedBitrateKbps: Int,
            recommendedBitrateKbps: Int,
        ): StreamNetworkTestMetrics? {
            if (rttMs !in 0..60_000 || jitterMs !in 0..60_000) return null
            if (lossPermille !in 0..1_000) return null
            if (!StreamBitratePolicy.isValidKbps(estimatedBitrateKbps)) return null
            if (!StreamBitratePolicy.isValidKbps(recommendedBitrateKbps)) return null
            if (recommendedBitrateKbps > estimatedBitrateKbps) return null
            return StreamNetworkTestMetrics(
                rttMs = rttMs,
                jitterMs = jitterMs,
                lossPermille = lossPermille,
                estimatedBitrateKbps = estimatedBitrateKbps,
                recommendedBitrateKbps = recommendedBitrateKbps,
            )
        }
    }
}

sealed interface StreamNetworkTestUiState {
    data class Unavailable(
        val reason: StreamNetworkTestUnavailableReason,
    ) : StreamNetworkTestUiState

    data object Running : StreamNetworkTestUiState

    data class Completed(
        val metrics: StreamNetworkTestMetrics,
    ) : StreamNetworkTestUiState

    data object Failed : StreamNetworkTestUiState

    companion object {
        val productionDefault: StreamNetworkTestUiState =
            Unavailable(StreamNetworkTestUnavailableReason.HOST_CONTRACT_NOT_READY)
    }
}
