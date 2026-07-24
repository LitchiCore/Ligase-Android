package com.limelight.ligase.library

import com.limelight.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryHdrPresentationTest {
    @Test
    fun `maps available and disabled reasons`() {
        assertEquals(
            R.string.ligase_hdr_available,
            LibraryHdrReason.AVAILABLE.messageResource(),
        )
        assertEquals(
            R.string.ligase_hdr_disabled,
            LibraryHdrReason.USER_DISABLED.messageResource(),
        )
    }

    @Test
    fun `maps host encoding failure separately`() {
        assertEquals(
            R.string.ligase_hdr_host_unsupported,
            LibraryHdrReason.HOST_ENCODING_UNSUPPORTED.messageResource(),
        )
    }

    @Test
    fun `maps display and decoder failures to the device reason`() {
        for (reason in listOf(
            LibraryHdrReason.DISPLAY_UNSUPPORTED,
            LibraryHdrReason.DECODER_UNSUPPORTED,
        )) {
            assertEquals(
                R.string.ligase_hdr_device_unsupported,
                reason.messageResource(),
            )
        }
    }

    @Test
    fun `maps unknown capabilities to the detection fallback`() {
        for (reason in listOf(
            LibraryHdrReason.HOST_CAPABILITY_UNKNOWN,
            LibraryHdrReason.DISPLAY_CAPABILITY_UNKNOWN,
            LibraryHdrReason.DECODER_CAPABILITY_UNKNOWN,
        )) {
            assertEquals(
                R.string.ligase_hdr_unknown,
                reason.messageResource(),
            )
        }
    }
}
