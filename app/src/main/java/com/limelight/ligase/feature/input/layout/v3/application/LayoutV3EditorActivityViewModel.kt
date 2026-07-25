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
    private val draftId: String,
    private val leaseRegistry: LayoutV3DraftLeaseRegistry,
) : AndroidViewModel(application) {
    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        application,
        savedStateHandle[EXTRA_LAYOUT_V3_DRAFT_ID] ?: "",
        LayoutV3EditorProcessLeases.registry,
    )

    private val generations = LayoutV3GenerationRepository(application)
    private val elementCreationPolicy = LayoutV3ElementCreationPolicy()
    private val mutableState = MutableStateFlow(LayoutV3EditorWorkspaceUiState())
    val state: StateFlow<LayoutV3EditorWorkspaceUiState> = mutableState.asStateFlow()
    private val mutableHandoff = MutableStateFlow<LayoutV3EditorHandoffResult>(
        LayoutV3EditorHandoffResult.Rejected(LayoutV3EditorHandoffIssue.MISSING),
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
        resumeExclusive()
    }

    fun selectElement(elementId: String) = publish(session.selectElement(elementId))
    fun moveElement(elementId: String, x: Int, y: Int) =
        publish(session.moveElement(elementId, x, y))
    fun resizeElement(elementId: String, width: Int, height: Int) =
        publish(session.resizeElement(elementId, width, height))
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
    fun deleteElement(elementId: String) = publish(session.deleteElement(elementId))
    fun updateProperties(elementId: String, properties: LayoutV3EditableProperties) =
        publish(session.updateProperties(elementId, properties))
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
    }
}
