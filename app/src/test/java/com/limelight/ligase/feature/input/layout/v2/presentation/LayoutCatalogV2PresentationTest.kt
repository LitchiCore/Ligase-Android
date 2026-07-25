package com.limelight.ligase.feature.input.layout.v2.presentation

import com.limelight.ligase.feature.input.layout.v2.domain.AspectRatio
import com.limelight.ligase.feature.input.layout.v2.domain.DeviceClass
import com.limelight.ligase.feature.input.layout.v2.domain.IntSize
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2CompatibilityDetails
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2DesignReferenceHint
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2LocalState
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiItem
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiVariant
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2VariantSummary
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutLocalAvailability
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutLocalOrigin
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutOrientation
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutPublication
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutVariantSelection
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutVariantSelectionCode
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutWorkspaceState
import com.limelight.ligase.feature.input.layout.v2.domain.SafeAreaPolicy
import com.limelight.ligase.layout.LayoutCompatibilityV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCatalogV2PresentationTest {
    @Test
    fun `grid breakpoints preserve phone and wide layouts`() {
        assertEquals(1, layoutCatalogV2Columns(719))
        assertEquals(2, layoutCatalogV2Columns(720))
        assertEquals(2, layoutCatalogV2Columns(1_199))
        assertEquals(3, layoutCatalogV2Columns(1_200))
    }

    @Test
    fun `verified ready eligible variant is the only selectable shape`() {
        val item = item()
        val variant = item.variants.single()

        assertTrue(canSelectLayoutVariant(item, variant))
        assertFalse(
            canSelectLayoutVariant(
                item.copy(contentVerified = false),
                variant,
            ),
        )
        assertFalse(
            canSelectLayoutVariant(
                item.copy(
                    local = item.local.copy(
                        availability = LayoutLocalAvailability.NOT_LOCAL,
                    ),
                ),
                variant,
            ),
        )
        assertFalse(
            canSelectLayoutVariant(
                item.copy(publication = LayoutPublication.RETIRED),
                variant,
            ),
        )
        assertFalse(
            canSelectLayoutVariant(
                item,
                variant.copy(
                    summary = variant.summary.copy(eligible = false),
                ),
            ),
        )
    }

    @Test
    fun `presentation uses verified display name and explicit preference`() {
        val presentation = item().toPresentation()

        assertEquals("Phone controls", presentation.title)
        assertEquals("variant-a", presentation.selectedVariantId)
        assertEquals(
            LayoutCatalogV2AvailabilityPresentation.READY,
            presentation.availability,
        )
        assertFalse(presentation.needsVariantSelection)
    }

    private fun item(): LayoutCatalogV2UiItem {
        val ratio = AspectRatio(16, 9)
        return LayoutCatalogV2UiItem(
            layoutId = "11111111-1111-4111-8111-111111111111",
            revision = 4,
            displayName = "Phone controls",
            publication = LayoutPublication.PUBLISHED,
            compatibility = LayoutCompatibilityV1(1, 1),
            portableIdentities = emptyList(),
            local = LayoutCatalogV2LocalState(
                origin = LayoutLocalOrigin.LOCAL_COPY,
                availability = LayoutLocalAvailability.READY,
                workspace = LayoutWorkspaceState.NONE,
            ),
            contentVerified = true,
            variants = listOf(
                LayoutCatalogV2UiVariant(
                    summary = LayoutCatalogV2VariantSummary(
                        variantId = "variant-a",
                        descriptorEligible = true,
                        eligible = true,
                        ineligibilityReasons = emptySet(),
                        compatibilityHints = emptySet(),
                    ),
                    deviceClasses = listOf(DeviceClass.PHONE),
                    orientations = listOf(LayoutOrientation.PORTRAIT),
                    compatibility = LayoutCatalogV2CompatibilityDetails(
                        preferredAspectRatio = ratio,
                        minAspectRatio = ratio,
                        maxAspectRatio = ratio,
                        minShortestSideDp = 360,
                        minTouchTargetDp = 48,
                        safeAreaPolicy = SafeAreaPolicy.VIDEO_CONTENT,
                        canvas = IntSize(1920, 1080),
                        designReferenceHint =
                            LayoutCatalogV2DesignReferenceHint(420, IntSize(1080, 2400)),
                    ),
                ),
            ),
            preferredVariantId = "variant-a",
            selection = LayoutVariantSelection(
                code = LayoutVariantSelectionCode.SELECTED,
                variantId = "variant-a",
            ),
        )
    }
}
