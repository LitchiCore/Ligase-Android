package com.limelight.ligase.feature.input.layout.v2.editor

import com.limelight.ligase.feature.input.layout.v2.application.LayoutCatalogV2RegisteredRecord
import com.limelight.ligase.feature.input.layout.v2.application.TouchLayoutV2ContentVerifier
import com.limelight.ligase.feature.input.layout.v2.data.*
import com.limelight.ligase.feature.input.layout.v2.domain.*
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Encoder
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Codec
import com.limelight.ligase.layout.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class LayoutV2CreatorSource(
    val descriptor: LayoutDescriptorV1,
    val content: VerifiedTouchLayoutV2Content,
    val origin: LayoutV2DraftOrigin,
    val availability: LayoutLocalAvailability,
    val workspace: LayoutWorkspaceState,
)

data class LayoutV2CreateBlankRequest(
    val displayName: String,
    val canvas: IntSize,
    val deviceClasses: List<DeviceClass>,
    val orientations: List<LayoutOrientation>,
    val recommendation: LayoutRecommendation,
)

class LayoutV2EditorSession(
    private val journal: LayoutV2DraftJournal,
    private val generations: LayoutV2GenerationRepository,
    private val registerCommitted: (LayoutCatalogV2RegisteredRecord) -> Boolean,
    private val uuid: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val onStateChanged: (LayoutV2EditorState) -> Unit = {},
) : AutoCloseable {
    var state: LayoutV2EditorState = LayoutV2EditorState(
        recoverableDrafts = journal.summaries(),
    )
        private set

    private var document: TouchLayoutV2Document? = null
    internal var candidateHash: String? = null
        private set
    private var journalWrite: ScheduledFuture<*>? = null
    private val journalExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "layout-v2-draft-journal").apply { isDaemon = true }
    }
    private var closed = false
    private var sessionGeneration = 0L

    fun createBlank(request: LayoutV2CreateBlankRequest): LayoutV2EditResult {
        if (
            request.displayName.isBlank() ||
            request.canvas.width <= 0 ||
            request.canvas.height <= 0
        ) return reject(LayoutV2EditorIssue.INVALID_PAYLOAD)
        val layoutId = canonicalUuid() ?: return reject(LayoutV2EditorIssue.INVALID_IDENTITY)
        val variantId = canonicalUuid() ?: return reject(LayoutV2EditorIssue.INVALID_IDENTITY)
        val variant = TouchLayoutV2Variant(
            variantId,
            request.deviceClasses,
            request.orientations,
            request.recommendation,
            request.canvas,
            emptyList(),
        )
        return activate(
            TouchLayoutV2Document(
                layoutId, 1, request.displayName, emptyMap(), listOf(variant), "",
            ),
            LayoutV2DraftIdentity(layoutId, 1, variantId, LayoutV2DraftOrigin.BLANK),
        )
    }

    fun createFromPackagedTemplate(
        source: LayoutV2CreatorSource,
        variantId: String,
    ): LayoutV2EditResult =
        copyFromSource(source, variantId, LayoutV2DraftOrigin.PACKAGED_TEMPLATE)

    fun createFromLocalCopy(
        source: LayoutV2CreatorSource,
        variantId: String,
    ): LayoutV2EditResult =
        copyFromSource(source, variantId, LayoutV2DraftOrigin.LOCAL_COPY)

    fun resumeRecoverableDraft(draftId: String): LayoutV2EditResult =
        when (val result = journal.read(draftId)) {
            is LayoutV2JournalReadResult.Ready ->
                activate(
                    result.entry.document,
                    result.entry.identity,
                    dirty = true,
                    recoveryAlreadySaved = true,
                )
            LayoutV2JournalReadResult.Missing -> reject(LayoutV2EditorIssue.SOURCE_NOT_READY)
            is LayoutV2JournalReadResult.Quarantined ->
                reject(LayoutV2EditorIssue.VALIDATION_FAILED)
        }

    fun discardRecoverableDraft(draftId: String): Boolean {
        val discarded = journal.discard(draftId)
        publish(state.copy(recoverableDrafts = journal.summaries()))
        return discarded
    }

    fun selectElement(elementId: String): LayoutV2EditResult {
        val element = selectedVariant()?.elements?.firstOrNull { it.elementId == elementId }
            ?: return reject(LayoutV2EditorIssue.UNKNOWN_ELEMENT, elementId)
        publish(state.copy(selectedElementId = element.elementId, issue = null))
        return LayoutV2EditResult.Applied
    }

    fun moveElement(elementId: String, x: Int, y: Int): LayoutV2EditResult =
        updateElement(elementId) { it.copy(rect = it.rect.copy(x = x, y = y)) }

    fun resizeElement(elementId: String, width: Int, height: Int): LayoutV2EditResult =
        updateElement(elementId) { it.copy(rect = it.rect.copy(width = width, height = height)) }

    fun setAnchors(
        elementId: String,
        horizontal: HorizontalAnchor,
        vertical: VerticalAnchor,
    ): LayoutV2EditResult = updateElement(elementId) {
        it.copy(horizontalAnchor = horizontal, verticalAnchor = vertical)
    }

    fun setZOrder(elementId: String, zOrder: Int): LayoutV2EditResult =
        updateElement(elementId) { it.copy(zOrder = zOrder) }

    fun deleteElement(elementId: String): LayoutV2EditResult {
        val variant = selectedVariant() ?: return reject(LayoutV2EditorIssue.NO_ACTIVE_DRAFT)
        val element = variant.elements.firstOrNull { it.elementId == elementId }
            ?: return reject(LayoutV2EditorIssue.UNKNOWN_ELEMENT, elementId)
        if (!element.editable()) return reject(LayoutV2EditorIssue.READ_ONLY_KIND, elementId)
        return replaceElements(variant.elements.filterNot { it.elementId == elementId })
    }

    fun updateProperties(
        elementId: String,
        properties: LayoutV2EditableProperties,
    ): LayoutV2EditResult = updateElement(elementId) { current ->
        val payload = properties.toPayload(current.kind) ?: return@updateElement null
        current.copy(payload = payload)
    }

    fun addElement(
        kind: ControlKind,
        rect: IntRect,
        properties: LayoutV2EditableProperties,
    ): LayoutV2EditResult {
        if (kind !in EDITABLE_KINDS) return reject(LayoutV2EditorIssue.READ_ONLY_KIND)
        val payload = properties.toPayload(kind) ?: return reject(LayoutV2EditorIssue.INVALID_PAYLOAD)
        val variant = selectedVariant() ?: return reject(LayoutV2EditorIssue.NO_ACTIVE_DRAFT)
        val elementId = canonicalUuid() ?: return reject(LayoutV2EditorIssue.INVALID_IDENTITY)
        val nextZ = (variant.elements.maxOfOrNull { it.zOrder } ?: -1) + 1
        val element = TouchLayoutV2Element(
            elementId, kind, rect, HorizontalAnchor.LEFT, VerticalAnchor.TOP, nextZ,
            enabled = true, hidden = false, opacityPermille = 1000, payload, null,
        )
        return replaceElements(variant.elements + element)
    }

    @Synchronized
    fun flushJournal(): LayoutV2JournalWriteResult {
        journalWrite?.cancel(false)
        val active = document ?: return LayoutV2JournalWriteResult.INVALID
        val identity = state.draft?.identity ?: return LayoutV2JournalWriteResult.INVALID
        publish(state.copy(recoveryProtection = LayoutV2RecoveryProtection.PENDING))
        val raw = draftRaw(active) ?: return journalFailure()
        return when (val result = journal.write(identity, raw)) {
            LayoutV2JournalWriteResult.SAVED -> {
                publish(state.copy(
                    recoveryProtection = LayoutV2RecoveryProtection.SAVED,
                    recoverableDrafts = journal.summaries(),
                    issue = null,
                ))
                result
            }
            else -> {
                journalFailure()
                result
            }
        }
    }

    fun onStop() {
        if (state.dirty) flushJournal()
    }

    fun refreshRecoverableDrafts() {
        publish(state.copy(recoverableDrafts = journal.summaries()))
    }

    @Synchronized
    fun checkpointAndRelease(expectedDraftId: String): LayoutV2EditorHandoffResult {
        if (closed) {
            return LayoutV2EditorHandoffResult.Rejected(LayoutV2EditorHandoffIssue.CLOSED)
        }
        val activeDraftId = state.draft?.identity?.layoutId
            ?: return LayoutV2EditorHandoffResult.Rejected(
                LayoutV2EditorHandoffIssue.NO_ACTIVE_DRAFT,
            )
        if (activeDraftId != expectedDraftId) {
            return LayoutV2EditorHandoffResult.Rejected(
                LayoutV2EditorHandoffIssue.DRAFT_ID_MISMATCH,
            )
        }
        if (
            state.recoveryProtection != LayoutV2RecoveryProtection.SAVED &&
            flushJournal() != LayoutV2JournalWriteResult.SAVED
        ) {
            return LayoutV2EditorHandoffResult.Rejected(
                LayoutV2EditorHandoffIssue.CHECKPOINT_FAILED,
            )
        }
        releaseInMemoryDraft()
        return LayoutV2EditorHandoffResult.LaunchReady(activeDraftId)
    }

    @Synchronized
    fun releaseWithoutCheckpoint() {
        releaseInMemoryDraft()
    }

    fun validateDraft(): Boolean {
        val active = document ?: return false
        val ready = catalogRaw(active) != null
        publish(
            state.copy(
                candidateReady = ready,
                issue = if (ready) null else LayoutV2EditorIssue.VALIDATION_FAILED,
            ),
        )
        return ready
    }

    fun leaveEditor() {
        if (state.dirty) flushJournal()
        publish(
            state.copy(
                selectedElementId = null,
                issue = null,
            ),
        )
    }

    fun saveDraft(): LayoutV2SaveResult {
        val active = document ?: return saveRejected(LayoutV2EditorIssue.NO_ACTIVE_DRAFT)
        val identity = state.draft?.identity ?: return saveRejected(LayoutV2EditorIssue.NO_ACTIVE_DRAFT)
        publish(state.copy(phase = LayoutV2EditorPhase.SAVING, saving = true, issue = null))
        val raw = catalogRaw(active) ?: return saveRejected(LayoutV2EditorIssue.VALIDATION_FAILED)
        val descriptor = descriptor(active)
        val result = generations.commit(descriptor, raw) {
            registerCommitted(
                LayoutCatalogV2RegisteredRecord(
                    it,
                    LayoutLocalOrigin.LOCAL_COPY,
                    LayoutWorkspaceState.DRAFT,
                ),
            )
        }
        if (result.code != LayoutV2GenerationWriteCode.SAVED) {
            return saveRejected(result.code.toEditorIssue())
        }
        if (!journal.discard(identity.layoutId)) {
            return saveRejected(LayoutV2EditorIssue.JOURNAL_WRITE_FAILED)
        }
        publish(state.copy(
            phase = LayoutV2EditorPhase.SAVED,
            dirty = false,
            saving = false,
            issue = null,
            candidateReady = true,
            recoveryProtection = LayoutV2RecoveryProtection.NOT_REQUIRED,
            recoverableDrafts = journal.summaries(),
        ))
        return LayoutV2SaveResult.Saved(identity.layoutId, identity.revision, identity.variantId)
    }

    fun exportCommittedArtifact(layoutId: String, revision: Long): LayoutV2ExportResult =
        generations.export(layoutId, revision)
            ?.let { LayoutV2ExportResult.Ready(LayoutV2ExportArtifact(it)) }
            ?: LayoutV2ExportResult.ContentNotReady

    fun discardDraft(): Boolean {
        journalWrite?.cancel(false)
        val draftId = state.draft?.identity?.layoutId
        val discarded = draftId == null || journal.discard(draftId)
        document = null
        candidateHash = null
        publish(LayoutV2EditorState(recoverableDrafts = journal.summaries()))
        return discarded
    }

    @Synchronized
    override fun close() {
        if (closed) return
        if (state.dirty) flushJournal()
        closed = true
        sessionGeneration++
        journalExecutor.shutdown()
    }

    private fun copyFromSource(
        source: LayoutV2CreatorSource,
        variantId: String,
        expectedOrigin: LayoutV2DraftOrigin,
    ): LayoutV2EditResult {
        if (
            source.origin != expectedOrigin ||
            source.availability != LayoutLocalAvailability.READY
        ) return reject(LayoutV2EditorIssue.SOURCE_NOT_READY)
        if (
            expectedOrigin == LayoutV2DraftOrigin.PACKAGED_TEMPLATE &&
            source.workspace != LayoutWorkspaceState.NONE
        ) return reject(LayoutV2EditorIssue.SOURCE_NOT_READY)
        if (source.descriptor.publicationStatus == "retired") {
            return reject(LayoutV2EditorIssue.RETIRED)
        }
        val selected = source.content.document.variants.firstOrNull { it.variantId == variantId }
            ?: return reject(LayoutV2EditorIssue.VARIANT_NOT_FOUND)
        val layoutId = canonicalUuid() ?: return reject(LayoutV2EditorIssue.INVALID_IDENTITY)
        val newVariantId = canonicalUuid() ?: return reject(LayoutV2EditorIssue.INVALID_IDENTITY)
        val copied = selected.copy(variantId = newVariantId)
        val document = source.content.document.copy(
            layoutId = layoutId,
            revision = 1,
            variants = listOf(copied),
            contentHash = "",
        )
        return activate(
            document,
            LayoutV2DraftIdentity(
                layoutId, 1, newVariantId, expectedOrigin,
                source.descriptor.layoutId, source.descriptor.revision,
            ),
        )
    }

    private fun activate(
        value: TouchLayoutV2Document,
        identity: LayoutV2DraftIdentity,
        dirty: Boolean = true,
        recoveryAlreadySaved: Boolean = false,
    ): LayoutV2EditResult {
        sessionGeneration++
        document = value
        candidateHash = draftRaw(value)?.let { TouchLayoutV2Codec.decodeDraft(it).contentHash }
        publish(LayoutV2EditorState(
            phase = LayoutV2EditorPhase.EDITING,
            draft = value.toEditorDraft(identity),
            dirty = dirty,
            candidateReady = catalogRaw(value) != null,
            recoveryProtection = if (recoveryAlreadySaved) {
                LayoutV2RecoveryProtection.SAVED
            } else {
                LayoutV2RecoveryProtection.PENDING
            },
            recoverableDrafts = journal.summaries(),
        ))
        if (!recoveryAlreadySaved) scheduleJournal()
        return LayoutV2EditResult.Applied
    }

    private fun updateElement(
        elementId: String,
        transform: (TouchLayoutV2Element) -> TouchLayoutV2Element?,
    ): LayoutV2EditResult {
        val variant = selectedVariant() ?: return reject(LayoutV2EditorIssue.NO_ACTIVE_DRAFT)
        val index = variant.elements.indexOfFirst { it.elementId == elementId }
        if (index < 0) return reject(LayoutV2EditorIssue.UNKNOWN_ELEMENT, elementId)
        val current = variant.elements[index]
        if (!current.editable()) return reject(LayoutV2EditorIssue.READ_ONLY_KIND, elementId)
        val updated = transform(current)
            ?: return reject(LayoutV2EditorIssue.INVALID_PAYLOAD, elementId)
        val next = variant.elements.toMutableList().also { it[index] = updated }
        return replaceElements(next)
    }

    private fun replaceElements(elements: List<TouchLayoutV2Element>): LayoutV2EditResult {
        val active = document ?: return reject(LayoutV2EditorIssue.NO_ACTIVE_DRAFT)
        val identity = state.draft?.identity ?: return reject(LayoutV2EditorIssue.NO_ACTIVE_DRAFT)
        val variants = active.variants.map {
            if (it.variantId == identity.variantId) it.copy(elements = elements.sortedWith(ELEMENT_ORDER))
            else it
        }
        val candidate = active.copy(variants = variants, contentHash = "")
        if (draftRaw(candidate) == null) return reject(LayoutV2EditorIssue.VALIDATION_FAILED)
        document = candidate
        candidateHash = draftRaw(candidate)?.let { TouchLayoutV2Codec.decodeDraft(it).contentHash }
        publish(state.copy(
            phase = LayoutV2EditorPhase.EDITING,
            draft = candidate.toEditorDraft(identity),
            dirty = true,
            issue = null,
            candidateReady = catalogRaw(candidate) != null,
            recoveryProtection = LayoutV2RecoveryProtection.PENDING,
        ))
        scheduleJournal()
        return LayoutV2EditResult.Applied
    }

    private fun selectedVariant(): TouchLayoutV2Variant? {
        val identity = state.draft?.identity ?: return null
        return document?.variants?.firstOrNull { it.variantId == identity.variantId }
    }
    private fun scheduleJournal() {
        journalWrite?.cancel(false)
        val scheduledGeneration = sessionGeneration
        journalWrite = journalExecutor.schedule(
            { flushScheduledJournal(scheduledGeneration) },
            JOURNAL_DEBOUNCE_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    @Synchronized
    private fun flushScheduledJournal(scheduledGeneration: Long) {
        if (
            !closed &&
            scheduledGeneration == sessionGeneration &&
            state.dirty
        ) {
            flushJournal()
        }
    }

    private fun releaseInMemoryDraft() {
        sessionGeneration++
        journalWrite?.cancel(false)
        journalWrite = null
        document = null
        candidateHash = null
        publish(LayoutV2EditorState(recoverableDrafts = journal.summaries()))
    }
    private fun draftRaw(value: TouchLayoutV2Document): ByteArray? = runCatching {
        val raw = TouchLayoutV2Encoder.encode(value)
        TouchLayoutV2Codec.decodeDraft(raw)
        raw
    }.getOrNull()
    private fun catalogRaw(value: TouchLayoutV2Document): ByteArray? = runCatching {
        val raw = TouchLayoutV2Encoder.encode(value)
        val result = TouchLayoutV2ContentVerifier.verify(raw)
        require(result is LayoutContentVerificationResult.Verified)
        raw
    }.getOrNull()
    private fun descriptor(value: TouchLayoutV2Document) = LayoutDescriptorV1(
        1,
        value.layoutId,
        value.revision,
        emptyList(),
        LayoutCompatibilityV1(1, 1),
        "draft",
        value.variants.map {
            LayoutVariantV1(
                it.variantId,
                "touch",
                it.deviceClasses.map { device -> device.name.lowercase() },
                it.orientations.map { orientation -> orientation.name.lowercase() },
            )
        },
    )
    private fun TouchLayoutV2Document.toEditorDraft(identity: LayoutV2DraftIdentity):
        LayoutV2EditorDraft {
        val variant = variants.first { it.variantId == identity.variantId }
        return LayoutV2EditorDraft(
            identity, displayName, variant.canvas, variant.deviceClasses, variant.orientations,
            variant.recommendation, variant.elements.map { it.toEditorElement() },
        )
    }
    private fun reject(issue: LayoutV2EditorIssue, elementId: String? = null): LayoutV2EditResult {
        publish(state.copy(issue = issue))
        return LayoutV2EditResult.Rejected(issue, elementId)
    }
    private fun journalFailure(): LayoutV2JournalWriteResult {
        publish(state.copy(
            recoveryProtection = LayoutV2RecoveryProtection.NOT_SAVED,
            issue = LayoutV2EditorIssue.JOURNAL_WRITE_FAILED,
        ))
        return LayoutV2JournalWriteResult.WRITE_FAILED
    }
    private fun saveRejected(issue: LayoutV2EditorIssue): LayoutV2SaveResult {
        publish(state.copy(
            phase = LayoutV2EditorPhase.FAILED,
            saving = false,
            issue = issue,
        ))
        return LayoutV2SaveResult.Rejected(issue)
    }
    private fun canonicalUuid(): String? = uuid().lowercase().takeIf {
        LayoutContractV1Validator.normalizeUuid(it) == it
    }
    private fun publish(next: LayoutV2EditorState) {
        state = next
        onStateChanged(next)
    }

    private companion object {
        const val JOURNAL_DEBOUNCE_MILLIS = 350L
        val EDITABLE_KINDS = setOf(
            ControlKind.KEYBOARD, ControlKind.MOUSE, ControlKind.ANALOG,
            ControlKind.DPAD, ControlKind.SOFT_KEYBOARD,
        )
        val ELEMENT_ORDER = compareBy<TouchLayoutV2Element>({ it.zOrder }, { it.elementId })
    }
}

private fun TouchLayoutV2Element.editable(): Boolean =
    kind in setOf(
        ControlKind.KEYBOARD, ControlKind.MOUSE, ControlKind.ANALOG,
        ControlKind.DPAD, ControlKind.SOFT_KEYBOARD,
    )

private fun LayoutV2EditableProperties.toPayload(kind: ControlKind): ControlPayload? = when (this) {
    is LayoutV2EditableProperties.Keyboard -> takeIf { kind == ControlKind.KEYBOARD }?.let {
        KeyboardPayload(inputCode, appearance, trigger, timedHoldMs)
    }
    is LayoutV2EditableProperties.Mouse -> takeIf { kind == ControlKind.MOUSE }?.let {
        MousePayload(button, appearance, trigger, timedHoldMs)
    }
    is LayoutV2EditableProperties.Analog -> takeIf { kind == ControlKind.ANALOG }?.let {
        DirectionalPayload(kind, up, down, left, right, press, diagonalPolicy)
    }
    is LayoutV2EditableProperties.Dpad -> takeIf { kind == ControlKind.DPAD }?.let {
        DirectionalPayload(kind, up, down, left, right, press, diagonalPolicy)
    }
    LayoutV2EditableProperties.SoftKeyboard ->
        SoftKeyboardPayload.takeIf { kind == ControlKind.SOFT_KEYBOARD }
}

private fun LayoutV2GenerationWriteCode.toEditorIssue() = when (this) {
    LayoutV2GenerationWriteCode.SAVED -> error("not an error")
    LayoutV2GenerationWriteCode.INVALID_DESCRIPTOR -> LayoutV2EditorIssue.INVALID_IDENTITY
    LayoutV2GenerationWriteCode.CONTENT_REJECTED,
    LayoutV2GenerationWriteCode.ALIGNMENT_INVALID -> LayoutV2EditorIssue.VALIDATION_FAILED
    LayoutV2GenerationWriteCode.WRITE_FAILED -> LayoutV2EditorIssue.WRITE_FAILED
    LayoutV2GenerationWriteCode.READBACK_FAILED -> LayoutV2EditorIssue.READBACK_FAILED
    LayoutV2GenerationWriteCode.REGISTRATION_FAILED -> LayoutV2EditorIssue.REGISTRATION_FAILED
    LayoutV2GenerationWriteCode.ROLLBACK_FAILED -> LayoutV2EditorIssue.ROLLBACK_FAILED
}
