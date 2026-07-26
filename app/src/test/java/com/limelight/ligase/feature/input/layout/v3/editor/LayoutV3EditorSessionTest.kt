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
        assertEquals(LayoutV3EditResult.Applied, session.setLayoutOpacityPermille(0))
        assertEquals(LayoutV3EditResult.Applied, session.setLayoutOpacityPermille(1000))
        assertEquals(LayoutV3EditResult.Applied, session.setLayoutOpacityPermille(375))
        assertEquals(375, session.state.draft!!.opacityPermille)

        assertEquals(LayoutV3JournalWriteResult.SAVED, session.flushJournal())
        val draftId = session.state.recoverableDrafts.single().draftId
        val recovered = session()
        assertEquals(LayoutV3EditResult.Applied, recovered.resumeRecoverableDraft(draftId))
        assertEquals(375, recovered.state.draft!!.opacityPermille)

        val saved = recovered.saveDraft() as LayoutV3SaveResult.Saved
        val generation = requireNotNull(
            LayoutV3GenerationRepository(context).read(saved.layoutId, saved.revision),
        )
        val document = (TouchLayoutV3ContentVerifier.verify(generation.artifact) as
            LayoutV3ContentVerificationResult.Verified).content.document
        assertEquals(375, document.opacityPermille)
        session.close()
        recovered.close()
    }

    @Test
    fun invalidOrUnknownOpacityFailsClosedWithoutMutation() {
        val session = session()
        activate(session)
        val before = session.state.draft

        listOf(-1, 1001).forEach { invalid ->
            val rejected = session.setLayoutOpacityPermille(invalid) as
                LayoutV3EditResult.Rejected
            assertEquals(LayoutV3EditorIssue.OUT_OF_RANGE, rejected.issue)
            assertEquals(before, session.state.draft)
        }
        session.close()
    }

    @Test
    fun comboAndRadialMutationsAreCanonicalAtomicAndPersistIdentity() {
        val session = session()
        activate(session)
        val combo = session.state.draft!!.elements.first { it.kind == ControlKind.COMBO }
        val radial = session.state.draft!!.elements.first { it.kind == ControlKind.RADIAL }
        val rawChord = listOf(
            InputCode(InputCodeNamespace.USB_HID_KEYBOARD_USAGE, 4),
            InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 29),
        )
        assertEquals(LayoutV3EditResult.Applied, session.replaceComboChord(combo.elementId, rawChord))
        val canonicalCombo = session.state.draft!!.elements.first { it.elementId == combo.elementId }
            .editableProperties as LayoutV3EditableProperties.Combo
        assertEquals(listOf(29, 4), canonicalCombo.keys.map { it.code })

        val added = session.addRadialAction(radial.elementId, "Action", rawChord)
            as LayoutV3RadialEditResult.Applied
        assertNotNull(added.actionId)
        assertEquals(listOf(29, 4), added.radial.actions.last().keys.map { it.code })
        val beforeInvalid = session.state.draft
        val duplicate = session.replaceRadialActionChord(
            radial.elementId,
            checkNotNull(added.actionId),
            listOf(rawChord.first(), rawChord.first()),
        ) as LayoutV3RadialEditResult.Rejected
        assertEquals(LayoutV3EditorIssue.DUPLICATE_KEY, duplicate.issue)
        assertEquals(beforeInvalid, session.state.draft)

        assertEquals(LayoutV3JournalWriteResult.SAVED, session.flushJournal())
        val saved = session.saveDraft() as LayoutV3SaveResult.Saved
        val generation = requireNotNull(
            LayoutV3GenerationRepository(context).read(saved.layoutId, saved.revision),
        )
        val reopened = (TouchLayoutV3ContentVerifier.verify(generation.artifact) as
            LayoutV3ContentVerificationResult.Verified).content.document
        val reopenedRadial = reopened.variants.single().elements.first { it.elementId == radial.elementId }
            .payload as RadialPayload
        assertTrue(reopenedRadial.actions.any { it.actionId == added.actionId })
        session.close()
    }

    @Test
    fun chordActionsExposeFrozenErrorsAndValidateTargetsBeforePayload() {
        val session = session()
        activate(session)
        val combo = session.state.draft!!.elements.first { it.kind == ControlKind.COMBO }
        val radial = session.state.draft!!.elements.first { it.kind == ControlKind.RADIAL }
        val radialProperties = radial.editableProperties as LayoutV3EditableProperties.Radial
        val actionId = radialProperties.actions.first().actionId
        val key = InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 29)

        fun comboIssue(keys: List<InputCode>) =
            (session.replaceComboChord(combo.elementId, keys) as LayoutV3EditResult.Rejected).issue
        assertEquals(LayoutV3EditorIssue.EMPTY_CHORD, comboIssue(emptyList()))
        assertEquals(LayoutV3EditorIssue.TOO_MANY_KEYS, comboIssue(List(17) { InputCode(InputCodeNamespace.ANDROID_KEY_CODE, it) }))
        assertEquals(LayoutV3EditorIssue.UNSUPPORTED_INPUT_CODE, comboIssue(listOf(key.copy(code = -1))))
        assertEquals(LayoutV3EditorIssue.DUPLICATE_KEY, comboIssue(listOf(key, key)))

        val unknown = session.replaceRadialActionChord(
            radial.elementId,
            "00000000-0000-0000-0000-000000000000",
            emptyList(),
        ) as LayoutV3RadialEditResult.Rejected
        assertEquals(LayoutV3EditorIssue.UNKNOWN_ACTION, unknown.issue)

        val invalidLabel = session.replaceRadialActionLabel(
            radial.elementId,
            actionId,
            "",
        ) as LayoutV3RadialEditResult.Rejected
        assertEquals(LayoutV3EditorIssue.INVALID_LABEL, invalidLabel.issue)
        session.close()
    }

    private fun session(): LayoutV3EditorSession {
        val ids = ArrayDeque(listOf(
            "10000000-0000-0000-0000-000000000001",
            "10000000-0000-0000-0000-000000000002",
            "10000000-0000-0000-0000-000000000003",
            "10000000-0000-0000-0000-000000000004",
            "10000000-0000-0000-0000-000000000005",
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
