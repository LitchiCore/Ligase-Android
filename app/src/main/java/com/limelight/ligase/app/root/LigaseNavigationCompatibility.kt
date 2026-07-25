package com.limelight.ligase

typealias LigasePage = com.limelight.ligase.app.navigation.LigasePage
typealias LigaseNavigationPlacement =
    com.limelight.ligase.app.navigation.LigaseNavigationPlacement

fun ligaseNavigationPlacement(
    screenWidthDp: Int,
    orientation: Int,
): LigaseNavigationPlacement =
    com.limelight.ligase.app.navigation.ligaseNavigationPlacement(
        screenWidthDp = screenWidthDp,
        orientation = orientation,
    )
