package com.limelight.ligase.feature.input.layout.v3.application

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorPhase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LayoutV3EditorWorkspaceViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun newDraftUsesStableFullOverlayViewportExactly() {
        val owner = LayoutV3EditorWorkspaceViewModel(application)
        owner.beginNewV3("Viewport")
        assertEquals(LayoutV3WorkspaceLaunchPhase.AWAITING_VIEWPORT, owner.state.value.launchPhase)
        owner.initializeNewV3(
            EditorTargetViewport(2340, 1080, LayoutOrientation.LANDSCAPE),
        )
        assertEquals(LayoutV3EditorPhase.EDITING, owner.state.value.editor.phase)
        assertEquals(IntSize(2340, 1080), owner.state.value.editor.draft!!.canvas)
    }

    @Test
    fun invalidViewportFailsClosedWithoutDraft() {
        val owner = LayoutV3EditorWorkspaceViewModel(application)
        owner.beginNewV3(null)
        owner.initializeNewV3(
            EditorTargetViewport(0, 1080, LayoutOrientation.LANDSCAPE),
        )
        assertNull(owner.state.value.editor.draft)
        assertNotNull(owner.state.value.lastAction)
    }
}
