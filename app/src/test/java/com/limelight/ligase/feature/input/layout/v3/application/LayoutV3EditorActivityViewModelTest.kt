package com.limelight.ligase.feature.input.layout.v3.application

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutV3EditorActivityViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = cleanup()
    @After fun tearDown() = cleanup()

    @Test
    fun newModeCreatesExactlyOnceAfterStableFullOverlayAndPersistsOpaqueIdentity() {
        val handle = SavedStateHandle(
            mapOf(
                LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_MODE to
                    LayoutV3EditorLaunchMode.NEW_V3.name,
                LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DISPLAY_NAME to "New",
            ),
        )
        val owner = LayoutV3EditorActivityViewModel(application, handle)
        assertEquals(LayoutV3EditorHandoffResult.AwaitingViewport, owner.handoff.value)
        val ready = owner.initializeNewV3(
            EditorTargetViewport(2400, 1080, LayoutOrientation.LANDSCAPE),
        ) as LayoutV3EditorHandoffResult.LaunchReady
        assertEquals(IntSize(2400, 1080), owner.state.value.editor.draft!!.canvas)
        assertTrue(
            owner.initializeNewV3(
                EditorTargetViewport(2400, 1080, LayoutOrientation.LANDSCAPE),
            ) is LayoutV3EditorHandoffResult.Rejected,
        )
        owner.closeForTest()

        val restarted = LayoutV3EditorActivityViewModel(application, handle)
        assertEquals(LayoutV3EditorHandoffResult.LaunchReady(ready.draftId), restarted.handoff.value)
        assertEquals(IntSize(2400, 1080), restarted.state.value.editor.draft!!.canvas)
        restarted.closeForTest()
    }

    @Test
    fun tokenBoundPixelCommitUsesAuthoritativeInverseAndRejectsDoubleCommit() {
        val owner = newOwner()
        owner.addElement(ControlKind.SOFT_KEYBOARD)
        val element = owner.state.value.editor.draft!!.elements.single()
        val token = (owner.beginGesture(element.elementId) as LayoutV3GestureStartResult.Ready).token
        val overlay = IntRect(0, 0, 2400, 1080)
        val target = IntRect(120, 90, element.resolvedRect.width, element.resolvedRect.height)
        assertEquals(LayoutV3EditResult.Applied, owner.commitPixelMove(token, overlay, target))
        assertEquals(120, owner.state.value.editor.draft!!.elements.single().resolvedRect.x)
        val repeated = owner.commitPixelMove(token, overlay, target) as LayoutV3EditResult.Rejected
        assertEquals(LayoutV3EditorIssue.STALE_GESTURE, repeated.issue)
        owner.closeForTest()
    }

    @Test
    fun invalidPixelCommitConsumesTokenAndPreservesAuthoritativeReadback() {
        val owner = newOwner()
        owner.addElement(ControlKind.SOFT_KEYBOARD)
        val before = owner.state.value.editor.draft!!.elements.single().resolvedRect
        val token = (owner.beginGesture(owner.state.value.editor.draft!!.elements.single().elementId)
            as LayoutV3GestureStartResult.Ready).token
        val result = owner.commitPixelResize(
            token,
            IntRect(0, 0, 0, 1080),
            IntRect(0, 0, 100, 100),
        ) as LayoutV3EditResult.Rejected
        assertEquals(LayoutV3EditorIssue.INVALID_RECT, result.issue)
        assertEquals(before, owner.state.value.editor.draft!!.elements.single().resolvedRect)
        owner.closeForTest()
    }

    @Test
    fun pixelResizeCommitsTheWholeAuthoritativeRectOnce() {
        val owner = newOwner()
        owner.addElement(ControlKind.SOFT_KEYBOARD)
        val element = owner.state.value.editor.draft!!.elements.single()
        val token = (owner.beginGesture(element.elementId) as LayoutV3GestureStartResult.Ready).token
        val target = IntRect(200, 100, 360, 180)
        assertEquals(
            LayoutV3EditResult.Applied,
            owner.commitPixelResize(token, IntRect(0, 0, 2400, 1080), target),
        )
        assertEquals(target, owner.state.value.editor.draft!!.elements.single().resolvedRect)
        owner.closeForTest()
    }

    @Test
    fun circlePixelResizeUsesBackendDominantAxisAndPersistsSquareReadback() {
        val owner = newOwner()
        owner.addElement(ControlKind.KEYBOARD)
        val element = owner.state.value.editor.draft!!.elements.single()
        val token = (owner.beginGesture(element.elementId) as LayoutV3GestureStartResult.Ready).token
        val target = element.resolvedRect.copy(
            width = element.resolvedRect.width + 80,
            height = element.resolvedRect.height + 20,
        )

        assertEquals(
            LayoutV3EditResult.Applied,
            owner.commitPixelResize(token, IntRect(0, 0, 2400, 1080), target),
        )
        val readback = owner.state.value.editor.draft!!.elements.single().resolvedRect
        assertEquals(readback.width, readback.height)
        assertEquals(element.resolvedRect.width + 80, readback.width)
        owner.closeForTest()
    }

    @Test
    fun directCircleResizeCannotPersistEllipse() {
        val owner = newOwner()
        owner.addElement(ControlKind.MOUSE)
        val element = owner.state.value.editor.draft!!.elements.single()

        assertEquals(
            LayoutV3EditResult.Applied,
            owner.resizeElement(
                element.elementId,
                element.resolvedRect.width + 80,
                element.resolvedRect.height + 20,
            ),
        )
        val readback = owner.state.value.editor.draft!!.elements.single().resolvedRect
        assertEquals(element.resolvedRect.width + 80, readback.width)
        assertEquals(readback.width, readback.height)
        owner.closeForTest()
    }

    @Test
    fun opacityActionPublishesAuthoritativeReadbackAndDoesNotReviveStaleGesture() {
        val owner = newOwner()
        owner.addElement(ControlKind.SOFT_KEYBOARD)
        val element = owner.state.value.editor.draft!!.elements.single()
        val token = (owner.beginGesture(element.elementId) as LayoutV3GestureStartResult.Ready).token
        assertEquals(LayoutV3EditResult.Applied, owner.cancelGesture(token))

        owner.setLayoutOpacityPermille(425)
        assertEquals(425, owner.state.value.editor.draft!!.opacityPermille)
        val stale = owner.commitMove(token, 20, 30) as LayoutV3EditResult.Rejected
        assertEquals(LayoutV3EditorIssue.STALE_GESTURE, stale.issue)
        assertEquals(425, owner.state.value.editor.draft!!.opacityPermille)
        owner.closeForTest()
    }

    @Test
    fun configuredComboAndRadialCreationPublishesAuthoritativeElements() {
        val owner = newOwner()
        val combo = owner.addComboElement(
            listOf(InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 29)),
            label = "Combo",
        ) as LayoutV3ElementCreateResult.Created
        val radial = owner.addRadialElement(
            listOf(
                LayoutV3NewRadialActionRequest(
                    "Left",
                    listOf(InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 21)),
                ),
                LayoutV3NewRadialActionRequest(
                    "Right",
                    listOf(InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 22)),
                ),
            ),
            label = "Radial",
        ) as LayoutV3ElementCreateResult.Created

        val elements = owner.state.value.editor.draft!!.elements
        assertTrue(elements.any { it.elementId == combo.elementId && it.kind == ControlKind.COMBO })
        assertTrue(elements.any { it.elementId == radial.elementId && it.kind == ControlKind.RADIAL })
        assertEquals(
            LayoutV3WorkspaceActionCode.APPLIED,
            requireNotNull(owner.state.value.lastAction).code,
        )
        owner.closeForTest()
    }

    private fun newOwner(): LayoutV3EditorActivityViewModel {
        val owner = LayoutV3EditorActivityViewModel(
            application,
            "",
            LayoutV3DraftLeaseRegistry(),
            LayoutV3EditorLaunchMode.NEW_V3,
            "Gesture",
        )
        assertTrue(
            owner.initializeNewV3(
                EditorTargetViewport(2400, 1080, LayoutOrientation.LANDSCAPE),
            ) is LayoutV3EditorHandoffResult.LaunchReady,
        )
        return owner
    }

    private fun cleanup() {
        listOf("ligase-touch-layout-v3-drafts", "ligase-touch-layout-v3-generations")
            .forEach { File(application.filesDir, it).deleteRecursively() }
    }
}
