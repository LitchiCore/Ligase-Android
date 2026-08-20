package com.limelight.nvstream.http

import java.nio.charset.StandardCharsets
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DevicePresenceRequestEnvelopeTest {
    @Test
    fun `production heartbeat writes one exact closed envelope`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val request = NvHTTP.buildDevicePresenceHeartbeatRequest(
                server.url("/ligase/v1/device-presence/heartbeat"),
            )
            OkHttpClient().newCall(request).execute().use { }

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/ligase/v1/device-presence/heartbeat", recorded.path)
            assertEquals(listOf("application/json"), recorded.headers.values("Content-Type"))
            assertEquals(listOf("application/json"), recorded.headers.values("Accept"))
            val expected = "{\"schemaVersion\":1}".toByteArray(StandardCharsets.UTF_8)
            assertEquals(expected.size.toLong(), recorded.bodySize)
            assertEquals(expected.size.toString(), recorded.getHeader("Content-Length"))
            assertEquals(expected.toList(), recorded.body.readByteArray().toList())
            assertEquals(0L, recorded.body.size)
        }
    }

    @Test
    fun `string request body demonstrates rejected charset envelope`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(415))
            val request = Request.Builder()
                .url(server.url("/ligase/v1/device-presence/heartbeat"))
                .header("Accept", "application/json")
                .post("{\"schemaVersion\":1}".toRequestBody("application/json".toMediaType()))
                .build()
            OkHttpClient().newCall(request).execute().use { }

            val contentType = server.takeRequest().getHeader("Content-Type")
            assertNotEquals("application/json", contentType)
            assertEquals("application/json; charset=utf-8", contentType)
        }
    }
}
