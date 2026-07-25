package com.limelight.ligase.feature.input.layout.v3.data

enum class LayoutV3CutoverState {
    NOT_STARTED,
    QUARANTINING,
    VERIFYING,
    FAILED_CLOSED,
    V3_READY,
}

class LayoutV3RepositoryClosedException : IllegalStateException("layoutV3CutoverNotReady")

class LayoutV3RepositoryGate internal constructor(
    private val stateProvider: () -> LayoutV3CutoverState,
) {
    fun requireReady() {
        if (stateProvider() != LayoutV3CutoverState.V3_READY) {
            throw LayoutV3RepositoryClosedException()
        }
    }
}
