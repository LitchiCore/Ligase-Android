package com.limelight.ligase.feature.input.layout.v2.application

import com.limelight.ligase.feature.input.layout.v2.domain.AspectRatio
import com.limelight.ligase.feature.input.layout.v2.domain.DeviceClass
import com.limelight.ligase.feature.input.layout.v2.domain.IntSize
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutOrientation
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutRecommendation
import com.limelight.ligase.feature.input.layout.v2.domain.SafeAreaPolicy
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2CreateBlankRequest

sealed interface LayoutV2BlankCreationDecision {
    data class Ready(val request: LayoutV2CreateBlankRequest) : LayoutV2BlankCreationDecision
    data object InvalidDisplayName : LayoutV2BlankCreationDecision
}

/**
 * The single owner of blank-layout defaults exposed through the application seam.
 *
 * UI callers may supply a display name, but must not construct canvas, device,
 * orientation, safe-area, aspect, touch-target, density, or reference defaults.
 */
class LayoutV2BlankCreationPolicy {
    fun create(displayName: String?): LayoutV2BlankCreationDecision {
        val normalizedName = displayName?.trim().orEmpty().ifEmpty { FALLBACK_DISPLAY_NAME }
        if (normalizedName.length > MAX_DISPLAY_NAME_LENGTH) {
            return LayoutV2BlankCreationDecision.InvalidDisplayName
        }
        return LayoutV2BlankCreationDecision.Ready(
            LayoutV2CreateBlankRequest(
                displayName = normalizedName,
                canvas = IntSize(REFERENCE_WIDTH, REFERENCE_HEIGHT),
                deviceClasses = listOf(DeviceClass.PHONE),
                orientations = listOf(LayoutOrientation.LANDSCAPE),
                recommendation = LayoutRecommendation(
                    preferredAspectRatio = AspectRatio(16, 9),
                    minAspectRatio = AspectRatio(4, 3),
                    maxAspectRatio = AspectRatio(32, 9),
                    minShortestSideDp = 320,
                    minTouchTargetDp = 48,
                    safeAreaPolicy = SafeAreaPolicy.VIDEO_CONTENT,
                    referenceDensityDpi = null,
                    referenceResolution = IntSize(REFERENCE_WIDTH, REFERENCE_HEIGHT),
                ),
            ),
        )
    }

    private companion object {
        const val FALLBACK_DISPLAY_NAME = "Untitled layout"
        const val MAX_DISPLAY_NAME_LENGTH = 80
        const val REFERENCE_WIDTH = 1920
        const val REFERENCE_HEIGHT = 1080
    }
}
