package com.limelight.ligase.feature.input.layout.v2.application

import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Codec
import com.limelight.ligase.layout.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LayoutCatalogV2ProjectorTest {
    @Test
    fun explicitPreferenceWinsWithoutScoringVariants() {
        val content = contentWithTwoEligibleVariants()
        val descriptor = descriptorFor(content.document)

        val result = LayoutCatalogV2Projector.project(
            descriptor,
            content,
            readyLocal(),
            compatibleContext(),
            SECOND_VARIANT_ID,
        ).item()

        assertEquals(LayoutVariantSelectionCode.SELECTED, result.selection.code)
        assertEquals(SECOND_VARIANT_ID, result.selection.variantId)
        assertEquals(LayoutVariantSelectionSource.EXPLICIT_PREFERENCE, result.selection.source)
        assertEquals(
            listOf(FIRST_VARIANT_ID, SECOND_VARIANT_ID),
            result.variants.map { it.variantId },
        )
    }

    @Test
    fun multipleEligibleVariantsRequireSelectionAndReferenceFieldsAreHintsOnly() {
        val content = contentWithTwoEligibleVariants()
        val result = LayoutCatalogV2Projector.project(
            descriptorFor(content.document),
            content,
            readyLocal(),
            compatibleContext(),
            preferredVariantId = null,
        ).item()

        assertEquals(LayoutVariantSelectionCode.NEEDS_VARIANT_SELECTION, result.selection.code)
        assertNull(result.selection.variantId)
        assertTrue(result.variants.all { it.eligible })
        assertTrue(
            result.variants.all {
                LayoutVariantCompatibilityHint.REFERENCE_RESOLUTION_DIAGNOSTIC_ONLY in
                    it.compatibilityHints
            },
        )
    }

    @Test
    fun onlyEligibleVariantIsSelectedWithoutUsingReferenceResolution() {
        val content = contentWithTwoEligibleVariants(
            secondRecommendation = baseDocument().variants.single().recommendation.copy(
                minShortestSideDp = 900,
            ),
        )
        val result = LayoutCatalogV2Projector.project(
            descriptorFor(content.document),
            content,
            readyLocal(),
            compatibleContext(),
            preferredVariantId = null,
        ).item()

        assertEquals(LayoutVariantSelectionCode.SELECTED, result.selection.code)
        assertEquals(FIRST_VARIANT_ID, result.selection.variantId)
        assertEquals(LayoutVariantSelectionSource.ONLY_ELIGIBLE, result.selection.source)
        assertEquals(
            setOf(LayoutVariantCompatibilityHint.SHORTEST_SIDE_BELOW_MINIMUM),
            result.variants.single { it.variantId == SECOND_VARIANT_ID }
                .compatibilityHints
                .filterNot { it.name.startsWith("REFERENCE_") }
                .toSet(),
        )
    }

    @Test
    fun contentAvailabilityAndAlignmentFailClosedWithoutInventingPublication() {
        val content = verified(baseDocument())
        val descriptor = descriptorFor(content.document)
        val notLocal = LayoutCatalogV2Projector.project(
            descriptor,
            content = null,
            local = readyLocal().copy(availability = LayoutLocalAvailability.NOT_LOCAL),
            context = compatibleContext(),
            preferredVariantId = null,
        ).item()
        assertEquals(LayoutVariantSelectionCode.CONTENT_NOT_READY, notLocal.selection.code)
        assertEquals(LayoutPublication.PUBLISHED, notLocal.publication)
        assertNull(notLocal.contentHash)

        val wrongDescriptor = descriptor.copy(revision = descriptor.revision + 1)
        val misaligned = LayoutCatalogV2Projector.project(
            wrongDescriptor,
            content,
            readyLocal(),
            compatibleContext(),
            preferredVariantId = null,
        ).item()
        assertEquals(LayoutVariantSelectionCode.INVALID_ALIGNMENT, misaligned.selection.code)
        assertEquals(LayoutPublication.PUBLISHED, misaligned.publication)
    }

    @Test
    fun preferenceActionAcceptsOnlyCurrentEligibleVariantAndDoesNotPersist() {
        val content = contentWithTwoEligibleVariants(
            secondRecommendation = baseDocument().variants.single().recommendation.copy(
                minShortestSideDp = 900,
            ),
        )
        val descriptor = descriptorFor(content.document)

        assertEquals(
            LayoutPreferenceV2Result.Accepted(
                descriptor.layoutId,
                descriptor.revision,
                FIRST_VARIANT_ID,
            ),
            LayoutPreferenceV2Action.select(
                descriptor,
                content,
                readyLocal(),
                compatibleContext(),
                FIRST_VARIANT_ID,
            ),
        )
        assertEquals(
            LayoutPreferenceV2Result.Rejected(LayoutPreferenceV2Rejection.INELIGIBLE_VARIANT),
            LayoutPreferenceV2Action.select(
                descriptor,
                content,
                readyLocal(),
                compatibleContext(),
                SECOND_VARIANT_ID,
            ),
        )
        assertEquals(
            LayoutPreferenceV2Result.Rejected(LayoutPreferenceV2Rejection.UNKNOWN_VARIANT),
            LayoutPreferenceV2Action.select(
                descriptor,
                content,
                readyLocal(),
                compatibleContext(),
                "00000000-0000-0000-0000-000000000099",
            ),
        )
    }

    @Test
    fun verifiedContentFactoryRejectsUntrustedBytesBeforeProjection() {
        val rejected = TouchLayoutV2ContentVerifier.verify("""{"format":"wrong"}""".toByteArray())
        assertTrue(rejected is LayoutContentVerificationResult.Rejected)
        val verified = TouchLayoutV2ContentVerifier.verify(positiveFile().readBytes())
        assertTrue(verified is LayoutContentVerificationResult.Verified)
    }

    @Test
    fun malformedDescriptorIsTypedAndNeverProjected() {
        val content = verified(baseDocument())
        val malformed = descriptorFor(content.document).copy(publicationStatus = "unknown")
        assertEquals(
            LayoutCatalogV2ProjectionResult.Rejected(
                LayoutCatalogV2ProjectionRejection.INVALID_DESCRIPTOR,
            ),
            LayoutCatalogV2Projector.project(
                malformed,
                content,
                readyLocal(),
                compatibleContext(),
                preferredVariantId = null,
            ),
        )
        assertEquals(
            LayoutPreferenceV2Result.Rejected(LayoutPreferenceV2Rejection.INVALID_DESCRIPTOR),
            LayoutPreferenceV2Action.select(
                malformed,
                content,
                readyLocal(),
                compatibleContext(),
                FIRST_VARIANT_ID,
            ),
        )
    }

    private fun contentWithTwoEligibleVariants(
        secondRecommendation: LayoutRecommendation =
            baseDocument().variants.single().recommendation.copy(referenceDensityDpi = 420),
    ): VerifiedTouchLayoutV2Content {
        val document = baseDocument()
        val first = document.variants.single()
        val second = first.copy(
            variantId = SECOND_VARIANT_ID,
            recommendation = secondRecommendation,
            elements = first.elements.mapIndexed { index, element ->
                element.copy(
                    elementId = "00000000-0000-0000-0001-${index.toString(16).padStart(12, '0')}",
                )
            },
        )
        return verified(document.copy(variants = listOf(first, second)))
    }

    private fun descriptorFor(document: TouchLayoutV2Document) = LayoutDescriptorV1(
        schemaVersion = 1,
        layoutId = document.layoutId,
        revision = document.revision,
        portableIdentities = listOf(PortableGameIdentityV1("steam", "123")),
        compatibility = LayoutCompatibilityV1(1, 1),
        publicationStatus = "published",
        variants = document.variants.map { variant ->
            LayoutVariantV1(
                variant.variantId,
                "touch",
                variant.deviceClasses.map { it.name.lowercase() },
                variant.orientations.map { it.name.lowercase() },
            )
        },
    )

    private fun compatibleContext() = LayoutCatalogV2Context(
        clientContractVersion = 1,
        layoutRuntimeVersion = 1,
        deviceClass = DeviceClass.PHONE,
        orientation = LayoutOrientation.LANDSCAPE,
        videoAspectRatio = AspectRatio(16, 9),
        shortestSideDp = 720,
        touchTargetDp = 48,
    )

    private fun readyLocal() = LayoutCatalogV2LocalState(
        LayoutLocalOrigin.PACKAGED_BUILT_IN,
        LayoutLocalAvailability.READY,
        LayoutWorkspaceState.NONE,
    )

    private fun baseDocument() = TouchLayoutV2Codec.decode(positiveFile().readBytes())
    private fun verified(document: TouchLayoutV2Document) = VerifiedTouchLayoutV2Content(document)
    private fun LayoutCatalogV2ProjectionResult.item(): LayoutCatalogV2Item =
        (this as LayoutCatalogV2ProjectionResult.Projected).item
    private fun positiveFile(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (!current.resolve("tests/fixtures").isDirectory) {
            current = current.parentFile ?: error("Repository root not found")
        }
        return current.resolve("tests/fixtures/ligase-touch-layout-v2-built-in-all-types.json")
    }

    private companion object {
        const val FIRST_VARIANT_ID = "00000000-0000-0000-0000-000000000002"
        const val SECOND_VARIANT_ID = "00000000-0000-0000-0000-000000000003"
    }
}
