package com.limelight.ligase.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSessionExitCoordinatorKotlinCallerTest {
    @Test
    fun `Kotlin caller observes the same one-shot contract`() {
        var disconnects = 0
        var endings = 0
        val coordinator = StreamSessionExitCoordinator(
            object : StreamSessionExitCoordinator.Callbacks {
                override fun disconnect() {
                    disconnects++
                }

                override fun endSession() {
                    endings++
                }
            },
        )

        assertFalse(coordinator.isExitRequested())
        assertTrue(coordinator.disconnect())
        assertFalse(coordinator.endSession())
        assertTrue(coordinator.isExitRequested())
        assertEquals(1, disconnects)
        assertEquals(0, endings)
    }
}
