package com.limelight.ligase.feature.stream.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamNetworkTestStateTest {
    @Test
    fun `production remains typed unavailable without Host quality contract`() {
        assertEquals(
            StreamNetworkTestUiState.Unavailable(
                StreamNetworkTestUnavailableReason.HOST_CONTRACT_NOT_READY,
            ),
            StreamNetworkTestUiState.productionDefault,
        )
    }

    @Test
    fun `verified result accepts bounded metrics and non-inflated suggestion`() {
        val metrics = StreamNetworkTestMetrics.validated(
            rttMs = 32,
            jitterMs = 4,
            lossPermille = 15,
            estimatedBitrateKbps = 40_000,
            recommendedBitrateKbps = 20_000,
        )

        assertTrue(metrics != null)
        assertEquals(15, metrics?.lossPermille)
    }

    @Test
    fun `invalid or inflated metrics fail closed`() {
        assertNull(StreamNetworkTestMetrics.validated(-1, 0, 0, 20_000, 10_000))
        assertNull(StreamNetworkTestMetrics.validated(1, 1, 1_001, 20_000, 10_000))
        assertNull(StreamNetworkTestMetrics.validated(1, 1, 0, 20_000, 40_000))
        assertNull(StreamNetworkTestMetrics.validated(1, 1, 0, 499, 500))
    }
}
