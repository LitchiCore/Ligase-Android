package com.limelight.ligase

import android.content.res.Configuration

enum class LigaseNavigationPlacement {
    BOTTOM,
    SIDE,
}

fun ligaseNavigationPlacement(
    screenWidthDp: Int,
    orientation: Int,
): LigaseNavigationPlacement =
    if (
        orientation == Configuration.ORIENTATION_LANDSCAPE ||
        screenWidthDp >= 600
    ) {
        LigaseNavigationPlacement.SIDE
    } else {
        LigaseNavigationPlacement.BOTTOM
    }
