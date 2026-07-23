package com.limelight.ligase.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRefreshCoordinatorTest {
    @Test
    fun `same host refresh is single flight`() {
        val coordinator = LibraryRefreshCoordinator()
        coordinator.selectHost(HOST_A)

        val first = coordinator.begin(HOST_A.lowercase(), preservesContent = true)

        assertTrue(first != null)
        assertTrue(coordinator.inFlight)
        assertNull(coordinator.begin(HOST_A, preservesContent = true))
    }

    @Test
    fun `completed ticket becomes stale and cannot mutate a later request`() {
        val coordinator = LibraryRefreshCoordinator()
        coordinator.selectHost(HOST_A)
        val first = coordinator.begin(HOST_A, preservesContent = true)!!
        assertTrue(coordinator.accept(first))

        val second = coordinator.begin(HOST_A, preservesContent = true)!!

        assertFalse(coordinator.accept(first))
        assertTrue(coordinator.inFlight)
        assertTrue(coordinator.accept(second))
    }

    @Test
    fun `host switch invalidates old result`() {
        val coordinator = LibraryRefreshCoordinator()
        coordinator.selectHost(HOST_A)
        val oldHostTicket = coordinator.begin(HOST_A, preservesContent = true)!!

        coordinator.selectHost(HOST_B)

        assertFalse(coordinator.accept(oldHostTicket))
        assertFalse(coordinator.inFlight)
        assertTrue(coordinator.begin(HOST_B, preservesContent = false) != null)
    }

    @Test
    fun `failure policy can retain visible content`() {
        val coordinator = LibraryRefreshCoordinator()
        coordinator.selectHost(HOST_A)

        val refresh = coordinator.begin(HOST_A, preservesContent = true)!!
        assertTrue(refresh.preservesContent)
        assertTrue(coordinator.accept(refresh))

        val initialLoad = coordinator.begin(HOST_A, preservesContent = false)!!
        assertFalse(initialLoad.preservesContent)
    }

    private companion object {
        const val HOST_A = "53BEB7EC-9788-CC23-461A-061F153029A5"
        const val HOST_B = "78B73D4B-B71E-4E2F-271C-7CE13E7B9241"
    }
}
