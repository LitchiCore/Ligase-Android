package com.limelight.ligase.feature.input.layout.v2.application

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.limelight.ligase.feature.input.layout.v2.data.LayoutV2DraftJournal
import com.limelight.ligase.feature.input.layout.v2.data.LayoutV2GenerationRepository
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutContentVerificationResult
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.editor.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class LayoutV2WorkspaceActionCode {
    APPLIED, REJECTED, SAVED, DISCARDED, RECOVERY_DISCARDED, VALID,
}

data class LayoutV2WorkspaceActionResult(
    val code: LayoutV2WorkspaceActionCode,
    val issue: LayoutV2EditorIssue? = null,
    val targetId: String? = null,
) {
    override fun toString(): String =
        "LayoutV2WorkspaceActionResult(code=$code,issue=$issue,target=redacted)"
}

data class LayoutV2EditorWorkspaceUiState(
    val editor: LayoutV2EditorState = LayoutV2EditorState(),
    val lastAction: LayoutV2WorkspaceActionResult? = null,
)

@JvmInline
value class LayoutV2CatalogRegistrationAttachment internal constructor(
    internal val generation: Long,
)

class LayoutV2EditorWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val generations = LayoutV2GenerationRepository(application)
    private val journal = LayoutV2DraftJournal(application)
    private val blankCreationPolicy = LayoutV2BlankCreationPolicy()
    private val elementCreationPolicy = LayoutV2ElementCreationPolicy()
    private var catalogRegistration:
        ((List<LayoutCatalogV2RegisteredRecord>) -> Boolean)? = null
    private var catalogRegistrationGeneration = 0L
    private val mutableState = MutableStateFlow(LayoutV2EditorWorkspaceUiState())
    val state: StateFlow<LayoutV2EditorWorkspaceUiState> = mutableState.asStateFlow()
    private val leaseRegistry = LayoutV2EditorProcessLeases.registry
    private val leaseOwnerToken = UUID.randomUUID().toString()
    private var draftLease: LayoutV2DraftLease? = null

    private val session = LayoutV2EditorSession(
        journal,
        generations,
        registerCommitted = {
            val callback = catalogRegistration ?: return@LayoutV2EditorSession false
            callback(committedRecords())
        },
        onStateChanged = { editor ->
            mutableState.value = mutableState.value.copy(editor = editor)
        },
    )

    init {
        mutableState.value = LayoutV2EditorWorkspaceUiState(session.state)
    }

    fun attachCatalogRegistration(
        callback: (List<LayoutCatalogV2RegisteredRecord>) -> Boolean,
    ): LayoutV2CatalogRegistrationAttachment {
        val attachment = LayoutV2CatalogRegistrationAttachment(++catalogRegistrationGeneration)
        catalogRegistration = callback
        callback(committedRecords())
        return attachment
    }

    fun detachCatalogRegistration(attachment: LayoutV2CatalogRegistrationAttachment) {
        if (attachment.generation != catalogRegistrationGeneration) return
        catalogRegistration = null
    }

    fun refreshCatalog(): Boolean =
        catalogRegistration?.invoke(committedRecords()) ?: false

    fun createBlank(displayName: String? = null) {
        when (val decision = blankCreationPolicy.create(displayName)) {
            is LayoutV2BlankCreationDecision.Ready ->
                publishActivated(session.createBlank(decision.request))
            LayoutV2BlankCreationDecision.InvalidDisplayName ->
                publishRejected(LayoutV2EditorIssue.INVALID_PAYLOAD, null)
        }
    }

    fun createFromPackaged(layoutId: String, revision: Long, variantId: String) {
        publishRejected(LayoutV2EditorIssue.SOURCE_NOT_READY, layoutId)
    }

    fun createFromLocal(layoutId: String, revision: Long, variantId: String) {
        val generation = generations.read(layoutId, revision)
        if (generation == null) {
            publishRejected(LayoutV2EditorIssue.SOURCE_NOT_READY, layoutId)
            return
        }
        val verified = TouchLayoutV2ContentVerifier.verify(generation.artifact)
        if (verified !is LayoutContentVerificationResult.Verified) {
            publishRejected(LayoutV2EditorIssue.VALIDATION_FAILED, layoutId)
            return
        }
        publishActivated(
            session.createFromLocalCopy(
                LayoutV2CreatorSource(
                    generation.descriptor,
                    verified.content,
                    LayoutV2DraftOrigin.LOCAL_COPY,
                    LayoutLocalAvailability.READY,
                    LayoutWorkspaceState.DRAFT,
                ),
                variantId,
            ),
        )
    }

    fun resumeRecovery(draftId: String) =
        publishActivated(session.resumeRecoverableDraft(draftId))

    fun checkpointAndRelease(draftId: String): LayoutV2EditorHandoffResult {
        val lease = draftLease
        if (lease == null || !leaseRegistry.isCurrent(lease)) {
            return LayoutV2EditorHandoffResult.Rejected(
                LayoutV2EditorHandoffIssue.ALREADY_OWNED,
            )
        }
        val result = session.checkpointAndRelease(draftId)
        if (result is LayoutV2EditorHandoffResult.LaunchReady) {
            leaseRegistry.release(lease)
            draftLease = null
            mutableState.value = mutableState.value.copy(editor = session.state)
        }
        return result
    }

    fun discardRecovery(draftId: String) {
        val discarded = session.discardRecoverableDraft(draftId)
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = LayoutV2WorkspaceActionResult(
                if (discarded) LayoutV2WorkspaceActionCode.RECOVERY_DISCARDED
                else LayoutV2WorkspaceActionCode.REJECTED,
                if (discarded) null else LayoutV2EditorIssue.JOURNAL_WRITE_FAILED,
                draftId,
            ),
        )
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
            publishRejected(LayoutV2EditorIssue.NO_ACTIVE_DRAFT, null)
            return
        }
        when (val decision = elementCreationPolicy.create(draft, kind)) {
            is LayoutV2ElementCreationDecision.Ready ->
                publish(session.addElement(kind, decision.rect, decision.properties))
            LayoutV2ElementCreationDecision.UnsupportedKind ->
                publishRejected(LayoutV2EditorIssue.READ_ONLY_KIND, null)
            LayoutV2ElementCreationDecision.NoSafePlacement ->
                publishRejected(LayoutV2EditorIssue.COLLISION, null)
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

    fun save() {
        val result = session.saveDraft()
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = when (result) {
                is LayoutV2SaveResult.Saved -> LayoutV2WorkspaceActionResult(
                    LayoutV2WorkspaceActionCode.SAVED,
                    targetId = result.layoutId,
                )
                is LayoutV2SaveResult.Rejected -> LayoutV2WorkspaceActionResult(
                    LayoutV2WorkspaceActionCode.REJECTED,
                    result.issue,
                )
            },
        )
    }

    fun discard() {
        val discarded = session.discardDraft()
        releaseDraftLease()
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = LayoutV2WorkspaceActionResult(
                if (discarded) LayoutV2WorkspaceActionCode.DISCARDED
                else LayoutV2WorkspaceActionCode.REJECTED,
                if (discarded) null else LayoutV2EditorIssue.JOURNAL_WRITE_FAILED,
            ),
        )
    }

    fun leave() {
        session.leaveEditor()
        mutableState.value = mutableState.value.copy(editor = session.state)
    }

    fun onStop() {
        session.onStop()
        mutableState.value = mutableState.value.copy(editor = session.state)
    }

    override fun onCleared() {
        catalogRegistrationGeneration++
        catalogRegistration = null
        session.close()
        releaseDraftLease()
    }

    private fun committedRecords(): List<LayoutCatalogV2RegisteredRecord> =
        generations.listCommitted().map {
            LayoutCatalogV2RegisteredRecord(
                it.descriptor,
                LayoutLocalOrigin.LOCAL_COPY,
                LayoutWorkspaceState.DRAFT,
                it.artifact,
            )
        }

    private fun publish(result: LayoutV2EditResult) {
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = when (result) {
                LayoutV2EditResult.Applied ->
                    LayoutV2WorkspaceActionResult(LayoutV2WorkspaceActionCode.APPLIED)
                is LayoutV2EditResult.Rejected ->
                    LayoutV2WorkspaceActionResult(
                        LayoutV2WorkspaceActionCode.REJECTED,
                        result.issue,
                        result.elementId,
                    )
            },
        )
    }

    private fun publishActivated(result: LayoutV2EditResult) {
        if (result is LayoutV2EditResult.Applied) {
            val draftId = session.state.draft?.identity?.layoutId
            val currentLease = draftLease
            val ownsDraft = if (draftId == null) {
                false
            } else if (currentLease?.draftId == draftId && leaseRegistry.isCurrent(currentLease)) {
                true
            } else {
                releaseDraftLease()
                leaseRegistry.acquire(draftId, leaseOwnerToken)
                    ?.also { draftLease = it } != null
            }
            if (!ownsDraft) {
                session.releaseWithoutCheckpoint()
                publishRejected(LayoutV2EditorIssue.STALE_SOURCE_GENERATION, draftId)
                return
            }
        }
        publish(result)
    }

    private fun releaseDraftLease() {
        draftLease?.let(leaseRegistry::release)
        draftLease = null
    }

    private fun publishRejected(issue: LayoutV2EditorIssue, target: String?) {
        mutableState.value = mutableState.value.copy(
            lastAction = LayoutV2WorkspaceActionResult(
                LayoutV2WorkspaceActionCode.REJECTED,
                issue,
                target,
            ),
        )
    }
}
