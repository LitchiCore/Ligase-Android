package com.limelight.ligase.feature.input.ui

import android.os.Bundle
import com.limelight.ligase.feature.input.application.GameInputOverrideEditorAction
import com.limelight.ligase.input.LigaseCloudTouchMode
import com.limelight.ligase.input.LigaseTouchOverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameInputOverrideDialogFragmentTest {
    private val uuid = "67209ea3-7129-42d0-9349-52f8799d292d"

    @Test
    fun saveResultRoundTripsCanonicalTypedAction() {
        val action = GameInputOverrideEditorAction.Save(
            uuid,
            LigaseTouchOverlayMode.CLOUD_CONTROLS,
            LigaseCloudTouchMode.TRACKPAD,
        )
        assertEquals(
            action,
            GameInputOverrideDialogFragment.resultFrom(
                GameInputOverrideDialogFragment.resultBundle(action),
            ),
        )
    }

    @Test
    fun missingOrNonCanonicalArgumentsFailClosed() {
        assertNull(GameInputOverrideDialogFragment.resultFrom(Bundle()))
        assertNull(
            GameInputOverrideDialogFragment.resultFrom(
                GameInputOverrideDialogFragment.resultBundle(
                    GameInputOverrideEditorAction.Clear(uuid.uppercase()),
                ),
            ),
        )
    }
}
