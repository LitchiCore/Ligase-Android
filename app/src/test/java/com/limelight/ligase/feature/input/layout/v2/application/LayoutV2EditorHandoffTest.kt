package com.limelight.ligase.feature.input.layout.v2.application

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorExitCode
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorHandoffIssue
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorHandoffResult
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorPhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutV2EditorHandoffTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = cleanup()

    @After
    fun tearDown() = cleanup()

    @Test
    fun workspaceCheckpointReleasesDraftForExclusiveActivityOwner() {
        val workspace = LayoutV2EditorWorkspaceViewModel(application)
        workspace.createBlank("Black editor")
        val draftId = workspace.state.value.editor.draft!!.identity.layoutId

        assertEquals(
            LayoutV2EditorHandoffResult.LaunchReady(draftId),
            workspace.checkpointAndRelease(draftId),
        )
        assertNull(workspace.state.value.editor.draft)

        val editor = LayoutV2EditorActivityViewModel(
            application,
            draftId,
            LayoutV2EditorProcessLeases.registry,
        )
        assertEquals(
            LayoutV2EditorHandoffResult.LaunchReady(draftId),
            editor.handoff.value,
        )
        assertEquals(LayoutV2EditorPhase.EDITING, editor.state.value.editor.phase)
        val kept = editor.keepDraftAndFinish()
        assertEquals(
            kept.toString(),
            LayoutV2EditorExitCode.LEFT_RECOVERABLE,
            kept.code,
        )
    }

    @Test
    fun duplicateOwnerAndLateReleaseAreRejectedByExactGeneration() {
        val registry = LayoutV2DraftLeaseRegistry()
        val draftId = "00000000-0000-0000-0000-000000000001"
        val first = registry.acquire(draftId, "first")!!
        assertNull(registry.acquire(draftId, "second"))
        assertTrue(registry.release(first))
        val second = registry.acquire(draftId, "second")!!

        assertEquals(false, registry.release(first))
        assertTrue(registry.isCurrent(second))
        assertTrue(registry.release(second))
    }

    @Test
    fun processStyleRestartResumesOnlyFromCheckpointedJournal() {
        val registry = LayoutV2DraftLeaseRegistry()
        val workspace = LayoutV2EditorWorkspaceViewModel(application)
        workspace.createBlank("Restart")
        val draftId = workspace.state.value.editor.draft!!.identity.layoutId
        assertTrue(workspace.checkpointAndRelease(draftId) is
            LayoutV2EditorHandoffResult.LaunchReady)
        val first = LayoutV2EditorActivityViewModel(application, draftId, registry)
        assertTrue(first.handoff.value is LayoutV2EditorHandoffResult.LaunchReady)
        val kept = first.keepDraftAndFinish()
        assertEquals(
            kept.toString(),
            LayoutV2EditorExitCode.LEFT_RECOVERABLE,
            kept.code,
        )

        val restarted = LayoutV2EditorActivityViewModel(application, draftId, registry)
        assertTrue(restarted.handoff.value is LayoutV2EditorHandoffResult.LaunchReady)
        restarted.closeForTest()
    }

    @Test
    fun invalidMissingAndMismatchedDraftsFailClosed() {
        val registry = LayoutV2DraftLeaseRegistry()
        val invalid = LayoutV2EditorActivityViewModel(application, "not-a-uuid", registry)
        assertEquals(
            LayoutV2EditorHandoffResult.Rejected(
                LayoutV2EditorHandoffIssue.INVALID_DRAFT_ID,
            ),
            invalid.handoff.value,
        )

        val missingId = "00000000-0000-0000-0000-000000000001"
        val missing = LayoutV2EditorActivityViewModel(application, missingId, registry)
        assertEquals(
            LayoutV2EditorHandoffResult.Rejected(LayoutV2EditorHandoffIssue.MISSING),
            missing.handoff.value,
        )

        val workspace = LayoutV2EditorWorkspaceViewModel(application)
        workspace.createBlank("Mismatch")
        val activeId = workspace.state.value.editor.draft!!.identity.layoutId
        assertEquals(
            LayoutV2EditorHandoffResult.Rejected(
                LayoutV2EditorHandoffIssue.DRAFT_ID_MISMATCH,
            ),
            workspace.checkpointAndRelease(missingId),
        )
        assertEquals(activeId, workspace.state.value.editor.draft!!.identity.layoutId)
        assertTrue(workspace.checkpointAndRelease(activeId) is
            LayoutV2EditorHandoffResult.LaunchReady)
    }

    @Test
    fun activitySaveClosesLeaseAndRemovesRecoverableJournal() {
        val registry = LayoutV2DraftLeaseRegistry()
        val workspace = LayoutV2EditorWorkspaceViewModel(application)
        workspace.createBlank("Saved")
        val draftId = workspace.state.value.editor.draft!!.identity.layoutId
        assertTrue(workspace.checkpointAndRelease(draftId) is
            LayoutV2EditorHandoffResult.LaunchReady)

        val editor = LayoutV2EditorActivityViewModel(application, draftId, registry)
        editor.addElement(com.limelight.ligase.feature.input.layout.v2.domain.ControlKind.SOFT_KEYBOARD)
        val saved = editor.saveAndFinish()
        assertEquals(saved.toString(), LayoutV2EditorExitCode.SAVED, saved.code)

        val afterSave = LayoutV2EditorActivityViewModel(application, draftId, registry)
        assertEquals(
            LayoutV2EditorHandoffResult.Rejected(LayoutV2EditorHandoffIssue.MISSING),
            afterSave.handoff.value,
        )
    }

    private fun cleanup() {
        listOf(
            "ligase-touch-layout-v2-drafts",
            "ligase-touch-layout-v2-generations",
        ).forEach { File(application.filesDir, it).deleteRecursively() }
    }
}
