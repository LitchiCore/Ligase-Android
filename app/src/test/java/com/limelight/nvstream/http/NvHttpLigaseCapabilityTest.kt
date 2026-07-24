package com.limelight.nvstream.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NvHttpLigaseCapabilityTest {
    @Test
    fun safeLogTargetNeverContainsRequestQueryOrSecrets() {
        val base = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("2001:db8::1")
            .port(48984)
            .build()

        val target = NvHTTP.getSafeLogTarget(base, "launch")

        assertEquals("https://[2001:db8::1]:48984/launch", target)
        assertFalse(target.contains("rikey"))
        assertFalse(target.contains("?"))
    }

    @Test
    fun `serverinfo reads Ligase capability fields`() {
        val xml = """
            <root status_code="200">
              <LigaseSyncVersion>1</LigaseSyncVersion>
              <LigaseSyncPath>/ligase/v1/sync</LigaseSyncPath>
              <LigaseHdrEncodingSupported>1</LigaseHdrEncodingSupported>
            </root>
        """.trimIndent()

        assertEquals(1, NvHTTP.getLigaseSyncVersion(xml))
        assertEquals(
            "/ligase/v1/sync",
            NvHTTP.getXmlString(xml, "LigaseSyncPath", false),
        )
        assertTrue(NvHTTP.getXmlBoolean(xml, "LigaseHdrEncodingSupported", false))
    }

    @Test
    fun `missing or invalid capability fields fail closed`() {
        val missing = """<root status_code="200"></root>"""
        val invalid = """
            <root status_code="200">
              <LigaseSyncVersion>future</LigaseSyncVersion>
              <LigaseHdrEncodingSupported>unexpected</LigaseHdrEncodingSupported>
            </root>
        """.trimIndent()

        assertEquals(0, NvHTTP.getLigaseSyncVersion(missing))
        assertEquals(0, NvHTTP.getLigaseSyncVersion(invalid))
        assertFalse(NvHTTP.getXmlBoolean(missing, "LigaseHdrEncodingSupported", false))
        assertFalse(NvHTTP.getXmlBoolean(invalid, "LigaseHdrEncodingSupported", false))
    }

    @Test
    fun `attended pairing and paired access projection are strict`() {
        val paired = """
            <root status_code="200">
              <LigaseAttendedPairingVersion>1</LigaseAttendedPairingVersion>
              <LigaseClientAccessMode>operate</LigaseClientAccessMode>
            </root>
        """.trimIndent()
        val custom = """
            <root status_code="200">
              <LigaseClientAccessMode>launch-only</LigaseClientAccessMode>
            </root>
        """.trimIndent()
        val public = """<root status_code="200"></root>"""

        assertEquals(1, NvHTTP.getLigaseAttendedPairingVersion(paired))
        assertEquals("operate", NvHTTP.getLigaseClientAccessMode(paired))
        assertEquals("observe", NvHTTP.getLigaseClientAccessMode(custom))
        assertEquals(null, NvHTTP.getLigaseClientAccessMode(public))
    }
}
