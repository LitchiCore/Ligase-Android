package com.limelight.ligase.feature.library.infrastructure

import android.media.MediaCodecInfo
import android.os.Build
import android.view.Display
import com.limelight.ligase.feature.library.domain.LibraryHdrReason
import com.limelight.ligase.feature.library.domain.LibraryHdrStateResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidHdrCapabilityProbeTest {
    @Test
    fun `pre Android N reports unsupported without enumerating platform`() {
        var codecReads = 0
        val result = probe(
            sdkInt = Build.VERSION_CODES.M,
            displayTypes = { error("display must not be read") },
            codecs = {
                codecReads++
                emptyList()
            },
            userEnabled = { true },
        ).probe()

        assertFalse(result.displaySupported!!)
        assertFalse(result.decoderSupported!!)
        assertTrue(result.userEnabled!!)
        assertEquals(0, codecReads)
    }

    @Test
    fun `missing display and non HDR type are unsupported`() {
        val missing = probe(displayTypes = { null }).probe()
        val dolbyOnly = probe(
            displayTypes = {
                intArrayOf(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
            },
        ).probe()

        assertFalse(missing.displaySupported!!)
        assertFalse(dolbyOnly.displaySupported!!)
    }

    @Test
    fun `HDR10 among multiple display types is supported`() {
        val result = probe(
            displayTypes = {
                intArrayOf(
                    Display.HdrCapabilities.HDR_TYPE_HLG,
                    Display.HdrCapabilities.HDR_TYPE_HDR10,
                )
            },
        ).probe()

        assertTrue(result.displaySupported!!)
    }

    @Test
    fun `HEVC HDR10 decoder profile is detected and encoder is ignored`() {
        val profile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
        val encoderOnly = probe(
            codecs = {
                listOf(
                    AndroidCodecCapability(true, mapOf("video/hevc" to setOf(profile))),
                )
            },
        ).probe()
        val decoder = probe(
            codecs = {
                listOf(
                    AndroidCodecCapability(false, mapOf("VIDEO/HEVC" to setOf(profile))),
                )
            },
        ).probe()

        assertFalse(encoderOnly.decoderSupported!!)
        assertTrue(decoder.decoderSupported!!)
    }

    @Test
    fun `AV1 HDR10 profile requires Android Q`() {
        val profile = MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10
        val codec = listOf(
            AndroidCodecCapability(false, mapOf("video/av01" to setOf(profile))),
        )

        assertFalse(probe(sdkInt = Build.VERSION_CODES.P, codecs = { codec }).probe()
            .decoderSupported!!)
        assertTrue(probe(sdkInt = Build.VERSION_CODES.Q, codecs = { codec }).probe()
            .decoderSupported!!)
    }

    @Test
    fun `codec enumeration failure is unknown and fail closed`() {
        val result = probe(
            codecs = { throw IllegalStateException("codec service unavailable") },
        ).probe()

        assertNull(result.decoderSupported)
        assertEquals(
            LibraryHdrReason.DECODER_CAPABILITY_UNKNOWN,
            LibraryHdrStateResolver.resolve(
                hostEncodingSupported = true,
                displaySupported = true,
                decoderSupported = result.decoderSupported,
                userEnabled = true,
            ).reason,
        )
    }

    @Test
    fun `user disabled and unknown remain resolver inputs without new precedence`() {
        val disabled = probe(userEnabled = { false }).probe()
        val unknown = probe(userEnabled = { null }).probe()

        assertEquals(
            LibraryHdrReason.USER_DISABLED,
            LibraryHdrStateResolver.resolve(
                hostEncodingSupported = true,
                displaySupported = true,
                decoderSupported = true,
                userEnabled = disabled.userEnabled,
            ).reason,
        )
        assertEquals(
            LibraryHdrReason.AVAILABLE,
            LibraryHdrStateResolver.resolve(
                hostEncodingSupported = true,
                displaySupported = true,
                decoderSupported = true,
                userEnabled = unknown.userEnabled,
            ).reason,
        )
    }

    private fun probe(
        sdkInt: Int = Build.VERSION_CODES.Q,
        displayTypes: () -> IntArray? = {
            intArrayOf(Display.HdrCapabilities.HDR_TYPE_HDR10)
        },
        codecs: () -> List<AndroidCodecCapability> = { emptyList() },
        userEnabled: () -> Boolean? = { true },
    ) = AndroidHdrCapabilityProbe(
        sdkInt = sdkInt,
        displayHdrTypes = displayTypes,
        codecCapabilities = codecs,
        userHdrEnabled = userEnabled,
    )
}
