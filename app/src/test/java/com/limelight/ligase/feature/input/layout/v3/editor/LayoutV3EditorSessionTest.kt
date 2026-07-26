package com.limelight.ligase.feature.input.layout.v3.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3DraftJournal
import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3GenerationRepository
import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3JournalWriteResult
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.serialization.LayoutV3ContentVerificationResult
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3ContentVerifier
import com.limelight.ligase.layout.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutV3EditorSessionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var content: VerifiedTouchLayoutV3Content

    @Before fun setUp() {
        cleanup()
        content = (TouchLayoutV3ContentVerifier.verify(fixture()) as
            LayoutV3ContentVerificationResult.Verified).content
    }

    @After fun tearDown() = cleanup()

    @Test
    fun signedMoveRebasesWithoutJumpAndOverlapRemainsFormalValid() {
        val session = session()
        activate(session)
        val editable = session.state.draft!!.elements.first { it.kind == ControlKind.KEYBOARD }
        assertEquals(LayoutV3EditResult.Applied, session.moveElement(editable.elementId, -40, 20))
        val moved = session.state.draft!!.elements.first { it.elementId == editable.elementId }
        assertEquals(IntRect(-40, 20, editable.resolvedRect.width, editable.resolvedRect.height), moved.resolvedRect)
        val other = session.state.draft!!.elements.first { it.kind == ControlKind.MOUSE }
        assertEquals(
            LayoutV3EditResult.Applied,
            session.moveElement(other.elementId, moved.resolvedRect.x, moved.resolvedRect.y),
        )
        assertTrue(session.validateDraft())
        session.close()
    }

    @Test
    fun gestureTokenCommitsAtMostOnceAndStaleReadbackIsRejected() {
        val session = session()
        activate(session)
        val element = session.state.draft!!.elements.first { it.kind == ControlKind.KEYBOARD }
        val token = (session.beginGesture(element.elementId) as LayoutV3GestureStartResult.Ready).token
        assertEquals(LayoutV3EditResult.Applied, session.commitMove(token, 10, 20))
        val repeated = session.commitMove(token, 30, 40) as LayoutV3EditResult.Rejected
        assertEquals(LayoutV3EditorIssue.STALE_GESTURE, repeated.issue)
        assertEquals(10, session.state.draft!!.elements.first { it.elementId == element.elementId }.resolvedRect.x)
        session.close()
    }

    @Test
    fun keyboardBatchIsAtomicCanonicalAndOrdinalSurvivesSaveReopen() {
        val session = session()
        activate(session)
        val before = session.state.draft!!.elements.size
        val keys = setOf(
            InputCode(InputCodeNamespace.USB_HID_KEYBOARD_USAGE, 4),
            InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 51),
            InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 29),
        )
        assertEquals(LayoutV3EditResult.Applied, session.addKeyboardKeys(keys))
        val added = session.state.draft!!.elements.takeLast(3)
        assertEquals(listOf(29, 51, 4), added.map { (it.editableProperties as LayoutV3EditableProperties.Keyboard).inputCode.code })
        assertEquals(before + 3, session.state.draft!!.elements.size)
        assertEquals(1, added.map { it.resolvedRect }.distinct().size)
        assertEquals(LayoutV3JournalWriteResult.SAVED, session.flushJournal())
        val saved = session.saveDraft() as LayoutV3SaveResult.Saved
        val reopened = requireNotNull(LayoutV3GenerationRepository(context).read(saved.layoutId, saved.revision))
        val verified = (TouchLayoutV3ContentVerifier.verify(reopened.artifact) as
            LayoutV3ContentVerificationResult.Verified).content.document
        assertEquals(2L, verified.nextKeyboardBatchOrdinal)
        session.close()
    }

    @Test
    fun opacityUsesFrozenRangeAndSurvivesJournalAndFormalReadback() {
        val session = session()
        activate(session)
        val element = session.state.draft!!.elements.first { it.kind == ControlKind.KEYBOARD }
        val propertiesBefore = element.editableProperties

        assertEquals(LayoutV3EditResult.Applied, session.setOpacityPermille(element.elementId, 0))
        assertEquals(LayoutV3EditResult.Applied, session.setOpacityPermille(element.elementId, 1000))
        assertEquals(LayoutV3EditResult.Applied, session.setOpacityPermille(element.elementId, 375))
        val after = session.state.draft!!.elements.first { it.elementId == element.elementId }
        assertEquals(375, after.opacityPermille)
        assertEquals(propertiesBefore, after.editableProperties)

        assertEquals(LayoutV3JournalWriteResult.SAVED, session.flushJournal())
        val draftId = session.state.recoverableDrafts.single().draftId
        val recovered = session()
        assertEquals(LayoutV3EditResult.Applied, recovered.resumeRecoverableDraft(draftId))
        assertEquals(375, recovered.state.draft!!.elements.first {
            it.elementId == element.elementId
        }.opacityPermille)

        val saved = recovered.saveDraft() as LayoutV3SaveResult.Saved
        val generation = requireNotNull(
            LayoutV3GenerationRepository(context).read(saved.layoutId, saved.revision),
        )
        val document = (TouchLayoutV3ContentVerifier.verify(generation.artifact) as
            LayoutV3ContentVerificationResult.Verified).content.document
        assertEquals(375, document.variants.single().elements.first {
            it.elementId == element.elementId
        }.opacityPermille)
        session.close()
        recovered.close()
    }

    @Test
    fun invalidOrUnknownOpacityFailsClosedWithoutMutation() {
        val session = session()
        activate(session)
        val element = session.state.draft!!.elements.first { it.kind == ControlKind.KEYBOARD }
        val before = session.state.draft

        listOf(-1, 1001).forEach { invalid ->
            val rejected = session.setOpacityPermille(element.elementId, invalid) as
                LayoutV3EditResult.Rejected
            assertEquals(LayoutV3EditorIssue.INVALID_OPACITY, rejected.issue)
            assertEquals(element.elementId, rejected.elementId)
            assertEquals(before, session.state.draft)
        }
        val unknown = session.setOpacityPermille(
            "10000000-0000-0000-0000-000000000099",
            500,
        ) as LayoutV3EditResult.Rejected
        assertEquals(LayoutV3EditorIssue.UNKNOWN_ELEMENT, unknown.issue)
        assertEquals(before, session.state.draft)
        session.close()
    }

    private fun session(): LayoutV3EditorSession {
        val ids = ArrayDeque(listOf(
            "10000000-0000-0000-0000-000000000001",
            "10000000-0000-0000-0000-000000000002",
        ))
        return LayoutV3EditorSession(
            LayoutV3DraftJournal(context),
            LayoutV3GenerationRepository(context),
            { true },
            ids::removeFirst,
        )
    }

    private fun activate(session: LayoutV3EditorSession) {
        assertEquals(
            LayoutV3EditResult.Applied,
            session.createFromLocalCopy(
                LayoutV3CreatorSource(
                    descriptor(content.document),
                    content,
                    LayoutV3DraftOrigin.LOCAL_COPY,
                    true,
                    LayoutV3WorkspaceState.DRAFT,
                ),
                content.document.variants.single().variantId,
            ),
        )
    }

    private fun descriptor(document: TouchLayoutV3Document) = LayoutDescriptorV1(
        1, document.layoutId, document.revision, emptyList(),
        LayoutCompatibilityV1(1, 1), "draft",
        document.variants.map {
            LayoutVariantV1(
                it.variantId, "touch",
                it.deviceClasses.map { value -> value.name.lowercase() },
                it.orientations.map { value -> value.name.lowercase() },
            )
        },
    )

    private fun fixture() = File(
        System.getProperty("user.dir"),
        "../tests/fixtures/ligase-touch-layout-v3-positive.json",
    ).readBytes()

    private fun cleanup() {
        listOf("ligase-touch-layout-v3-drafts", "ligase-touch-layout-v3-generations")
            .forEach { File(context.filesDir, it).deleteRecursively() }
    }
}
