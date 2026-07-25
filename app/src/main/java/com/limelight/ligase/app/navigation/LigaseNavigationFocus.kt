package com.limelight.ligase.app.navigation

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.limelight.ligase.LigaseSemanticTheme

internal data class LigaseNavigationFocusPresentation(
    val showRing: Boolean,
    val ringWidthDp: Int,
)

internal fun ligaseNavigationFocusPresentation(
    focused: Boolean,
    enabled: Boolean,
): LigaseNavigationFocusPresentation = LigaseNavigationFocusPresentation(
    showRing = focused && enabled,
    ringWidthDp = 3,
)

internal fun Modifier.ligaseNavigationFocusIndicator(
    enabled: Boolean,
    shape: Shape,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val presentation = ligaseNavigationFocusPresentation(
        focused = focused,
        enabled = enabled,
    )
    val focusColor = LigaseSemanticTheme.colors.focus
    onFocusChanged { focused = it.isFocused }
        .then(
            if (presentation.showRing) {
                Modifier.border(
                    width = presentation.ringWidthDp.dp,
                    color = focusColor,
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}
