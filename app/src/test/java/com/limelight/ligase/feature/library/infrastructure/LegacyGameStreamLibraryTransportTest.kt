package com.limelight.ligase.feature.library.infrastructure

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParserException
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyGameStreamLibraryTransportTest {
    private val transport = LegacyGameStreamLibraryTransport()

    @Test
    fun `legacy applist parser preserves launch ABI identity`() {
        val apps = transport.parseAppList(
            "<root status_code=\"200\"><App><AppTitle>Desktop</AppTitle>" +
                "<UUID>78a25216-f239-45bd-b4aa-f41c814066e9</UUID><ID>7</ID>" +
                "<IsHdrSupported>1</IsHdrSupported></App></root>",
        )

        assertEquals(1, apps.size)
        assertEquals("Desktop", apps.single().appName)
        assertEquals("78a25216-f239-45bd-b4aa-f41c814066e9", apps.single().appUUID)
        assertEquals(7, apps.single().appId)
    }

    @Test(expected = XmlPullParserException::class)
    fun `malformed applist fails closed`() {
        transport.parseAppList(
            "<root status_code=\"200\"><App><AppTitle>Desktop</AppTitle>",
        )
    }
}
