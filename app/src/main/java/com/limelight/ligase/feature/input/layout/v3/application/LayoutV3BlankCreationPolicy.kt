package com.limelight.ligase.feature.input.layout.v3.application

import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3CreateBlankRequest

sealed interface LayoutV3BlankCreationDecision {
    data class Ready(val request: LayoutV3CreateBlankRequest) : LayoutV3BlankCreationDecision
    data object InvalidDisplayName : LayoutV3BlankCreationDecision
    data object InvalidViewport : LayoutV3BlankCreationDecision
}

class LayoutV3BlankCreationPolicy {
    fun create(displayName: String?, viewport: EditorTargetViewport): LayoutV3BlankCreationDecision {
        val name = displayName?.trim().orEmpty().ifEmpty { "Untitled layout" }
        if (name.length > 80) return LayoutV3BlankCreationDecision.InvalidDisplayName
        if (viewport.widthPx !in 1..32768 || viewport.heightPx !in 1..32768) {
            return LayoutV3BlankCreationDecision.InvalidViewport
        }
        val actual = if (viewport.widthPx >= viewport.heightPx) LayoutOrientation.LANDSCAPE else LayoutOrientation.PORTRAIT
        if (viewport.orientation != actual) return LayoutV3BlankCreationDecision.InvalidViewport
        val canvas = IntSize(viewport.widthPx, viewport.heightPx)
        return LayoutV3BlankCreationDecision.Ready(
            LayoutV3CreateBlankRequest(
                displayName = name,
                canvas = canvas,
                deviceClasses = listOf(DeviceClass.PHONE),
                orientations = listOf(viewport.orientation),
                recommendation = LayoutRecommendation(
                    preferredAspectRatio = AspectRatio(canvas.width, canvas.height),
                    minAspectRatio = AspectRatio(canvas.width, canvas.height),
                    maxAspectRatio = AspectRatio(canvas.width, canvas.height),
                    minShortestSideDp = 1,
                    minTouchTargetDp = 1,
                    referenceDensityDpi = null,
                    referenceResolution = canvas,
                ),
            ),
        )
    }
}
