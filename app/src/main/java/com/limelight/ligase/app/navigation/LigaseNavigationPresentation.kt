package com.limelight.ligase.app.navigation

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.limelight.R

enum class LigasePage(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    HOME(R.string.ligase_nav_home, R.drawable.ic_computer),
    INPUT(R.string.ligase_nav_input, R.drawable.ic_ligase_gamepad),
    SETTINGS(R.string.ligase_nav_settings, R.drawable.ic_settings),
}

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

fun ligaseNavigationDestinationEnabled(
    destination: LigasePage,
    inputEnabled: Boolean,
): Boolean = destination != LigasePage.INPUT || inputEnabled

@StringRes
fun ligaseNavigationHeader(destination: LigasePage): Int =
    if (destination == LigasePage.HOME) {
        R.string.ligase_library_title
    } else {
        destination.label
    }
