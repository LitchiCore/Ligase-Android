package com.limelight.ligase.feature.input.layout.v2.application

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v2.domain.AspectRatio
import com.limelight.ligase.feature.input.layout.v2.domain.DeviceClass
import com.limelight.ligase.feature.input.layout.v2.domain.ControlKind
import com.limelight.ligase.feature.input.layout.v2.domain.HorizontalAnchor
import com.limelight.ligase.feature.input.layout.v2.domain.IntRect
import com.limelight.ligase.feature.input.layout.v2.domain.IntSize
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutOrientation
import com.limelight.ligase.feature.input.layout.v2.domain.SafeAreaPolicy
import com.limelight.ligase.feature.input.layout.v2.domain.VerticalAnchor
import com.limelight.ligase.feature.input.layout.v2.domain.SoftKeyboardPayload
import com.limelight.ligase.feature.input.layout.v2.domain.TouchLayoutV2Document
import com.limelight.ligase.feature.input.layout.v2.domain.TouchLayoutV2Element
import com.limelight.ligase.feature.input.layout.v2.domain.TouchLayoutV2Variant
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutLocalAvailability
import com.limelight.ligase.feature.input.layout.v2.data.LayoutCatalogV2LocalRepository
import com.limelight.ligase.feature.input.layout.v2.data.LayoutCatalogV2PackagedSource
import com.limelight.ligase.feature.input.layout.v2.data.LayoutPreferredVariantV2Repository
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2DraftIdentity
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2DraftOrigin
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditableProperties
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorDraft
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorElement
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2ElementCapability
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorIssue
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorPhase
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2RecoveryProtection
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Codec
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Encoder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutV2EditorWorkspaceViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = cleanup()

    @After
    fun tearDown() = cleanup()

    @Test
    fun blankCreationPolicyOwnsEveryNonNameDefault() {
        val ready = LayoutV2BlankCreationPolicy().create("  My layout  ")
            as LayoutV2BlankCreationDecision.Ready
        val request = ready.request

        assertEquals("My layout", request.displayName)
        assertEquals(IntSize(1920, 1080), request.canvas)
        assertEquals(listOf(DeviceClass.PHONE), request.deviceClasses)
        assertEquals(listOf(LayoutOrientation.LANDSCAPE), request.orientations)
        assertEquals(AspectRatio(16, 9), request.recommendation.preferredAspectRatio)
        assertEquals(AspectRatio(4, 3), request.recommendation.minAspectRatio)
        assertEquals(AspectRatio(32, 9), request.recommendation.maxAspectRatio)
        assertEquals(320, request.recommendation.minShortestSideDp)
        assertEquals(48, request.recommendation.minTouchTargetDp)
        assertEquals(SafeAreaPolicy.VIDEO_CONTENT, request.recommendation.safeAreaPolicy)
        assertNull(request.recommendation.referenceDensityDpi)
        assertEquals(IntSize(1920, 1080), request.recommendation.referenceResolution)
    }

    @Test
    fun blankNameUsesStableFallbackAndInvalidNameIsNotTruncated() {
        val fallback = LayoutV2BlankCreationPolicy().create(" ")
            as LayoutV2BlankCreationDecision.Ready
        assertEquals("Untitled layout", fallback.request.displayName)
        assertEquals(
            LayoutV2BlankCreationDecision.InvalidDisplayName,
            LayoutV2BlankCreationPolicy().create("x".repeat(81)),
        )
    }

    @Test
    fun elementCreationPolicyProvidesClosedDefaultsForFiveKinds() {
        val policy = LayoutV2ElementCreationPolicy()
        val draft = blankDraft()
        val expectedProperties = mapOf(
            ControlKind.KEYBOARD to LayoutV2EditableProperties.Keyboard::class,
            ControlKind.MOUSE to LayoutV2EditableProperties.Mouse::class,
            ControlKind.ANALOG to LayoutV2EditableProperties.Analog::class,
            ControlKind.DPAD to LayoutV2EditableProperties.Dpad::class,
            ControlKind.SOFT_KEYBOARD to LayoutV2EditableProperties.SoftKeyboard::class,
        )

        expectedProperties.forEach { (kind, propertiesClass) ->
            val ready = policy.create(draft, kind) as LayoutV2ElementCreationDecision.Ready
            assertEquals(IntRect(0, 0, ready.rect.width, ready.rect.height), ready.rect)
            assertEquals(propertiesClass, ready.properties::class)
        }
        assertEquals(
            LayoutV2ElementCreationDecision.UnsupportedKind,
            policy.create(draft, ControlKind.RADIAL),
        )
    }

    @Test
    fun elementCreationFailsClosedWhenNoNonOverlappingBoundsExist() {
        val occupied = blankDraft().copy(
            canvas = IntSize(160, 160),
            elements = listOf(
                LayoutV2EditorElement(
                    elementId = "00000000-0000-0000-0000-000000000003",
                    kind = ControlKind.KEYBOARD,
                    rect = IntRect(0, 0, 160, 160),
                    horizontalAnchor = HorizontalAnchor.LEFT,
                    verticalAnchor = VerticalAnchor.TOP,
                    zOrder = 0,
                    enabled = true,
                    hidden = false,
                    opacityPermille = 1000,
                    editableProperties = null,
                    inspectOnlySummary = null,
                    capabilities = setOf(LayoutV2ElementCapability.SELECT),
                ),
            ),
        )

        assertEquals(
            LayoutV2ElementCreationDecision.NoSafePlacement,
            LayoutV2ElementCreationPolicy().create(occupied, ControlKind.KEYBOARD),
        )
    }

    @Test
    fun blankDefaultsAndCreatedSoftKeyboardEncodeAsStrictDraft() {
        val request = (
            LayoutV2BlankCreationPolicy().create(null) as LayoutV2BlankCreationDecision.Ready
            ).request
        val document = TouchLayoutV2Document(
            layoutId = "00000000-0000-0000-0000-000000000001",
            revision = 1,
            displayName = request.displayName,
            extensions = emptyMap(),
            variants = listOf(
                TouchLayoutV2Variant(
                    variantId = "00000000-0000-0000-0000-000000000002",
                    deviceClasses = request.deviceClasses,
                    orientations = request.orientations,
                    recommendation = request.recommendation,
                    canvas = request.canvas,
                    elements = listOf(
                        TouchLayoutV2Element(
                            elementId = "00000000-0000-0000-0000-000000000003",
                            kind = ControlKind.SOFT_KEYBOARD,
                            rect = IntRect(0, 0, 240, 120),
                            horizontalAnchor = HorizontalAnchor.LEFT,
                            verticalAnchor = VerticalAnchor.TOP,
                            zOrder = 0,
                            enabled = true,
                            hidden = false,
                            opacityPermille = 1000,
                            payload = SoftKeyboardPayload,
                            sourceReference = null,
                        ),
                    ),
                ),
            ),
            contentHash = "",
        )

        assertEquals(
            1,
            TouchLayoutV2Codec.decodeDraft(TouchLayoutV2Encoder.encode(document))
                .variants.single().elements.size,
        )
    }

    @Test
    fun ownerPublishesActionsAndStopFlushesRecoveryState() {
        val owner = LayoutV2EditorWorkspaceViewModel(application)
        owner.createBlank("Workspace")

        assertEquals(LayoutV2EditorPhase.EDITING, owner.state.value.editor.phase)
        assertEquals(
            LayoutV2WorkspaceActionCode.APPLIED,
            owner.state.value.lastAction?.code,
        )
        owner.onStop()
        assertEquals(
            LayoutV2RecoveryProtection.SAVED,
            owner.state.value.editor.recoveryProtection,
        )
        assertEquals(1, owner.state.value.editor.recoverableDrafts.size)

        owner.createBlank("x".repeat(81))
        assertEquals(LayoutV2EditorIssue.INVALID_PAYLOAD, owner.state.value.lastAction?.issue)
        assertEquals("Workspace", owner.state.value.editor.draft?.displayName)
    }

    @Test
    fun saveRegistersAllCommittedGenerationsAndRestartRehydratesRegistration() {
        val first = LayoutV2EditorWorkspaceViewModel(application)
        var firstRegistration = emptyList<LayoutCatalogV2RegisteredRecord>()
        val firstRegistry = registry()
        first.attachCatalogRegistration {
            firstRegistration = it
            firstRegistry.refresh(registeredRecords = it)
            true
        }
        assertTrue(firstRegistration.isEmpty())

        first.createBlank("Persisted")
        first.addElement(ControlKind.SOFT_KEYBOARD)
        assertEquals(
            first.state.value.lastAction.toString(),
            LayoutV2WorkspaceActionCode.APPLIED,
            first.state.value.lastAction?.code,
        )
        first.save()
        assertEquals(
            first.state.value.lastAction.toString(),
            LayoutV2WorkspaceActionCode.SAVED,
            first.state.value.lastAction?.code,
        )
        assertEquals(1, firstRegistration.size)
        val savedId = firstRegistration.single().descriptor.layoutId
        assertNotNull(savedId)
        assertTrue(firstRegistration.single().committedArtifact?.isNotEmpty() == true)
        assertEquals(
            LayoutLocalAvailability.READY,
            firstRegistry.state.items.single().local.availability,
        )
        assertTrue(firstRegistry.state.items.single().contentVerified)

        val restarted = LayoutV2EditorWorkspaceViewModel(application)
        var restored = emptyList<LayoutCatalogV2RegisteredRecord>()
        val restartedRegistry = registry()
        restarted.attachCatalogRegistration {
            restored = it
            restartedRegistry.refresh(registeredRecords = it)
            true
        }
        assertEquals(firstRegistration, restored)
        assertEquals(
            LayoutLocalAvailability.READY,
            restartedRegistry.state.items.single().local.availability,
        )
        assertTrue(restartedRegistry.state.items.single().contentVerified)
        assertFalse(restarted.state.value.editor.recoverableDrafts.any {
            it.draftId == savedId
        })
    }

    @Test
    fun unavailablePackagedSourceIsTypedAndDoesNotRegister() {
        val owner = LayoutV2EditorWorkspaceViewModel(application)
        var calls = 0
        owner.attachCatalogRegistration {
            calls++
            true
        }
        owner.createFromPackaged(
            "00000000-0000-0000-0000-000000000001",
            1,
            "00000000-0000-0000-0000-000000000002",
        )

        assertEquals(1, calls)
        assertEquals(LayoutV2EditorIssue.SOURCE_NOT_READY, owner.state.value.lastAction?.issue)
        assertNull(owner.state.value.editor.draft)
    }

    @Test
    fun staleActivityDetachCannotRemoveNewCatalogAttachment() {
        val owner = LayoutV2EditorWorkspaceViewModel(application)
        var oldCalls = 0
        var currentCalls = 0
        val oldAttachment = owner.attachCatalogRegistration {
            oldCalls++
            true
        }
        owner.attachCatalogRegistration {
            currentCalls++
            true
        }

        owner.detachCatalogRegistration(oldAttachment)
        assertTrue(owner.refreshCatalog())
        assertEquals(1, oldCalls)
        assertEquals(2, currentCalls)
    }

    @Test
    fun refreshAfterEditorSaveRemovesRecoveryAndReenumeratesCommittedCatalog() {
        val workspace = LayoutV2EditorWorkspaceViewModel(application)
        var records = emptyList<LayoutCatalogV2RegisteredRecord>()
        var refreshes = 0
        workspace.attachCatalogRegistration {
            records = it
            refreshes++
            true
        }
        workspace.createBlank("Editor save")
        workspace.addElement(ControlKind.SOFT_KEYBOARD)
        val draftId = workspace.state.value.editor.draft!!.identity.layoutId
        assertTrue(
            workspace.checkpointAndRelease(draftId) is
                com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorHandoffResult.LaunchReady,
        )

        val editor = LayoutV2EditorActivityViewModel(
            application,
            draftId,
            LayoutV2EditorProcessLeases.registry,
        )
        assertEquals(
            com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorExitCode.SAVED,
            editor.saveAndFinish().code,
        )
        assertTrue(workspace.refreshAfterEditorReturn())

        assertNull(workspace.state.value.editor.draft)
        assertTrue(workspace.state.value.editor.recoverableDrafts.isEmpty())
        assertEquals(1, records.size)
        assertEquals(draftId, records.single().descriptor.layoutId)
        assertEquals(2, refreshes)
    }

    @Test
    fun refreshAfterEditorDiscardRemovesStaleSummaryWithoutOpeningDraft() {
        val workspace = LayoutV2EditorWorkspaceViewModel(application)
        workspace.attachCatalogRegistration { true }
        workspace.createBlank("Editor discard")
        val draftId = workspace.state.value.editor.draft!!.identity.layoutId
        assertTrue(
            workspace.checkpointAndRelease(draftId) is
                com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorHandoffResult.LaunchReady,
        )
        assertEquals(1, workspace.state.value.editor.recoverableDrafts.size)

        val editor = LayoutV2EditorActivityViewModel(
            application,
            draftId,
            LayoutV2EditorProcessLeases.registry,
        )
        assertEquals(
            com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorExitCode.DISCARDED,
            editor.discardAndFinish().code,
        )
        assertTrue(workspace.refreshAfterEditorReturn())

        assertNull(workspace.state.value.editor.draft)
        assertTrue(workspace.state.value.editor.recoverableDrafts.isEmpty())
    }

    private fun cleanup() {
        listOf(
            "ligase-touch-layout-v2-drafts",
            "ligase-touch-layout-v2-draft-quarantine",
            "ligase-touch-layout-v2-generations",
        ).forEach { File(application.filesDir, it).deleteRecursively() }
    }

    private fun registry() = LayoutCatalogV2SourceRegistry(
        LayoutCatalogV2Catalog(
            LayoutCatalogV2LocalRepository(application),
            LayoutPreferredVariantV2Repository(application),
        ),
        LayoutCatalogV2PackagedSource(application),
    )

    private fun blankDraft(): LayoutV2EditorDraft {
        val request = (
            LayoutV2BlankCreationPolicy().create(null) as LayoutV2BlankCreationDecision.Ready
            ).request
        return LayoutV2EditorDraft(
            identity = LayoutV2DraftIdentity(
                "00000000-0000-0000-0000-000000000001",
                1,
                "00000000-0000-0000-0000-000000000002",
                LayoutV2DraftOrigin.BLANK,
            ),
            displayName = request.displayName,
            canvas = request.canvas,
            deviceClasses = request.deviceClasses,
            orientations = request.orientations,
            recommendation = request.recommendation,
            elements = emptyList(),
        )
    }
}
