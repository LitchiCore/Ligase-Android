package com.limelight.ligase.feature.input.layout.v3.application

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorPhase
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorLaunchMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LayoutV3EditorWorkspaceViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun newDraftLaunchRequestDoesNotCreateHallSession() {
        val owner = LayoutV3EditorWorkspaceViewModel(application)
        val request = owner.beginNewV3("Viewport")
        assertEquals(LayoutV3WorkspaceLaunchPhase.AWAITING_VIEWPORT, owner.state.value.launchPhase)
        assertEquals(LayoutV3EditorLaunchMode.NEW_V3, request!!.mode)
        assertNull(request.draftId)
        assertNull(owner.state.value.editor.draft)
    }

    @Test
    fun duplicateHallCreateWhileDraftActiveFailsClosed() {
        val owner = LayoutV3EditorWorkspaceViewModel(application)
        owner.beginNewV3(null)
        assertNull(owner.state.value.editor.draft)
        assertEquals(LayoutV3WorkspaceLaunchPhase.AWAITING_VIEWPORT, owner.state.value.launchPhase)
    }
}
