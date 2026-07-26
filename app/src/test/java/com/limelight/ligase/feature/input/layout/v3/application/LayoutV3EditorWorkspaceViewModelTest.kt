package com.limelight.ligase.feature.input.layout.v3.application

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorPhase
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorExitCode
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorIssue
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorLaunchMode
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutV3EditorWorkspaceViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = cleanup()
    @After fun tearDown() = cleanup()

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

    @Test
    fun committedGenerationIsProjectedAfterFreshWorkspaceConstructionAndReopensTyped() {
        val editor = LayoutV3EditorActivityViewModel(
            application,
            SavedStateHandle(
                mapOf(
                    LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_MODE to
                        LayoutV3EditorLaunchMode.NEW_V3.name,
                    LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DISPLAY_NAME to "Saved",
                ),
            ),
        )
        editor.initializeNewV3(
            EditorTargetViewport(2400, 1080, LayoutOrientation.LANDSCAPE),
        )
        editor.addElement(ControlKind.SOFT_KEYBOARD)
        assertEquals(LayoutV3EditorExitCode.SAVED, editor.saveAndFinish().code)

        val restarted = LayoutV3EditorWorkspaceViewModel(application)
        val summary = restarted.state.value.committedLayouts.single()
        assertEquals("Saved", summary.displayName)
        assertTrue(summary.contentVerified)
        assertTrue(summary.localCopy)
        assertFalse(summary.runtimeExecutable)
        assertEquals(
            LayoutV3EditorLaunchMode.EXISTING_V3,
            restarted.launchCommitted(
                summary.layoutId,
                summary.revision,
                summary.variants.single().variantId,
            )!!.mode,
        )
    }

    @Test
    fun workspaceOpacityActionPublishesTypedRejectionWithoutWriting() {
        val owner = LayoutV3EditorWorkspaceViewModel(application)
        owner.setOpacityPermille(
            "10000000-0000-0000-0000-000000000099",
            500,
        )
        assertEquals(
            LayoutV3EditorIssue.NO_ACTIVE_DRAFT,
            owner.state.value.lastAction?.issue,
        )
        assertNull(owner.state.value.editor.draft)
    }

    private fun cleanup() {
        listOf("ligase-touch-layout-v3-drafts", "ligase-touch-layout-v3-generations")
            .forEach { File(application.filesDir, it).deleteRecursively() }
    }
}
