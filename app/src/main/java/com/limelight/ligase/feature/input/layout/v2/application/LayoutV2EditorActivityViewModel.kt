package com.limelight.ligase.feature.input.layout.v2.application

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.limelight.ligase.feature.input.layout.v2.data.LayoutV2DraftJournal
import com.limelight.ligase.feature.input.layout.v2.data.LayoutV2GenerationRepository
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.editor.*
import com.limelight.ligase.layout.LayoutContractV1Validator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class LayoutV2EditorActivityViewModel internal constructor(
    application: Application,
    private val draftId: String,
    private val leaseRegistry: LayoutV2DraftLeaseRegistry,
) : AndroidViewModel(application) {
    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        application,
        savedStateHandle[EXTRA_LAYOUT_V2_DRAFT_ID] ?: "",
        LayoutV2EditorProcessLeases.registry,
    )

    private val generations = LayoutV2GenerationRepository(application)
    private val elementCreationPolicy = LayoutV2ElementCreationPolicy()
    private val mutableState = MutableStateFlow(LayoutV2EditorWorkspaceUiState())
    val state: StateFlow<LayoutV2EditorWorkspaceUiState> = mutableState.asStateFlow()
    private val mutableHandoff = MutableStateFlow<LayoutV2EditorHandoffResult>(
        LayoutV2EditorHandoffResult.Rejected(LayoutV2EditorHandoffIssue.MISSING),
    )
    val handoff: StateFlow<LayoutV2EditorHandoffResult> = mutableHandoff.asStateFlow()
    private val ownerToken = UUID.randomUUID().toString()
    private var lease: LayoutV2DraftLease? = null
    private var closed = false

    private val session = LayoutV2EditorSession(
        LayoutV2DraftJournal(application),
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
    fun setAnchors(
        elementId: String,
        horizontal: HorizontalAnchor,
        vertical: VerticalAnchor,
    ) = publish(session.setAnchors(elementId, horizontal, vertical))
    fun setZOrder(elementId: String, zOrder: Int) =
        publish(session.setZOrder(elementId, zOrder))
    fun deleteElement(elementId: String) = publish(session.deleteElement(elementId))
    fun updateProperties(elementId: String, properties: LayoutV2EditableProperties) =
        publish(session.updateProperties(elementId, properties))
    fun addElement(kind: ControlKind) {
        val draft = session.state.draft
        if (draft == null) {
            publishRejected(LayoutV2EditorIssue.NO_ACTIVE_DRAFT)
            return
        }
        when (val decision = elementCreationPolicy.create(draft, kind)) {
            is LayoutV2ElementCreationDecision.Ready ->
                publish(session.addElement(kind, decision.rect, decision.properties))
            LayoutV2ElementCreationDecision.UnsupportedKind ->
                publishRejected(LayoutV2EditorIssue.READ_ONLY_KIND)
            LayoutV2ElementCreationDecision.NoSafePlacement ->
                publishRejected(LayoutV2EditorIssue.COLLISION)
        }
    }

    fun validate() {
        val valid = session.validateDraft()
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = LayoutV2WorkspaceActionResult(
                if (valid) LayoutV2WorkspaceActionCode.VALID
                else LayoutV2WorkspaceActionCode.REJECTED,
                if (valid) null else LayoutV2EditorIssue.VALIDATION_FAILED,
            ),
        )
    }

    fun onStop() {
        if (!closed) {
            session.onStop()
            mutableState.value = mutableState.value.copy(editor = session.state)
        }
    }

    fun keepDraftAndFinish(): LayoutV2EditorExitResult {
        if (closed) return LayoutV2EditorExitResult(
            LayoutV2EditorExitCode.FAILED,
            LayoutV2EditorIssue.STALE_SOURCE_GENERATION,
        )
        val result = session.checkpointAndRelease(draftId)
        return if (result is LayoutV2EditorHandoffResult.LaunchReady) {
            closeOwner()
            LayoutV2EditorExitResult(LayoutV2EditorExitCode.LEFT_RECOVERABLE)
        } else {
            LayoutV2EditorExitResult(
                LayoutV2EditorExitCode.FAILED,
                LayoutV2EditorIssue.JOURNAL_WRITE_FAILED,
            )
        }
    }

    fun saveAndFinish(): LayoutV2EditorExitResult =
        when (val result = session.saveDraft()) {
            is LayoutV2SaveResult.Saved -> {
                session.releaseWithoutCheckpoint()
                closeOwner()
                LayoutV2EditorExitResult(LayoutV2EditorExitCode.SAVED)
            }
            is LayoutV2SaveResult.Rejected ->
                LayoutV2EditorExitResult(LayoutV2EditorExitCode.FAILED, result.issue)
        }

    fun discardAndFinish(): LayoutV2EditorExitResult =
        if (session.discardDraft()) {
            closeOwner()
            LayoutV2EditorExitResult(LayoutV2EditorExitCode.DISCARDED)
        } else {
            LayoutV2EditorExitResult(
                LayoutV2EditorExitCode.FAILED,
                LayoutV2EditorIssue.JOURNAL_WRITE_FAILED,
            )
        }

    override fun onCleared() {
        closeOwner()
    }

    internal fun closeForTest() = closeOwner()

    private fun resumeExclusive() {
        if (LayoutContractV1Validator.normalizeUuid(draftId) != draftId) {
            mutableHandoff.value = LayoutV2EditorHandoffResult.Rejected(
                LayoutV2EditorHandoffIssue.INVALID_DRAFT_ID,
            )
            return
        }
        val acquired = leaseRegistry.acquire(draftId, ownerToken)
        if (acquired == null) {
            mutableHandoff.value = LayoutV2EditorHandoffResult.Rejected(
                LayoutV2EditorHandoffIssue.ALREADY_OWNED,
            )
            return
        }
        lease = acquired
        when (val result = session.resumeRecoverableDraft(draftId)) {
            LayoutV2EditResult.Applied -> {
                mutableState.value = mutableState.value.copy(editor = session.state)
                mutableHandoff.value = LayoutV2EditorHandoffResult.LaunchReady(draftId)
            }
            is LayoutV2EditResult.Rejected -> {
                leaseRegistry.release(acquired)
                lease = null
                mutableHandoff.value = LayoutV2EditorHandoffResult.Rejected(
                    if (result.issue == LayoutV2EditorIssue.VALIDATION_FAILED) {
                        LayoutV2EditorHandoffIssue.QUARANTINED
                    } else {
                        LayoutV2EditorHandoffIssue.MISSING
                    },
                )
            }
        }
    }

    private fun publish(result: LayoutV2EditResult) {
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = when (result) {
                LayoutV2EditResult.Applied ->
                    LayoutV2WorkspaceActionResult(LayoutV2WorkspaceActionCode.APPLIED)
                is LayoutV2EditResult.Rejected -> LayoutV2WorkspaceActionResult(
                    LayoutV2WorkspaceActionCode.REJECTED,
                    result.issue,
                    result.elementId,
                )
            },
        )
    }

    private fun publishRejected(issue: LayoutV2EditorIssue) {
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = LayoutV2WorkspaceActionResult(
                LayoutV2WorkspaceActionCode.REJECTED,
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
        const val EXTRA_LAYOUT_V2_DRAFT_ID =
            "com.limelight.ligase.extra.LAYOUT_V2_DRAFT_ID"
    }
}
