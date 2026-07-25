package com.limelight.ligase.feature.pairing.presentation

import com.limelight.R
import com.limelight.ligase.pairing.AttendedPairingUiState
import com.limelight.ligase.pairing.PairingCountdown
import com.limelight.ligase.pairing.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendedPairingPresentationTest {
    @Test
    fun hiddenStatesExposeNoActionsOrSensitiveDisplayValues() {
        listOf(AttendedPairingUiState.Idle, AttendedPairingUiState.Completed).forEach { state ->
            val presentation = attendedPairingPresentation(state)
            assertEquals(AttendedPairingPresentationKind.HIDDEN, presentation.kind)
            assertFalse(presentation.cancelEnabled)
            assertFalse(presentation.dismissEnabled)
            assertNull(presentation.safetyCode)
            assertNull(presentation.remainingSeconds)
        }
    }

    @Test
    fun activeStatesKeepCancelAvailable() {
        val states = listOf(
            AttendedPairingUiState.Creating,
            AttendedPairingUiState.Waiting(
                "Phone",
                "ABCD-EFGH",
                PairingCountdown.of(120),
            ),
            AttendedPairingUiState.Finishing("ABCD-EFGH", PairingCountdown.of(119)),
        )

        states.forEach { state ->
            val presentation = attendedPairingPresentation(state)
            assertTrue(presentation.cancelEnabled)
            assertFalse(presentation.dismissEnabled)
        }
    }

    @Test
    fun countdownIsProjectedAtTypedBoundariesWithoutLocalTime() {
        listOf(0, 1, 120).forEach { seconds ->
            val waiting = attendedPairingPresentation(
                AttendedPairingUiState.Waiting(
                    "Phone",
                    "ABCD-EFGH",
                    PairingCountdown.of(seconds),
                ),
            )
            val finishing = attendedPairingPresentation(
                AttendedPairingUiState.Finishing(
                    "ABCD-EFGH",
                    PairingCountdown.of(seconds),
                ),
            )

            assertEquals(seconds, waiting.remainingSeconds)
            assertEquals(seconds, finishing.remainingSeconds)
            assertFalse(waiting.toString().contains("remainingSeconds"))
            assertFalse(finishing.toString().contains("remainingSeconds"))
        }
        assertNull(
            attendedPairingPresentation(AttendedPairingUiState.Creating).remainingSeconds,
        )
        assertNull(
            attendedPairingPresentation(
                AttendedPairingUiState.Stopped(StopReason.EXPIRED),
            ).remainingSeconds,
        )
    }

    @Test
    fun stoppedReasonsMapToExistingNaturalLanguageResources() {
        val expected = mapOf(
            StopReason.REJECTED to R.string.ligase_pair_rejected,
            StopReason.CANCELLED to R.string.ligase_pair_cancelled,
            StopReason.EXPIRED to R.string.ligase_pair_expired,
            StopReason.FAILED to R.string.ligase_pair_failed,
            StopReason.NETWORK to R.string.ligase_pair_network,
            StopReason.PROTOCOL to R.string.ligase_pair_protocol,
            StopReason.UNKNOWN to R.string.ligase_pair_unknown,
        )

        expected.forEach { (reason, message) ->
            val presentation = attendedPairingPresentation(
                AttendedPairingUiState.Stopped(reason),
            )
            assertEquals(AttendedPairingPresentationKind.STOPPED, presentation.kind)
            assertEquals(message, presentation.message)
            assertFalse(presentation.cancelEnabled)
            assertTrue(presentation.dismissEnabled)
        }
    }

    @Test
    fun safetyCodeIsNeverIncludedInPresentationString() {
        val code = "ABCD-EFGH"
        val presentation = attendedPairingPresentation(
            AttendedPairingUiState.Waiting("Phone", code, PairingCountdown.of(120)),
        )

        assertEquals(code, presentation.safetyCode?.revealForDisplay())
        assertFalse(presentation.toString().contains(code))
        assertFalse(presentation.safetyCode.toString().contains(code))
    }
}
