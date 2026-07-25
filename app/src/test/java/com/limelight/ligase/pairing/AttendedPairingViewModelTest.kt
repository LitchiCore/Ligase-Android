package com.limelight.ligase.pairing

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AttendedPairingViewModelTest {
    @Test
    fun `recreated observer keeps same countdown state`() {
        val viewModel = AttendedPairingViewModel()
        val binding = viewModel.createBinding()
        val coordinator = mock(AttendedPairingCoordinator::class.java)
        viewModel.start(coordinator, HOST_ID, binding) {}
        val waiting = AttendedPairingUiState.Waiting(
            deviceName = "Phone",
            safetyCode = "ABCD-EFGH",
            countdown = PairingCountdown.of(47),
        )

        binding.stateListener(waiting)

        assertEquals(waiting, viewModel.state)
        assertEquals(47, (viewModel.state as AttendedPairingUiState.Waiting).countdown.remainingSeconds)
    }

    @Test
    fun `late callback from old generation cannot overwrite new request`() {
        val viewModel = AttendedPairingViewModel()
        val firstBinding = viewModel.createBinding()
        val firstCoordinator = mock(AttendedPairingCoordinator::class.java)
        viewModel.start(firstCoordinator, HOST_ID, firstBinding) {}
        firstBinding.stateListener(AttendedPairingUiState.Stopped(StopReason.EXPIRED))

        val secondBinding = viewModel.createBinding()
        val secondCoordinator = mock(AttendedPairingCoordinator::class.java)
        viewModel.start(secondCoordinator, OTHER_HOST_ID, secondBinding) {}
        val current = AttendedPairingUiState.Waiting(
            deviceName = "Tablet",
            safetyCode = "WXYZ-2345",
            countdown = PairingCountdown.of(119),
        )
        secondBinding.stateListener(current)

        firstBinding.stateListener(
            AttendedPairingUiState.Waiting(
                deviceName = "Old phone",
                safetyCode = "OLD0-0000",
                countdown = PairingCountdown.of(1),
            ),
        )

        assertEquals(current, viewModel.state)
        assertEquals(OTHER_HOST_ID, viewModel.targetHostUuid)
        verify(firstCoordinator).close()
    }

    companion object {
        private const val HOST_ID = "53beb7ec-9788-cc23-461a-061f153029a5"
        private const val OTHER_HOST_ID = "cdd1aadf-6f41-4895-8615-43c35543fb3e"
    }
}
