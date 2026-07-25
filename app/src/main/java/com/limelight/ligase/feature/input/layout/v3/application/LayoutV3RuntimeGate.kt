package com.limelight.ligase.feature.input.layout.v3.application

enum class LayoutV3RuntimeUnavailableReason {
    NOT_IMPLEMENTED,
}

sealed interface LayoutV3RuntimeDecision {
    data class Unavailable(val reason: LayoutV3RuntimeUnavailableReason) :
        LayoutV3RuntimeDecision
}

/**
 * The sole product runtime gate for v3 layouts.
 *
 * Runtime mapping and input dispatch are not implemented yet, so no catalog,
 * preference, legacy profile, or UI state can make a layout executable.
 */
object LayoutV3RuntimeGate {
    fun current(): LayoutV3RuntimeDecision =
        LayoutV3RuntimeDecision.Unavailable(LayoutV3RuntimeUnavailableReason.NOT_IMPLEMENTED)
}
