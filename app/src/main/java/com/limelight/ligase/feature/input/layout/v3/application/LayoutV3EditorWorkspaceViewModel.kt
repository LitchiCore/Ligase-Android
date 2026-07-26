package com.limelight.ligase.feature.input.layout.v3.application

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3CommittedGeneration
import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3DraftJournal
import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3GenerationRepository
import com.limelight.ligase.feature.input.layout.v3.serialization.LayoutV3ContentVerificationResult
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3ContentVerifier
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.editor.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class LayoutV3WorkspaceActionCode {
    APPLIED, REJECTED, SAVED, DISCARDED, RECOVERY_DISCARDED, VALID,
}

enum class LayoutV3WorkspaceLaunchPhase { IDLE, AWAITING_VIEWPORT, EDITING }

data class LayoutV3WorkspaceActionResult(
    val code: LayoutV3WorkspaceActionCode,
    val issue: LayoutV3EditorIssue? = null,
    val targetId: String? = null,
) {
    override fun toString(): String =
        "LayoutV3WorkspaceActionResult(code=$code,issue=$issue,target=redacted)"
}

data class LayoutV3EditorWorkspaceUiState(
    val editor: LayoutV3EditorState = LayoutV3EditorState(),
    val committedLayouts: List<LayoutV3CommittedLayoutSummary> = emptyList(),
    val lastAction: LayoutV3WorkspaceActionResult? = null,
    val launchPhase: LayoutV3WorkspaceLaunchPhase = LayoutV3WorkspaceLaunchPhase.IDLE,
)

@JvmInline
value class LayoutV3CatalogRegistrationAttachment internal constructor(
    internal val generation: Long,
)

class LayoutV3EditorWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val generations = LayoutV3GenerationRepository(application)
    private val journal = LayoutV3DraftJournal(application)
    private val elementCreationPolicy = LayoutV3ElementCreationPolicy()
    private var catalogRegistration:
        ((List<LayoutV3RegisteredGeneration>) -> Boolean)? = null
    private var catalogRegistrationGeneration = 0L
    private val mutableState = MutableStateFlow(LayoutV3EditorWorkspaceUiState())
    val state: StateFlow<LayoutV3EditorWorkspaceUiState> = mutableState.asStateFlow()
    private val leaseRegistry = LayoutV3EditorProcessLeases.registry
    private val leaseOwnerToken = UUID.randomUUID().toString()
    private var draftLease: LayoutV3DraftLease? = null

    private val session = LayoutV3EditorSession(
        journal,
        generations,
        registerCommitted = {
            val callback = catalogRegistration ?: return@LayoutV3EditorSession false
            callback(committedRecords())
        },
        onStateChanged = { editor ->
            mutableState.value = mutableState.value.copy(editor = editor)
        },
    )

    init {
        mutableState.value = LayoutV3EditorWorkspaceUiState(
            editor = session.state,
            committedLayouts = committedSummaries(),
        )
    }

    fun attachCatalogRegistration(
        callback: (List<LayoutV3RegisteredGeneration>) -> Boolean,
    ): LayoutV3CatalogRegistrationAttachment {
        val attachment = LayoutV3CatalogRegistrationAttachment(++catalogRegistrationGeneration)
        catalogRegistration = callback
        callback(committedRecords())
        return attachment
    }

    fun detachCatalogRegistration(attachment: LayoutV3CatalogRegistrationAttachment) {
        if (attachment.generation != catalogRegistrationGeneration) return
        catalogRegistration = null
    }

    fun refreshCatalog(): Boolean {
        mutableState.value = mutableState.value.copy(
            committedLayouts = committedSummaries(),
        )
        return catalogRegistration?.invoke(committedRecords()) ?: false
    }

    fun refreshAfterEditorReturn(): Boolean {
        session.refreshRecoverableDrafts()
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            committedLayouts = committedSummaries(),
        )
        return refreshCatalog()
    }

    fun beginNewV3(displayName: String? = null): LayoutV3EditorLaunchRequest? {
        if (session.state.draft != null) {
            publishRejected(LayoutV3EditorIssue.INVALID_PAYLOAD, null)
            return null
        }
        mutableState.value = mutableState.value.copy(
            launchPhase = LayoutV3WorkspaceLaunchPhase.AWAITING_VIEWPORT,
            lastAction = null,
        )
        return LayoutV3EditorLaunchRequest(
            LayoutV3EditorLaunchMode.NEW_V3,
            displayName = displayName,
        )
    }

    fun createFromPackaged(layoutId: String, revision: Long, variantId: String) {
        publishRejected(LayoutV3EditorIssue.SOURCE_NOT_READY, layoutId)
    }

    fun createFromLocal(layoutId: String, revision: Long, variantId: String) {
        val generation = generations.read(layoutId, revision)
        if (generation == null) {
            publishRejected(LayoutV3EditorIssue.SOURCE_NOT_READY, layoutId)
            return
        }
        val verified = TouchLayoutV3ContentVerifier.verify(generation.artifact)
        if (verified !is LayoutV3ContentVerificationResult.Verified) {
            publishRejected(LayoutV3EditorIssue.VALIDATION_FAILED, layoutId)
            return
        }
        publishActivated(
            session.createFromLocalCopy(
                LayoutV3CreatorSource(
                    generation.descriptor,
                    verified.content,
                    LayoutV3DraftOrigin.LOCAL_COPY,
                    true,
                    LayoutV3WorkspaceState.DRAFT,
                ),
                variantId,
            ),
        )
    }

    fun launchCommitted(
        layoutId: String,
        revision: Long,
        variantId: String,
    ): LayoutV3EditorLaunchRequest? {
        val generation = generations.read(layoutId, revision)
        if (generation == null) {
            publishRejected(LayoutV3EditorIssue.SOURCE_NOT_READY, layoutId)
            return null
        }
        val verified = TouchLayoutV3ContentVerifier.verify(generation.artifact)
            as? LayoutV3ContentVerificationResult.Verified
        if (verified == null) {
            publishRejected(LayoutV3EditorIssue.VALIDATION_FAILED, layoutId)
            return null
        }
        publishActivated(
            session.openCommittedLocalCopy(
                LayoutV3CreatorSource(
                    generation.descriptor,
                    verified.content,
                    LayoutV3DraftOrigin.LOCAL_COPY,
                    true,
                    LayoutV3WorkspaceState.DRAFT,
                ),
                variantId,
            ),
        )
        return session.state.draft?.identity?.layoutId
            ?.let(::launchExistingAfterCheckpoint)
    }

    fun resumeRecovery(draftId: String) =
        publishActivated(session.resumeRecoverableDraft(draftId))

    fun checkpointAndRelease(draftId: String): LayoutV3EditorHandoffResult {
        val lease = draftLease
        if (lease == null || !leaseRegistry.isCurrent(lease)) {
            return LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.ALREADY_OWNED,
            )
        }
        val result = session.checkpointAndRelease(draftId)
        if (result is LayoutV3EditorHandoffResult.LaunchReady) {
            leaseRegistry.release(lease)
            draftLease = null
            mutableState.value = mutableState.value.copy(editor = session.state)
        }
        return result
    }

    fun launchExistingAfterCheckpoint(draftId: String): LayoutV3EditorLaunchRequest? =
        when (checkpointAndRelease(draftId)) {
            is LayoutV3EditorHandoffResult.LaunchReady ->
                LayoutV3EditorLaunchRequest(
                    LayoutV3EditorLaunchMode.EXISTING_V3,
                    draftId = draftId,
                )
            else -> null
        }

    fun discardRecovery(draftId: String) {
        val discarded = session.discardRecoverableDraft(draftId)
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = LayoutV3WorkspaceActionResult(
                if (discarded) LayoutV3WorkspaceActionCode.RECOVERY_DISCARDED
                else LayoutV3WorkspaceActionCode.REJECTED,
                if (discarded) null else LayoutV3EditorIssue.JOURNAL_WRITE_FAILED,
                draftId,
            ),
        )
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
            publishRejected(LayoutV3EditorIssue.NO_ACTIVE_DRAFT, null)
            return
        }
        when (val decision = elementCreationPolicy.create(draft, kind)) {
            is LayoutV3ElementCreationDecision.Ready ->
                publish(session.addElement(kind, decision.rect, decision.properties))
            LayoutV3ElementCreationDecision.UnsupportedKind ->
                publishRejected(LayoutV3EditorIssue.READ_ONLY_KIND, null)
            LayoutV3ElementCreationDecision.NoSafePlacement ->
                publishRejected(LayoutV3EditorIssue.LIMIT_EXCEEDED, null)
        }
    }

    fun addComboElement(
        keys: List<InputCode>,
        label: String? = null,
        description: String? = null,
    ): LayoutV3ElementCreateResult {
        val placement = configuredPlacement(ControlKind.COMBO)
        val rect = placement.first
            ?: return publishCreateRejected(checkNotNull(placement.second))
        return publishCreated(session.addComboElement(rect, keys, label, description))
    }

    fun addRadialElement(
        actions: List<LayoutV3NewRadialActionRequest>,
        label: String? = null,
    ): LayoutV3ElementCreateResult {
        val placement = configuredPlacement(ControlKind.RADIAL)
        val rect = placement.first
            ?: return publishCreateRejected(checkNotNull(placement.second))
        return publishCreated(session.addRadialElement(rect, actions, label))
    }

    private fun configuredPlacement(
        kind: ControlKind,
    ): Pair<IntRect?, LayoutV3EditorIssue?> {
        val draft = session.state.draft
            ?: return null to LayoutV3EditorIssue.NO_ACTIVE_DRAFT
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
        publishRejected(issue, null)
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

    fun save() {
        val result = session.saveDraft()
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = when (result) {
                is LayoutV3SaveResult.Saved -> LayoutV3WorkspaceActionResult(
                    LayoutV3WorkspaceActionCode.SAVED,
                    targetId = result.layoutId,
                )
                is LayoutV3SaveResult.Rejected -> LayoutV3WorkspaceActionResult(
                    LayoutV3WorkspaceActionCode.REJECTED,
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
            launchPhase = LayoutV3WorkspaceLaunchPhase.IDLE,
            lastAction = LayoutV3WorkspaceActionResult(
                if (discarded) LayoutV3WorkspaceActionCode.DISCARDED
                else LayoutV3WorkspaceActionCode.REJECTED,
                if (discarded) null else LayoutV3EditorIssue.JOURNAL_WRITE_FAILED,
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

    private fun committedRecords(): List<LayoutV3RegisteredGeneration> =
        generations.listCommitted().map {
            LayoutV3RegisteredGeneration(
                it.descriptor,
                LayoutV3LocalOrigin.LOCAL_COPY,
                LayoutV3WorkspaceState.DRAFT,
                it.artifact,
            )
        }

    private fun committedSummaries(): List<LayoutV3CommittedLayoutSummary> =
        generations.listCommitted().mapNotNull(LayoutV3CommittedGeneration::toSafeSummary)

    private fun publish(result: LayoutV3EditResult) {
        mutableState.value = mutableState.value.copy(
            editor = session.state,
            lastAction = when (result) {
                LayoutV3EditResult.Applied ->
                    LayoutV3WorkspaceActionResult(LayoutV3WorkspaceActionCode.APPLIED)
                is LayoutV3EditResult.Rejected ->
                    LayoutV3WorkspaceActionResult(
                        LayoutV3WorkspaceActionCode.REJECTED,
                        result.issue,
                        result.elementId,
                    )
            },
        )
    }

    private fun publishActivated(result: LayoutV3EditResult) {
        if (result is LayoutV3EditResult.Applied) {
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
                publishRejected(LayoutV3EditorIssue.STALE_SOURCE_GENERATION, draftId)
                return
            }
        }
        publish(result)
        if (result is LayoutV3EditResult.Applied) {
            mutableState.value = mutableState.value.copy(
                launchPhase = LayoutV3WorkspaceLaunchPhase.EDITING,
            )
        }
    }

    private fun releaseDraftLease() {
        draftLease?.let(leaseRegistry::release)
        draftLease = null
    }

    private fun publishRejected(issue: LayoutV3EditorIssue, target: String?) {
        mutableState.value = mutableState.value.copy(
            lastAction = LayoutV3WorkspaceActionResult(
                LayoutV3WorkspaceActionCode.REJECTED,
                issue,
                target,
            ),
        )
    }
}
