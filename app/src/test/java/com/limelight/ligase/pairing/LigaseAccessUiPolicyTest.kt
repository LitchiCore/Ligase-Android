package com.limelight.ligase.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LigaseAccessUiPolicyTest {
    @Test
    fun onlyExactOperateEnablesHostOperations() {
        assertTrue(LigaseAccessUiPolicy.canOperate("operate"))
        assertFalse(LigaseAccessUiPolicy.canOperate("observe"))
        assertFalse(LigaseAccessUiPolicy.canOperate(null))
        assertFalse(LigaseAccessUiPolicy.canOperate("launch-only"))
    }

    @Test
    fun pairedObserveDisablesInputButLocalAndUnpairedFlowsRemainAvailable() {
        assertFalse(
            LigaseAccessUiPolicy.canConfigureInput(
                hasSelectedHost = true,
                paired = true,
                accessMode = "observe",
            ),
        )
        assertTrue(
            LigaseAccessUiPolicy.canConfigureInput(
                hasSelectedHost = true,
                paired = true,
                accessMode = "operate",
            ),
        )
        assertTrue(
            LigaseAccessUiPolicy.canConfigureInput(
                hasSelectedHost = false,
                paired = false,
                accessMode = null,
            ),
        )
    }
}
