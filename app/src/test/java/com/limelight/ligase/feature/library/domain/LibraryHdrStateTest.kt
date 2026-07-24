package com.limelight.ligase.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHdrStateTest {
    @Test
    fun `all required capabilities and enabled preference are available`() {
        val state = resolve(host = true, display = true, decoder = true, user = true)

        assertTrue(state.available)
        assertEquals(LibraryHdrReason.AVAILABLE, state.reason)
    }

    @Test
    fun `disabled user preference is the actionable reason`() {
        val state = resolve(host = true, display = true, decoder = true, user = false)

        assertFalse(state.available)
        assertEquals(LibraryHdrReason.USER_DISABLED, state.reason)
    }

    @Test
    fun `host display and decoder failures have stable reasons`() {
        assertEquals(
            LibraryHdrReason.HOST_ENCODING_UNSUPPORTED,
            resolve(host = false, display = true, decoder = true, user = true).reason,
        )
        assertEquals(
            LibraryHdrReason.DISPLAY_UNSUPPORTED,
            resolve(host = true, display = false, decoder = true, user = true).reason,
        )
        assertEquals(
            LibraryHdrReason.DECODER_UNSUPPORTED,
            resolve(host = true, display = true, decoder = false, user = true).reason,
        )
    }

    @Test
    fun `unknown capability never becomes available`() {
        assertEquals(
            LibraryHdrReason.HOST_CAPABILITY_UNKNOWN,
            resolve(host = null, display = true, decoder = true, user = true).reason,
        )
        assertEquals(
            LibraryHdrReason.DISPLAY_CAPABILITY_UNKNOWN,
            resolve(host = true, display = null, decoder = true, user = true).reason,
        )
        assertEquals(
            LibraryHdrReason.DECODER_CAPABILITY_UNKNOWN,
            resolve(host = true, display = true, decoder = null, user = true).reason,
        )
    }

    private fun resolve(
        host: Boolean?,
        display: Boolean?,
        decoder: Boolean?,
        user: Boolean?,
    ): LibraryHdrState = LibraryHdrStateResolver.resolve(
        hostEncodingSupported = host,
        displaySupported = display,
        decoderSupported = decoder,
        userEnabled = user,
    )
}
