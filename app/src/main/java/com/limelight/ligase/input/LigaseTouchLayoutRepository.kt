package com.limelight.ligase.input

import android.content.Context
@Deprecated("v1 layout repository is closed; residual type is removed after frontend cutover")
class LigaseTouchLayoutRepository(
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    fun layouts(): List<LigaseTouchLayout> = emptyList()
    fun initializeSelection(@Suppress("UNUSED_PARAMETER") available: List<LigaseTouchLayout>):
        String? = null
    fun select(
        @Suppress("UNUSED_PARAMETER") layoutId: String,
        @Suppress("UNUSED_PARAMETER") available: List<LigaseTouchLayout>,
    ): Boolean = false
}
