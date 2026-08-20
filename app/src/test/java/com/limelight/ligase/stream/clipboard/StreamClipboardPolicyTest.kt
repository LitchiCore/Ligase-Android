package com.limelight.ligase.stream.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StreamClipboardPolicyTest {
    @Test
    fun localClipboardRequiresOnePlainTextItem() {
        assertEquals(
            StreamClipboardDecisionCode.ACCEPTED,
            StreamClipboardPolicy.validateLocal(listOf("text/plain"), 1, "hello").code,
        )
        assertEquals(
            StreamClipboardDecisionCode.UNSUPPORTED_TYPE,
            StreamClipboardPolicy.validateLocal(listOf("text/html"), 1, "hello").code,
        )
        assertEquals(
            StreamClipboardDecisionCode.EMPTY,
            StreamClipboardPolicy.validateLocal(listOf("text/plain"), 2, "hello").code,
        )
    }

    @Test
    fun utf8LimitIsByteExactAndDecisionRedactsText() {
        val accepted = StreamClipboardPolicy.validateRemote("a".repeat(64 * 1024))
        val rejected = StreamClipboardPolicy.validateRemote("界".repeat(22_000))

        assertEquals(StreamClipboardDecisionCode.ACCEPTED, accepted.code)
        assertEquals(StreamClipboardDecisionCode.TOO_LARGE, rejected.code)
        assertFalse(accepted.toString().contains("aaaa"))
    }
}
