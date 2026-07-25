package com.limelight.ligase.feature.input.layout.v3

import com.google.gson.JsonParser
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Codec
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Encoder
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Exception
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TouchLayoutV3CoreTest {
    @Test
    fun frozenManifestAndPositiveArtifactDecodeAndRoundTrip() {
        fixture("ligase-touch-layout-v3-sha256.txt").readLines().forEach { line ->
            val (expected, relative) = line.split("  ", limit = 2)
            assertEquals(relative, expected, sha256(repoRoot().resolve(relative).readBytes()))
        }
        val raw = fixture("ligase-touch-layout-v3-positive.json").readBytes()
        val document = TouchLayoutV3Codec.decode(raw)
        assertEquals(3L, JsonParser.parseString(String(raw)).asJsonObject["schemaVersion"].asLong)
        assertEquals(1L, document.nextKeyboardBatchOrdinal)
        assertEquals((0..9).toSet(), document.variants.single().elements.map { it.kind.ordinal }.toSet())
        assertEquals(document, TouchLayoutV3Codec.decode(TouchLayoutV3Encoder.encode(document)))
    }

    @Test
    fun signedRoundingAnchorsPartialOffscreenOverlapAndHitAreFrozen() {
        assertEquals(0, LayoutV3Geometry.roundHalfUp(-1, 2))
        assertEquals(-1, LayoutV3Geometry.roundHalfUp(-3, 2))
        val canvas = IntSize(1000, 600)
        val element = element(
            rect = AnchoredRect(-60, 20, 100, 100),
            anchorX = HorizontalAnchor.LEFT,
            anchorY = VerticalAnchor.TOP,
            z = 0,
        )
        assertEquals(IntRect(-60, 20, 100, 100), LayoutV3Geometry.resolve(canvas, element))
        assertEquals(
            IntRect(-96, 53, 267, 267),
            LayoutV3Geometry.map(canvas, element, IntRect(0, 0, 1600, 1600)),
        )
        val rebased = LayoutV3Geometry.rebase(canvas, IntRect(550, 250, 100, 100))
        assertEquals(HorizontalAnchor.CENTER, rebased.first.first)
        assertEquals(VerticalAnchor.BOTTOM, rebased.first.second)
        assertEquals(IntRect(550, 250, 100, 100), LayoutV3Geometry.resolve(
            canvas,
            element.copy(rect = rebased.second, anchorX = rebased.first.first, anchorY = rebased.first.second),
        ))
        val upper = element.copy(elementId = "00000000-0000-0000-0000-000000000002", zOrder = 1)
        assertEquals(
            upper.elementId,
            LayoutV3Geometry.hitTest(
                listOf(element to IntRect(0, 0, 100, 100), upper to IntRect(0, 0, 100, 100)),
                50,
                50,
            ),
        )
    }

    @Test
    fun fullOverlayInverseMapperRoundTripsResolvedRectWithoutUiProtocolMath() {
        val canvas = IntSize(2400, 1080)
        val overlay = IntRect(0, 0, 3200, 1440)
        val element = element(
            rect = AnchoredRect(-40, -20, 120, 96),
            anchorX = HorizontalAnchor.RIGHT,
            anchorY = VerticalAnchor.BOTTOM,
            z = 0,
        )
        val mapped = LayoutV3Geometry.map(canvas, element, overlay)
        assertEquals(
            LayoutV3Geometry.resolve(canvas, element),
            LayoutV3Geometry.unmapResolvedRect(
                canvas,
                element.anchorX,
                element.anchorY,
                overlay,
                mapped,
            ),
        )
    }

    @Test
    fun unsupportedVersionAndDuplicateZOrderFailClosedWhileOverlapIsAccepted() {
        val raw = fixture("ligase-touch-layout-v3-positive.json").readText()
        val unsupported = raw.replace("\"schemaVersion\": 3", "\"schemaVersion\": 2")
        assertEquals("unsupportedSchema", rejectedCode(unsupported.toByteArray()))
        val document = TouchLayoutV3Codec.decode(raw.toByteArray())
        val variant = document.variants.single()
        val overlapping = variant.elements[1].copy(
            rect = variant.elements[0].rect,
            anchorX = variant.elements[0].anchorX,
            anchorY = variant.elements[0].anchorY,
        )
        TouchLayoutV3Validator.validate(
            document.copy(variants = listOf(variant.copy(elements = listOf(variant.elements[0], overlapping)))),
        )
        val duplicateZ = overlapping.copy(zOrder = variant.elements[0].zOrder)
        val error = runCatching {
            TouchLayoutV3Validator.validate(
                document.copy(variants = listOf(variant.copy(elements = listOf(variant.elements[0], duplicateZ)))),
            )
        }.exceptionOrNull() as TouchLayoutV3Exception
        assertEquals("duplicateZOrder", error.code)
    }

    @Test
    fun keyboardBatchIdentityUsesCanonicalOrderAndPersistedOrdinal() {
        val android29 = InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 29)
        val android51 = InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 51)
        val hid4 = InputCode(InputCodeNamespace.USB_HID_KEYBOARD_USAGE, 4)
        assertEquals(
            listOf(android29, android51, hid4),
            LayoutV3KeyboardBatchIdentity.canonicalKeys(setOf(hid4, android51, android29)),
        )
        val layoutId = "00000000-0000-0000-0000-000000000001"
        assertEquals(
            "3964593b-4c3d-53ea-9f4c-fca879599257",
            LayoutV3KeyboardBatchIdentity.elementId(layoutId, 1, android29),
        )
        assertEquals(
            "aa8bc924-476b-5055-ae4a-72e4a03b77c3",
            LayoutV3KeyboardBatchIdentity.elementId(layoutId, 2, android29),
        )
    }

    @Test
    fun strictRawAndSchemaBoundariesRejectBeforeMaterialization() {
        val raw = fixture("ligase-touch-layout-v3-positive.json").readText()
        assertEquals(
            "negativeZero",
            rejectedCode(raw.replace("\"nextKeyboardBatchOrdinal\": 1", "\"nextKeyboardBatchOrdinal\": -0").toByteArray()),
        )
        assertEquals(
            "duplicateKey",
            rejectedCode(raw.replace("\"schemaVersion\": 3,", "\"schemaVersion\": 3,\"schemaVersion\": 3,").toByteArray()),
        )
        assertEquals(
            "schemaRejected",
            rejectedCode(raw.replace("\"nextKeyboardBatchOrdinal\": 1,", "").toByteArray()),
        )
        assertEquals("invalidUtf8", rejectedCode(byteArrayOf(0xc3.toByte(), 0x28)))
    }

    private fun element(
        rect: AnchoredRect,
        anchorX: HorizontalAnchor,
        anchorY: VerticalAnchor,
        z: Int,
    ) = TouchLayoutV3Element(
        elementId = "00000000-0000-0000-0000-000000000001",
        kind = ControlKind.SOFT_KEYBOARD,
        rect = rect,
        anchorX = anchorX,
        anchorY = anchorY,
        zOrder = z,
        enabled = true,
        hidden = false,
        opacityPermille = 1000,
        payload = SoftKeyboardPayload,
        sourceReference = null,
    )

    private fun rejectedCode(raw: ByteArray): String =
        (runCatching { TouchLayoutV3Codec.decode(raw) }.exceptionOrNull() as TouchLayoutV3Exception).code

    private fun fixture(name: String) = repoRoot().resolve("tests/fixtures/$name")
    private fun repoRoot(): File {
        var current = checkNotNull(File(System.getProperty("user.dir")).canonicalFile)
        while (!current.resolve("tests/fixtures").isDirectory) current = checkNotNull(current.parentFile)
        return current
    }
    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
