package com.limelight.ligase.feature.host.presentation

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostManagementPresentationTest {
    @Test
    fun hostStatesMapWithoutChangingExistingPriority() {
        assertEquals(
            HostManagementStatus.CHECKING,
            hostManagementStatus(host(state = ComputerDetails.State.UNKNOWN)),
        )
        assertEquals(
            HostManagementStatus.OFFLINE,
            hostManagementStatus(host(state = ComputerDetails.State.OFFLINE)),
        )
        assertEquals(
            HostManagementStatus.PAIR_REQUIRED,
            hostManagementStatus(host(pairState = PairingManager.PairState.NOT_PAIRED)),
        )
        assertEquals(
            HostManagementStatus.ONLINE,
            hostManagementStatus(host()),
        )
    }

    @Test
    fun rowsKeepOrderSelectionAndStableComposeKey() {
        val first = host(name = "First", uuid = "HOST-A")
        val second = host(name = "Second", uuid = "HOST-B")
        val presentation = hostManagementPresentation(listOf(first, second), "host-b")

        assertFalse(presentation.empty)
        assertEquals(listOf("First", "Second"), presentation.rows.map { it.name })
        assertFalse(presentation.rows[0].selected)
        assertTrue(presentation.rows[1].selected)
        assertEquals("HOST-A", presentation.rows[0].stableKey.revealForComposeKey())
    }

    @Test
    fun emptyHostListProducesExecutableEmptyPresentation() {
        val presentation = hostManagementPresentation(emptyList(), null)

        assertTrue(presentation.empty)
        assertTrue(presentation.rows.isEmpty())
    }

    @Test
    fun presentationStringsDoNotExposeUuidAddressOrMac() {
        val uuid = "private-host-uuid"
        val presentation = hostManagementPresentation(
            listOf(
                host(name = "Visible name", uuid = uuid).apply {
                    localAddress = ComputerDetails.AddressTuple("10.0.0.8", 48989)
                    macAddress = "AA:BB:CC:DD:EE:FF"
                },
            ),
            uuid,
        )
        val rendered = presentation.toString()

        assertFalse(rendered.contains(uuid))
        assertFalse(rendered.contains("10.0.0.8"))
        assertFalse(rendered.contains("AA:BB:CC:DD:EE:FF"))
    }

    private fun host(
        name: String = "Host",
        uuid: String = "host-uuid",
        state: ComputerDetails.State = ComputerDetails.State.ONLINE,
        pairState: PairingManager.PairState = PairingManager.PairState.PAIRED,
    ): ComputerDetails = ComputerDetails().apply {
        this.name = name
        this.uuid = uuid
        this.state = state
        this.pairState = pairState
    }
}
