package com.limelight.ligase.feature.input.layout.v2.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v2.application.TouchLayoutV2ContentVerifier
import com.limelight.ligase.feature.input.layout.v2.data.*
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Encoder
import com.limelight.ligase.layout.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutV2LocalCreatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture: ByteArray by lazy {
        File(
            System.getProperty("user.dir"),
            "../tests/fixtures/ligase-touch-layout-v2-built-in-all-types.json",
        ).readBytes()
    }
    private lateinit var content: VerifiedTouchLayoutV2Content

    @Before
    fun setUp() {
        cleanup()
        content = (
            TouchLayoutV2ContentVerifier.verify(fixture) as LayoutContentVerificationResult.Verified
            ).content
    }

    @After
    fun tearDown() = cleanup()

    @Test
    fun encoder_roundTripsAllKindsAndHash() {
        val encoded = TouchLayoutV2Encoder.encode(content.document)
        val verified = TouchLayoutV2ContentVerifier.verify(encoded)
        assertTrue(verified.toString(), verified is LayoutContentVerificationResult.Verified)
        val document = (verified as LayoutContentVerificationResult.Verified).content.document
        assertEquals(ControlKind.entries.toSet(), document.variants.single().elements.map { it.kind }.toSet())
        assertEquals(content.document.extensions, document.extensions)
    }

    @Test
    fun journal_isStrictSortedAndDoesNotRegressClock() {
        var now = 2_000L
        val journal = LayoutV2DraftJournal(context, { now }, {})
        val first = identity(content.document, LayoutV2DraftOrigin.LOCAL_COPY)
        assertEquals(LayoutV2JournalWriteResult.SAVED, journal.write(first, fixture))
        now = 1_000L
        assertEquals(LayoutV2JournalWriteResult.SAVED, journal.write(first, fixture))
        val ready = journal.read(first.layoutId) as LayoutV2JournalReadResult.Ready
        assertEquals(2_000L, ready.entry.updatedAtEpochMillis)
        assertFalse(ready.entry.toString().contains(String(fixture)))
        assertEquals(first.layoutId, journal.summaries().single().draftId)
        assertFalse(journal.summaries().single().toString().contains("2000"))
    }

    @Test
    fun generation_commitReadbackAndExportAreExact() {
        val repository = LayoutV2GenerationRepository(context)
        val descriptor = descriptor(content.document)
        assertEquals(
            LayoutV2GenerationWriteCode.SAVED,
            repository.commit(descriptor, fixture).code,
        )
        val committed = requireNotNull(repository.read(descriptor.layoutId, descriptor.revision))
        assertEquals(descriptor, committed.descriptor)
        assertArrayEquals(fixtureCanonical(), committed.artifact)
        assertArrayEquals(committed.artifact, repository.export(descriptor.layoutId, descriptor.revision))
    }

    @Test
    fun generation_readbackFailureRollsBackCandidate() {
        val repository = LayoutV2GenerationRepository(context) { file ->
            file.writeText("{\"corrupt\":true}")
        }
        val descriptor = descriptor(content.document)
        assertEquals(
            LayoutV2GenerationWriteCode.READBACK_FAILED,
            repository.commit(descriptor, fixture).code,
        )
        assertNull(repository.read(descriptor.layoutId, descriptor.revision))
    }

    @Test
    fun corruptJournalIsQuarantinedWithoutTouchingCommittedGeneration() {
        val draftId = content.document.layoutId
        val draftDirectory = File(context.filesDir, "ligase-touch-layout-v2-drafts")
        draftDirectory.mkdirs()
        File(draftDirectory, "$draftId.draft.json").writeText("{\"bad\":true}")
        val journal = LayoutV2DraftJournal(context)
        assertTrue(journal.read(draftId) is LayoutV2JournalReadResult.Quarantined)
        assertTrue(
            File(draftDirectory, "quarantine/$draftId.draft.json").isFile,
        )
    }

    @Test
    fun session_preservesReadonlyKindsAndRejectsTheirMutation() {
        val journal = LayoutV2DraftJournal(context)
        val generations = LayoutV2GenerationRepository(context)
        val ids = ArrayDeque(
            listOf(
                "10000000-0000-0000-0000-000000000001",
                "10000000-0000-0000-0000-000000000002",
            ),
        )
        val session = LayoutV2EditorSession(journal, generations, { true }, ids::removeFirst)
        val source = LayoutV2CreatorSource(
            descriptor(content.document),
            content,
            LayoutV2DraftOrigin.LOCAL_COPY,
            LayoutLocalAvailability.READY,
            LayoutWorkspaceState.DRAFT,
        )
        assertEquals(
            LayoutV2EditResult.Applied,
            session.createFromLocalCopy(source, content.document.variants.single().variantId),
        )
        val readonly = session.state.draft!!.elements.first {
            it.kind == ControlKind.RADIAL
        }
        assertNull(readonly.editableProperties)
        assertEquals(LayoutV2ReadonlyReason.UNSUPPORTED_EDITOR_KIND, readonly.inspectOnlySummary!!.readonlyReason)
        assertTrue(LayoutV2ElementCapability.INSPECT_ONLY in readonly.capabilities)
        val result = session.moveElement(readonly.elementId, 1, 1)
        assertEquals(LayoutV2EditorIssue.READ_ONLY_KIND, (result as LayoutV2EditResult.Rejected).issue)
        assertEquals(LayoutV2JournalWriteResult.SAVED, session.flushJournal())
        assertTrue(session.state.recoveryProtection == LayoutV2RecoveryProtection.SAVED)
        val editableKinds = session.state.draft!!.elements
            .filter { it.kind in setOf(
                ControlKind.KEYBOARD, ControlKind.MOUSE, ControlKind.ANALOG,
                ControlKind.DPAD, ControlKind.SOFT_KEYBOARD,
            ) }
        assertTrue(editableKinds.all { it.editableProperties != null })
        val keyboard = editableKinds.first { it.kind == ControlKind.KEYBOARD }
        assertEquals(
            LayoutV2EditResult.Applied,
            session.moveElement(keyboard.elementId, keyboard.rect.x + 1, keyboard.rect.y),
        )
        session.close()
    }

    @Test
    fun packagedTemplateRequiresReadyPackagedSource() {
        val session = LayoutV2EditorSession(
            LayoutV2DraftJournal(context),
            LayoutV2GenerationRepository(context),
            { true },
        )
        val source = LayoutV2CreatorSource(
            descriptor(content.document),
            content,
            LayoutV2DraftOrigin.PACKAGED_TEMPLATE,
            LayoutLocalAvailability.NOT_LOCAL,
            LayoutWorkspaceState.NONE,
        )
        val result = session.createFromPackagedTemplate(
            source,
            content.document.variants.single().variantId,
        )
        assertEquals(
            LayoutV2EditorIssue.SOURCE_NOT_READY,
            (result as LayoutV2EditResult.Rejected).issue,
        )
        session.close()
    }

    @Test
    fun formalSaveCommitsRegistersThenDeletesJournal() {
        val journal = LayoutV2DraftJournal(context)
        val generations = LayoutV2GenerationRepository(context)
        val ids = ArrayDeque(
            listOf(
                "20000000-0000-0000-0000-000000000001",
                "20000000-0000-0000-0000-000000000002",
            ),
        )
        var registrations = 0
        val session = LayoutV2EditorSession(
            journal,
            generations,
            { registrations++; true },
            ids::removeFirst,
        )
        val source = LayoutV2CreatorSource(
            descriptor(content.document),
            content,
            LayoutV2DraftOrigin.LOCAL_COPY,
            LayoutLocalAvailability.READY,
            LayoutWorkspaceState.DRAFT,
        )
        session.createFromLocalCopy(source, content.document.variants.single().variantId)
        assertEquals(LayoutV2JournalWriteResult.SAVED, session.flushJournal())
        val saved = session.saveDraft()
        assertTrue(saved is LayoutV2SaveResult.Saved)
        assertEquals(1, registrations)
        assertTrue(journal.summaries().isEmpty())
        val value = saved as LayoutV2SaveResult.Saved
        assertNotNull(generations.read(value.layoutId, value.revision))
        val exported = session.exportCommittedArtifact(value.layoutId, value.revision)
        assertTrue(exported is LayoutV2ExportResult.Ready)
        assertFalse(exported.toString().contains("contentHash"))
        session.close()
    }

    @Test
    fun blankDraftIsRecoverableButNotCatalogReadyUntilItHasAnElement() {
        val session = LayoutV2EditorSession(
            LayoutV2DraftJournal(context),
            LayoutV2GenerationRepository(context),
            { true },
            ArrayDeque(
                listOf(
                    "30000000-0000-0000-0000-000000000001",
                    "30000000-0000-0000-0000-000000000002",
                ),
            )::removeFirst,
        )
        val request = LayoutV2CreateBlankRequest(
            "Blank",
            IntSize(1920, 1080),
            listOf(DeviceClass.PHONE),
            listOf(LayoutOrientation.LANDSCAPE),
            content.document.variants.single().recommendation,
        )
        assertEquals(LayoutV2EditResult.Applied, session.createBlank(request))
        assertFalse(session.state.candidateReady)
        assertEquals(LayoutV2JournalWriteResult.SAVED, session.flushJournal())
        assertTrue(session.saveDraft() is LayoutV2SaveResult.Rejected)
        session.close()
    }

    @Test
    fun registrationFailureRollsBackGenerationAndRetainsJournal() {
        val journal = LayoutV2DraftJournal(context)
        val generations = LayoutV2GenerationRepository(context)
        val ids = ArrayDeque(
            listOf(
                "40000000-0000-0000-0000-000000000001",
                "40000000-0000-0000-0000-000000000002",
            ),
        )
        val session = LayoutV2EditorSession(journal, generations, { false }, ids::removeFirst)
        session.createFromLocalCopy(
            LayoutV2CreatorSource(
                descriptor(content.document),
                content,
                LayoutV2DraftOrigin.LOCAL_COPY,
                LayoutLocalAvailability.READY,
                LayoutWorkspaceState.DRAFT,
            ),
            content.document.variants.single().variantId,
        )
        session.flushJournal()
        val rejected = session.saveDraft() as LayoutV2SaveResult.Rejected
        assertEquals(LayoutV2EditorIssue.REGISTRATION_FAILED, rejected.issue)
        assertEquals(1, journal.summaries().size)
        val identity = requireNotNull(session.state.draft).identity
        assertNull(generations.read(identity.layoutId, identity.revision))
        session.close()
    }

    private fun fixtureCanonical(): ByteArray = TouchLayoutV2Encoder.encode(content.document)

    private fun identity(
        document: TouchLayoutV2Document,
        origin: LayoutV2DraftOrigin,
    ) = LayoutV2DraftIdentity(
        document.layoutId,
        document.revision,
        document.variants.single().variantId,
        origin,
    )

    private fun descriptor(document: TouchLayoutV2Document) = LayoutDescriptorV1(
        1,
        document.layoutId,
        document.revision,
        emptyList(),
        LayoutCompatibilityV1(1, 1),
        "draft",
        document.variants.map {
            LayoutVariantV1(
                it.variantId,
                "touch",
                it.deviceClasses.map { value -> value.name.lowercase() },
                it.orientations.map { value -> value.name.lowercase() },
            )
        },
    )

    private fun cleanup() {
        listOf(
            "ligase-touch-layout-v2-drafts",
            "ligase-touch-layout-v2-generations",
        ).forEach { File(context.filesDir, it).deleteRecursively() }
    }
}
