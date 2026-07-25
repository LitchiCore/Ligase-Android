package com.limelight.ligase.feature.input.layout.v3.application

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutV3RuntimeGateTest {
    @Test
    fun `runtime remains typed unavailable until separately implemented`() {
        assertEquals(
            LayoutV3RuntimeDecision.Unavailable(
                LayoutV3RuntimeUnavailableReason.NOT_IMPLEMENTED,
            ),
            LayoutV3RuntimeGate.current(),
        )
    }
}
