package com.limelight.ligase.feature.input.layout.v3.application

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3DraftJournal
import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3GenerationRepository
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.*
import com.limelight.ligase.layout.LayoutContractV1Validator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class LayoutV3EditorActivityViewModel internal constructor(
    application: Application,
    private var draftId: String,
    private val leaseRegistry: LayoutV3DraftLeaseRegistry,
    private var launchMode: LayoutV3EditorLaunchMode = LayoutV3EditorLaunchMode.EXISTING_V3,
    private val displayName: String? = null,
    private val savedStateHandle: SavedStateHandle? = null,
) : AndroidViewModel(application) {
    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        application,
        savedStateHandle[EXTRA_LAYOUT_V3_DRAFT_ID] ?: "",
        LayoutV3EditorProcessLeases.registry,
        savedStateHandle.get<String>(EXTRA_LAYOUT_V3_MODE)
            ?.let(LayoutV3EditorLaunchMode::valueOf)
            ?: if (savedStateHandle.get<String>(EXTRA_LAYOUT_V3_DRAFT_ID).isNullOrEmpty()) {
                LayoutV3EditorLaunchMode.NEW_V3
            } else {
                LayoutV3EditorLaunchMode.EXISTING_V3
            },
        savedStateHandle[EXTRA_LAYOUT_V3_DISPLAY_NAME],
        savedStateHandle,
    )

    private val generations = LayoutV3GenerationRepository(application)
    private val elementCreationPolicy = LayoutV3ElementCreationPolicy()
    private val blankCreationPolicy = LayoutV3BlankCreationPolicy()
    private val mutableState = MutableStateFlow(LayoutV3EditorWorkspaceUiState())
    val state: StateFlow<LayoutV3EditorWorkspaceUiState> = mutableState.asStateFlow()
    private val mutableHandoff = MutableStateFlow<LayoutV3EditorHandoffResult>(
        if (launchMode == LayoutV3EditorLaunchMode.NEW_V3) {
            LayoutV3EditorHandoffResult.AwaitingViewport
        } else {
            LayoutV3EditorHandoffResult.Rejected(LayoutV3EditorHandoffIssue.MISSING)
        },
    )
    val handoff: StateFlow<LayoutV3EditorHandoffResult> = mutableHandoff.asStateFlow()
    private val ownerToken = UUID.randomUUID().toString()
    private var lease: LayoutV3DraftLease? = null
    private var closed = false

    private val session = LayoutV3EditorSession(
        LayoutV3DraftJournal(application),
        generations,
        registerCommitted = { record ->
            generations.read(record.descriptor.layoutId, record.descriptor.revision)?.let {
                it.descriptor == record.descriptor
            } == true
        },
        onStateChanged = { editor ->
            mutableState.value = mutableState.value.copy(editor = editor)
        },
    )

    init {
        if (launchMode == LayoutV3EditorLaunchMode.EXISTING_V3) resumeExclusive()
    }

    /**
     * NEW_V3 creation is owned by this Activity-scoped owner and occurs only
     * after the full immersive overlay reports stable bounds.
     */
    fun initializeNewV3(viewport: EditorTargetViewport): LayoutV3EditorHandoffResult {
        if (
            closed ||
            launchMode != LayoutV3EditorLaunchMode.NEW_V3 ||
            mutableHandoff.value != LayoutV3EditorHandoffResult.AwaitingViewport
        ) {
            return LayoutV3EditorHandoffResult.Rejected(LayoutV3EditorHandoffIssue.ALREADY_OWNED)
        }
        val request = when (val decision = blankCreationPolicy.create(displayName, viewport)) {
            is LayoutV3BlankCreationDecision.Ready -> decision.request
            LayoutV3BlankCreationDecision.InvalidDisplayName,
            LayoutV3BlankCreationDecision.InvalidViewport -> {
                return LayoutV3EditorHandoffResult.Rejected(
                    LayoutV3EditorHandoffIssue.CHECKPOINT_FAILED,
                ).also { mutableHandoff.value = it }
            }
        }
        val created = session.createBlank(request)
        if (created !is LayoutV3EditResult.Applied) {
            return LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.CHECKPOINT_FAILED,
            ).also { mutableHandoff.value = it }
        }
        val createdDraftId = session.state.draft?.identity?.layoutId
            ?: return LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.CHECKPOINT_FAILED,
            ).also { mutableHandoff.value = it }
        val acquired = leaseRegistry.acquire(createdDraftId, ownerToken)
        if (acquired == null || session.flushJournal() !=
            com.limelight.ligase.feature.input.layout.v3.data.LayoutV3JournalWriteResult.SAVED
        ) {
            acquired?.let(leaseRegistry::release)
            session.discardDraft()
            return LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.CHECKPOINT_FAILED,
            ).also { mutableHandoff.value = it }
        }
        lease = acquired
        draftId = createdDraftId
        launchMode = LayoutV3EditorLaunchMode.EXISTING_V3
        savedStateHandle?.set(EXTRA_LAYOUT_V3_DRAFT_ID, createdDraftId)
        savedStateHandle?.set(EXTRA_LAYOUT_V3_MODE, LayoutV3EditorLaunchMode.EXISTING_V3.name)
        val ready = LayoutV3EditorHandoffResult.LaunchReady(createdDraftId)
        mutableState.value = mutableState.value.copy(editor = session.state)
        mutableHandoff.value = ready
        return ready
    }

    fun selectElement(elementId: String) = publish(session.selectElement(elementId))
    fun moveElement(elementId: String, x: Int, y: Int) =
        publish(session.moveElement(elementId, x, y))
    fun resizeElement(
        elementId: String,
        width: Int,
        height: Int,
    ): LayoutV3EditResult = session.resizeElement(elementId, width, height).also(::publish)
    fun beginGesture(elementId: String): LayoutV3GestureStartResult =
        session.beginGesture(elementId)
    fun commitMove(token: LayoutV3GestureCommitToken, x: Int, y: Int): LayoutV3EditResult =
        session.commitMove(token, x, y).also(::publish)
    fun commitResize(
        token: LayoutV3GestureCommitToken,
        width: Int,
        height: Int,
    ): LayoutV3EditResult = session.commitResize(token, width, height).also(::publish)
    fun cancelGesture(token: LayoutV3GestureCommitToken): LayoutV3EditResult =
        session.cancelGesture(token).also(::publish)

    fun commitPixelMove(
        token: LayoutV3GestureCommitToken,
        fullOverlay: IntRect,
        previewRect: IntRect,
    ): LayoutV3EditResult {
        val canonical = inverseRect(token, fullOverlay, previewRect)
            ?: return rejectedGestureReadback(token)
        return commitMove(token, canonical.x, canonical.y)
    }

    fun commitPixelResize(
        token: LayoutV3GestureCommitToken,
        fullOverlay: IntRect,
        previewRect: IntRect,
    ): LayoutV3EditResult {
        val canonical = inverseRect(token, fullOverlay, previewRect)
            ?: return rejectedGestureReadback(token)
        return session.commitResolvedRect(token, canonical).also(::publish)
    }
    fun nudgeElement(elementId: String, deltaX: Int, deltaY: Int) =
        publish(session.nudgeElement(elementId, deltaX, deltaY))
    fun rebaseElement(elementId: String) =
        publish(session.rebaseElement(elementId))
    fun setAnchors(
        elementId: String,
        horizontal: HorizontalAnchor,
        vertical: VerticalAnchor,
    ) = publish(session.setAnchors(elementId, horizontal, vertical))
    fun setZOrder(elementId: String, zOrder: Int) =
        publish(session.setZOrder(elementId, zOrder))
    fun setLayoutOpacityPermille(opacityPermille: Int) =
        publish(session.setLayoutOpacityPermille(opacityPermille))

    fun replaceComboChord(elementId: String, keys: List<InputCode>) =
        publish(session.replaceComboChord(elementId, keys))

    fun addRadialAction(elementId: String, label: String?, keys: List<InputCode>) =
        publishRadial(session.addRadialAction(elementId, label, keys))

    fun removeRadialAction(elementId: String, actionId: String) =
        publishRadial(session.removeRadialAction(elementId, actionId))

    fun reorderRadialAction(elementId: String, actionId: String, targetOrder: Int) =
        publishRadial(session.reorderRadialAction(elementId, actionId, targetOrder))

    fun replaceRadialActionLabel(elementId: String, actionId: String, label: String?) =
        publishRadial(session.replaceRadialActionLabel(elementId, actionId, label))

    fun replaceRadialActionChord(elementId: String, actionId: String, keys: List<InputCode>) =
        publishRadial(session.replaceRadialActionChord(elementId, actionId, keys))
    fun deleteElement(elementId: String) = publish(session.deleteElement(elementId))
    fun updateProperties(elementId: String, properties: LayoutV3EditableProperties) =
        publish(session.updateProperties(elementId, properties))

    private fun publishRadial(result: LayoutV3RadialEditResult): LayoutV3RadialEditResult {
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = when (result) {
                is LayoutV3RadialEditResult.Applied ->
                    LayoutV3WorkspaceActionResult(LayoutV3WorkspaceActionCode.APPLIED)
                is LayoutV3RadialEditResult.Rejected ->
                    LayoutV3WorkspaceActionResult(LayoutV3WorkspaceActionCode.REJECTED, result.issue)
            },
        )
        return result
    }
    fun addKeyboardKeys(keys: Set<InputCode>) =
        publish(session.addKeyboardKeys(keys))
    fun addElement(kind: ControlKind) {
        val draft = session.state.draft
        if (draft == null) {
            publishRejected(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
            return
        }
        when (val decision = elementCreationPolicy.create(draft, kind)) {
            is LayoutV3ElementCreationDecision.Ready ->
                publish(session.addElement(kind, decision.rect, decision.properties))
            LayoutV3ElementCreationDecision.UnsupportedKind ->
                publishRejected(LayoutV3EditorIssue.READ_ONLY_KIND)
            LayoutV3ElementCreationDecision.NoSafePlacement ->
                publishRejected(LayoutV3EditorIssue.LIMIT_EXCEEDED)
        }
    }

    fun addComboElement(
        keys: List<InputCode>,
        label: String? = null,
        description: String? = null,
    ): LayoutV3ElementCreateResult {
        val placement = configuredPlacement(ControlKind.COMBO)
        val rect = placement.first ?: return publishCreateRejected(checkNotNull(placement.second))
        return publishCreated(session.addComboElement(rect, keys, label, description))
    }

    fun addRadialElement(
        actions: List<LayoutV3NewRadialActionRequest>,
        label: String? = null,
    ): LayoutV3ElementCreateResult {
        val placement = configuredPlacement(ControlKind.RADIAL)
        val rect = placement.first ?: return publishCreateRejected(checkNotNull(placement.second))
        return publishCreated(session.addRadialElement(rect, actions, label))
    }

    private fun configuredPlacement(
        kind: ControlKind,
    ): Pair<IntRect?, LayoutV3EditorIssue?> {
        val draft = session.state.draft
        if (draft == null) {
            return null to LayoutV3EditorIssue.NO_ACTIVE_DRAFT
        }
        return when (val decision = elementCreationPolicy.place(draft, kind)) {
            is LayoutV3ElementPlacementDecision.Ready -> decision.rect to null
            LayoutV3ElementPlacementDecision.UnsupportedKind ->
                null to LayoutV3EditorIssue.READ_ONLY_KIND
            LayoutV3ElementPlacementDecision.NoSafePlacement ->
                null to LayoutV3EditorIssue.LIMIT_EXCEEDED
        }
    }

    private fun publishCreated(result: LayoutV3ElementCreateResult): LayoutV3ElementCreateResult {
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = when (result) {
                is LayoutV3ElementCreateResult.Created ->
                    LayoutV3WorkspaceActionResult(LayoutV3WorkspaceActionCode.APPLIED)
                is LayoutV3ElementCreateResult.Rejected ->
                    LayoutV3WorkspaceActionResult(LayoutV3WorkspaceActionCode.REJECTED, result.issue)
            },
        )
        return result
    }

    private fun publishCreateRejected(issue: LayoutV3EditorIssue): LayoutV3ElementCreateResult {
        publishRejected(issue)
        return LayoutV3ElementCreateResult.Rejected(issue)
    }

    fun validate() {
        val valid = session.validateDraft()
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = LayoutV3WorkspaceActionResult(
                if (valid) LayoutV3WorkspaceActionCode.VALID
                else LayoutV3WorkspaceActionCode.REJECTED,
                if (valid) null else LayoutV3EditorIssue.VALIDATION_FAILED,
            ),
        )
    }

    fun onStop() {
        if (!closed) {
            session.onStop()
            mutableState.value = mutableState.value.copy(editor = session.state)
        }
    }

    fun keepDraftAndFinish(): LayoutV3EditorExitResult {
        if (closed) return LayoutV3EditorExitResult(
            LayoutV3EditorExitCode.FAILED,
            LayoutV3EditorIssue.STALE_SOURCE_GENERATION,
        )
        val result = session.checkpointAndRelease(draftId)
        return if (result is LayoutV3EditorHandoffResult.LaunchReady) {
            closeOwner()
            LayoutV3EditorExitResult(LayoutV3EditorExitCode.LEFT_RECOVERABLE)
        } else {
            LayoutV3EditorExitResult(
                LayoutV3EditorExitCode.FAILED,
                LayoutV3EditorIssue.JOURNAL_WRITE_FAILED,
            )
        }
    }

    fun saveAndFinish(): LayoutV3EditorExitResult =
        when (val result = session.saveDraft()) {
            is LayoutV3SaveResult.Saved -> {
                session.releaseWithoutCheckpoint()
                closeOwner()
                LayoutV3EditorExitResult(LayoutV3EditorExitCode.SAVED)
            }
            is LayoutV3SaveResult.Rejected ->
                LayoutV3EditorExitResult(LayoutV3EditorExitCode.FAILED, result.issue)
        }

    fun discardAndFinish(): LayoutV3EditorExitResult =
        if (session.discardDraft()) {
            closeOwner()
            LayoutV3EditorExitResult(LayoutV3EditorExitCode.DISCARDED)
        } else {
            LayoutV3EditorExitResult(
                LayoutV3EditorExitCode.FAILED,
                LayoutV3EditorIssue.JOURNAL_WRITE_FAILED,
            )
        }

    override fun onCleared() {
        closeOwner()
    }

    internal fun closeForTest() = closeOwner()

    private fun resumeExclusive() {
        if (LayoutContractV1Validator.normalizeUuid(draftId) != draftId) {
            mutableHandoff.value = LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.INVALID_DRAFT_ID,
            )
            return
        }
        val acquired = leaseRegistry.acquire(draftId, ownerToken)
        if (acquired == null) {
            mutableHandoff.value = LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.ALREADY_OWNED,
            )
            return
        }
        lease = acquired
        when (val result = session.resumeRecoverableDraft(draftId)) {
            LayoutV3EditResult.Applied -> {
                mutableState.value = mutableState.value.copy(editor = session.state)
                mutableHandoff.value = LayoutV3EditorHandoffResult.LaunchReady(draftId)
            }
            is LayoutV3EditResult.Rejected -> {
                leaseRegistry.release(acquired)
                lease = null
                mutableHandoff.value = LayoutV3EditorHandoffResult.Rejected(
                    if (result.issue == LayoutV3EditorIssue.VALIDATION_FAILED) {
                        LayoutV3EditorHandoffIssue.QUARANTINED
                    } else {
                        LayoutV3EditorHandoffIssue.MISSING
                    },
                )
            }
        }
    }

    private fun publish(result: LayoutV3EditResult) {
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = when (result) {
                LayoutV3EditResult.Applied ->
                    LayoutV3WorkspaceActionResult(LayoutV3WorkspaceActionCode.APPLIED)
                is LayoutV3EditResult.Rejected -> LayoutV3WorkspaceActionResult(
                    LayoutV3WorkspaceActionCode.REJECTED,
                    result.issue,
                    result.elementId,
                )
            },
        )
    }

    private fun publishRejected(issue: LayoutV3EditorIssue) {
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = LayoutV3WorkspaceActionResult(
                LayoutV3WorkspaceActionCode.REJECTED,
                issue,
            ),
        )
    }

    private fun inverseRect(
        token: LayoutV3GestureCommitToken,
        fullOverlay: IntRect,
        previewRect: IntRect,
    ): IntRect? {
        val draft = session.state.draft ?: return null
        val element = draft.elements.firstOrNull { it.elementId == token.elementId } ?: return null
        return runCatching {
            LayoutV3Geometry.unmapResolvedRect(
                draft.canvas,
                element.anchorX,
                element.anchorY,
                fullOverlay,
                previewRect,
            )
        }.getOrNull()
    }

    private fun rejectedGestureReadback(token: LayoutV3GestureCommitToken): LayoutV3EditResult {
        session.cancelGesture(token)
        return LayoutV3EditResult.Rejected(
            LayoutV3EditorIssue.INVALID_RECT,
            token.elementId,
        ).also(::publish)
    }

    private fun closeOwner() {
        if (closed) return
        closed = true
        session.close()
        lease?.let(leaseRegistry::release)
        lease = null
    }

    companion object {
        const val EXTRA_LAYOUT_V3_DRAFT_ID =
            "com.limelight.ligase.extra.LAYOUT_V3_DRAFT_ID"
        const val EXTRA_LAYOUT_V3_MODE =
            "com.limelight.ligase.extra.LAYOUT_V3_MODE"
        const val EXTRA_LAYOUT_V3_DISPLAY_NAME =
            "com.limelight.ligase.extra.LAYOUT_V3_DISPLAY_NAME"
    }
}
