package com.limelight.ligase.feature.input.layout.v3.editor

import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3ContentVerifier
import com.limelight.ligase.feature.input.layout.v3.serialization.LayoutV3ContentVerificationResult
import com.limelight.ligase.feature.input.layout.v3.data.*
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Encoder
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Codec
import com.limelight.ligase.layout.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class LayoutV3CreatorSource(
    val descriptor: LayoutDescriptorV1,
    val content: VerifiedTouchLayoutV3Content,
    val origin: LayoutV3DraftOrigin,
    val availability: Boolean,
    val workspace: LayoutV3WorkspaceState,
)

data class LayoutV3CreateBlankRequest(
    val displayName: String,
    val canvas: IntSize,
    val deviceClasses: List<DeviceClass>,
    val orientations: List<LayoutOrientation>,
    val recommendation: LayoutRecommendation,
)

class LayoutV3EditorSession(
    private val journal: LayoutV3DraftJournal,
    private val generations: LayoutV3GenerationRepository,
    private val registerCommitted: (LayoutV3RegisteredGeneration) -> Boolean,
    private val uuid: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val onStateChanged: (LayoutV3EditorState) -> Unit = {},
) : AutoCloseable {
    var state: LayoutV3EditorState = LayoutV3EditorState(
        recoverableDrafts = journal.summaries(),
    )
        private set

    private var document: TouchLayoutV3Document? = null
    internal var candidateHash: String? = null
        private set
    private var journalWrite: ScheduledFuture<*>? = null
    private val journalExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "layout-v3-draft-journal").apply { isDaemon = true }
    }
    private var closed = false
    private var sessionGeneration = 0L
    private val gestureTokens = mutableMapOf<String, Long>()

    fun createBlank(request: LayoutV3CreateBlankRequest): LayoutV3EditResult {
        if (
            request.displayName.isBlank() ||
            request.canvas.width <= 0 ||
            request.canvas.height <= 0
        ) return reject(LayoutV3EditorIssue.INVALID_PAYLOAD)
        val layoutId = canonicalUuid() ?: return reject(LayoutV3EditorIssue.INVALID_IDENTITY)
        val variantId = canonicalUuid() ?: return reject(LayoutV3EditorIssue.INVALID_IDENTITY)
        val variant = TouchLayoutV3Variant(
            variantId,
            request.deviceClasses,
            request.orientations,
            request.recommendation,
            request.canvas,
            emptyList(),
        )
        return activate(
            TouchLayoutV3Document(
                layoutId, 1, 1L, request.displayName, 1000, emptyMap(), listOf(variant), "",
            ),
            LayoutV3DraftIdentity(layoutId, 1, variantId, LayoutV3DraftOrigin.BLANK),
        )
    }

    fun createFromPackagedTemplate(
        source: LayoutV3CreatorSource,
        variantId: String,
    ): LayoutV3EditResult =
        copyFromSource(source, variantId, LayoutV3DraftOrigin.PACKAGED_TEMPLATE)

    fun createFromLocalCopy(
        source: LayoutV3CreatorSource,
        variantId: String,
    ): LayoutV3EditResult =
        copyFromSource(source, variantId, LayoutV3DraftOrigin.LOCAL_COPY)

    fun openCommittedLocalCopy(
        source: LayoutV3CreatorSource,
        variantId: String,
    ): LayoutV3EditResult {
        if (
            source.origin != LayoutV3DraftOrigin.LOCAL_COPY ||
            source.availability != true
        ) return reject(LayoutV3EditorIssue.SOURCE_NOT_READY)
        val document = source.content.document
        if (source.descriptor.publicationStatus == "retired") {
            return reject(LayoutV3EditorIssue.RETIRED)
        }
        if (document.variants.none { it.variantId == variantId }) {
            return reject(LayoutV3EditorIssue.VARIANT_NOT_FOUND)
        }
        return activate(
            document,
            LayoutV3DraftIdentity(
                document.layoutId,
                document.revision,
                variantId,
                LayoutV3DraftOrigin.LOCAL_COPY,
            ),
            dirty = false,
        )
    }

    fun resumeRecoverableDraft(draftId: String): LayoutV3EditResult =
        when (val result = journal.read(draftId)) {
            is LayoutV3JournalReadResult.Ready ->
                activate(
                    result.entry.document,
                    result.entry.identity,
                    dirty = true,
                    recoveryAlreadySaved = true,
                )
            LayoutV3JournalReadResult.Missing -> reject(LayoutV3EditorIssue.SOURCE_NOT_READY)
            is LayoutV3JournalReadResult.Quarantined ->
                reject(LayoutV3EditorIssue.VALIDATION_FAILED)
        }

    fun discardRecoverableDraft(draftId: String): Boolean {
        val discarded = journal.discard(draftId)
        publish(state.copy(recoverableDrafts = journal.summaries()))
        return discarded
    }

    fun selectElement(elementId: String): LayoutV3EditResult {
        val element = selectedVariant()?.elements?.firstOrNull { it.elementId == elementId }
            ?: return reject(LayoutV3EditorIssue.UNKNOWN_ELEMENT, elementId)
        publish(state.copy(selectedElementId = element.elementId, issue = null))
        return LayoutV3EditResult.Applied
    }

    fun moveElement(elementId: String, x: Int, y: Int): LayoutV3EditResult =
        updateResolvedRect(elementId) { it.copy(x = x, y = y) }

    fun resizeElement(elementId: String, width: Int, height: Int): LayoutV3EditResult {
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val current = variant.elements.firstOrNull { it.elementId == elementId }
            ?: return reject(LayoutV3EditorIssue.UNKNOWN_ELEMENT, elementId)
        val resolved = LayoutV3Geometry.resolve(variant.canvas, current)
        return resizeResolvedRect(elementId, resolved.copy(width = width, height = height))
    }

    fun nudgeElement(elementId: String, deltaX: Int, deltaY: Int): LayoutV3EditResult =
        updateResolvedRect(elementId) {
            it.copy(
                x = Math.addExact(it.x, deltaX),
                y = Math.addExact(it.y, deltaY),
            )
        }

    fun rebaseElement(elementId: String): LayoutV3EditResult =
        updateResolvedRect(elementId) { it }

    @Synchronized
    fun beginGesture(elementId: String): LayoutV3GestureStartResult {
        if (closed || selectedVariant()?.elements?.none { it.elementId == elementId } != false) {
            return LayoutV3GestureStartResult.Rejected(LayoutV3EditorIssue.UNKNOWN_ELEMENT)
        }
        val token = LayoutV3GestureCommitToken(UUID.randomUUID().toString(), elementId)
        gestureTokens[token.value] = sessionGeneration
        return LayoutV3GestureStartResult.Ready(token)
    }

    @Synchronized
    fun commitMove(
        token: LayoutV3GestureCommitToken,
        x: Int,
        y: Int,
    ): LayoutV3EditResult {
        val generation = gestureTokens.remove(token.value)
        if (generation == null || generation != sessionGeneration) {
            return reject(LayoutV3EditorIssue.STALE_GESTURE, token.elementId)
        }
        return moveElement(token.elementId, x, y)
    }

    @Synchronized
    fun commitResize(
        token: LayoutV3GestureCommitToken,
        width: Int,
        height: Int,
    ): LayoutV3EditResult {
        val generation = gestureTokens.remove(token.value)
        if (generation == null || generation != sessionGeneration) {
            return reject(LayoutV3EditorIssue.STALE_GESTURE, token.elementId)
        }
        return resizeElement(token.elementId, width, height)
    }

    @Synchronized
    fun commitResolvedRect(
        token: LayoutV3GestureCommitToken,
        rect: IntRect,
    ): LayoutV3EditResult {
        val generation = gestureTokens.remove(token.value)
        if (generation == null || generation != sessionGeneration) {
            return reject(LayoutV3EditorIssue.STALE_GESTURE, token.elementId)
        }
        return resizeResolvedRect(token.elementId, rect)
    }

    @Synchronized
    fun cancelGesture(token: LayoutV3GestureCommitToken): LayoutV3EditResult =
        if (gestureTokens.remove(token.value) != null) LayoutV3EditResult.Applied
        else reject(LayoutV3EditorIssue.STALE_GESTURE, token.elementId)

    fun setAnchors(
        elementId: String,
        horizontal: HorizontalAnchor,
        vertical: VerticalAnchor,
    ): LayoutV3EditResult = reject(LayoutV3EditorIssue.INVALID_PAYLOAD, elementId)

    fun setZOrder(elementId: String, zOrder: Int): LayoutV3EditResult {
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        if (zOrder !in 0 until variant.elements.size) {
            return reject(LayoutV3EditorIssue.INVALID_Z_ORDER, elementId)
        }
        val selected = variant.elements.firstOrNull { it.elementId == elementId }
            ?: return reject(LayoutV3EditorIssue.UNKNOWN_ELEMENT, elementId)
        val reordered = variant.elements.sortedBy { it.zOrder }.toMutableList()
        reordered.remove(selected)
        reordered.add(zOrder, selected)
        return replaceElements(reordered.mapIndexed { index, element -> element.copy(zOrder = index) })
    }

    fun setLayoutOpacityPermille(
        opacityPermille: Int,
    ): LayoutV3EditResult {
        if (opacityPermille !in MIN_OPACITY_PERMILLE..MAX_OPACITY_PERMILLE) {
            return reject(LayoutV3EditorIssue.OUT_OF_RANGE)
        }
        val active = document ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val identity = state.draft?.identity ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        return replaceDocument(
            active.copy(opacityPermille = opacityPermille, contentHash = ""),
            identity,
        )
    }

    fun deleteElement(elementId: String): LayoutV3EditResult {
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val element = variant.elements.firstOrNull { it.elementId == elementId }
            ?: return reject(LayoutV3EditorIssue.UNKNOWN_ELEMENT, elementId)
        if (!element.editable()) return reject(LayoutV3EditorIssue.READ_ONLY_KIND, elementId)
        return replaceElements(variant.elements.filterNot { it.elementId == elementId })
    }

    fun updateProperties(
        elementId: String,
        properties: LayoutV3EditableProperties,
    ): LayoutV3EditResult = updateElement(elementId) { current ->
        val payload = properties.toPayload(current.kind) ?: return@updateElement null
        current.copy(payload = payload)
    }

    fun replaceComboChord(elementId: String, keys: List<InputCode>): LayoutV3EditResult {
        val target = selectedVariant()?.elements?.firstOrNull { it.elementId == elementId }
            ?: return reject(LayoutV3EditorIssue.UNKNOWN_ELEMENT, elementId)
        if (target.kind != ControlKind.COMBO) {
            return reject(LayoutV3EditorIssue.WRONG_KIND, elementId)
        }
        val canonical = validateAndCanonicalizeChord(keys)
            ?: return reject(chordIssue(keys), elementId)
        return updateElementAndPersist(elementId) { current ->
            val payload = current.payload as? ChordPayload
                ?: return@updateElementAndPersist null
            if (current.kind != ControlKind.COMBO) return@updateElementAndPersist null
            current.copy(payload = payload.copy(keys = canonical))
        }
    }

    fun addRadialAction(
        elementId: String,
        label: String?,
        keys: List<InputCode>,
    ): LayoutV3RadialEditResult {
        val current = radialElement(elementId) ?: return radialReject(radialTargetIssue(elementId))
        val canonical = validateAndCanonicalizeChord(keys)
            ?: return radialReject(chordIssue(keys))
        if (!validOptionalLabel(label)) return radialReject(LayoutV3EditorIssue.INVALID_LABEL)
        val payload = current.payload as RadialPayload
        if (payload.actions.size >= MAX_RADIAL_ACTIONS) {
            return radialReject(LayoutV3EditorIssue.ACTION_LIMIT)
        }
        val existing = payload.actions.mapTo(mutableSetOf()) { it.actionId }
        val actionId = generateRadialActionId(existing)
            ?: return radialReject(LayoutV3EditorIssue.ID_GENERATION_FAILED)
        val actions = payload.actions + RadialAction(actionId, payload.actions.size, canonical, label)
        return applyRadial(elementId, current, payload.copy(actions = actions), actionId)
    }

    fun removeRadialAction(elementId: String, actionId: String): LayoutV3RadialEditResult {
        val current = radialElement(elementId) ?: return radialReject(radialTargetIssue(elementId))
        val payload = current.payload as RadialPayload
        if (payload.actions.none { it.actionId == actionId }) {
            return radialReject(LayoutV3EditorIssue.UNKNOWN_ACTION)
        }
        if (payload.actions.size <= MIN_RADIAL_ACTIONS) {
            return radialReject(LayoutV3EditorIssue.MINIMUM_ACTIONS)
        }
        val actions = payload.actions.filterNot { it.actionId == actionId }
            .mapIndexed { order, action -> action.copy(order = order) }
        return applyRadial(elementId, current, payload.copy(actions = actions))
    }

    fun reorderRadialAction(
        elementId: String,
        actionId: String,
        targetOrder: Int,
    ): LayoutV3RadialEditResult {
        val current = radialElement(elementId) ?: return radialReject(radialTargetIssue(elementId))
        val payload = current.payload as RadialPayload
        val selected = payload.actions.firstOrNull { it.actionId == actionId }
            ?: return radialReject(LayoutV3EditorIssue.UNKNOWN_ACTION)
        if (targetOrder !in payload.actions.indices) return radialReject(LayoutV3EditorIssue.OUT_OF_RANGE)
        val actions = payload.actions.sortedBy { it.order }.toMutableList()
        actions.remove(selected)
        actions.add(targetOrder, selected)
        return applyRadial(
            elementId,
            current,
            payload.copy(actions = actions.mapIndexed { order, action -> action.copy(order = order) }),
        )
    }

    fun replaceRadialActionLabel(
        elementId: String,
        actionId: String,
        label: String?,
    ): LayoutV3RadialEditResult {
        val targetIssue = radialActionTargetIssue(elementId, actionId)
        if (targetIssue != null) return radialReject(targetIssue)
        if (!validOptionalLabel(label)) return radialReject(LayoutV3EditorIssue.INVALID_LABEL)
        return updateRadialAction(elementId, actionId) { it.copy(label = label) }
    }

    fun replaceRadialActionChord(
        elementId: String,
        actionId: String,
        keys: List<InputCode>,
    ): LayoutV3RadialEditResult {
        val targetIssue = radialActionTargetIssue(elementId, actionId)
        if (targetIssue != null) return radialReject(targetIssue)
        val canonical = validateAndCanonicalizeChord(keys)
            ?: return radialReject(chordIssue(keys))
        return updateRadialAction(elementId, actionId) { it.copy(keys = canonical) }
    }

    fun addElement(
        kind: ControlKind,
        rect: IntRect,
        properties: LayoutV3EditableProperties,
    ): LayoutV3EditResult {
        if (kind !in EDITABLE_KINDS) return reject(LayoutV3EditorIssue.READ_ONLY_KIND)
        val payload = properties.toPayload(kind) ?: return reject(LayoutV3EditorIssue.INVALID_PAYLOAD)
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val elementId = canonicalUuid() ?: return reject(LayoutV3EditorIssue.INVALID_IDENTITY)
        val nextZ = (variant.elements.maxOfOrNull { it.zOrder } ?: -1) + 1
        val rebased = LayoutV3Geometry.rebase(variant.canvas, rect)
        val element = TouchLayoutV3Element(
            elementId, kind, rebased.second, rebased.first.first, rebased.first.second, nextZ,
            enabled = true, hidden = false, payload, null,
        )
        return replaceElements(variant.elements + element)
    }

    fun addComboElement(
        rect: IntRect,
        keys: List<InputCode>,
        label: String? = null,
        description: String? = null,
    ): LayoutV3ElementCreateResult {
        val variant = selectedVariant() ?: return createReject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val canonical = validateAndCanonicalizeChord(keys)
            ?: return createReject(chordIssue(keys))
        val resolvedLabel = label ?: DEFAULT_COMBO_LABEL
        val resolvedDescription = description ?: ""
        if (!validAppearanceText(resolvedLabel, 32) ||
            !validAppearanceText(resolvedDescription, 80)
        ) {
            return createReject(LayoutV3EditorIssue.INVALID_LABEL)
        }
        if (runCatching { LayoutV3Geometry.rebase(variant.canvas, rect) }.isFailure) {
            return createReject(LayoutV3EditorIssue.VALIDATION_FAILED)
        }
        val elementId = canonicalUuid()
            ?: return createReject(LayoutV3EditorIssue.INVALID_IDENTITY)
        val element = runCatching {
            newElement(
                variant,
                elementId,
                ControlKind.COMBO,
                rect,
                ChordPayload(
                    ControlKind.COMBO,
                    canonical,
                    Trigger.HOLD,
                    null,
                    Appearance(resolvedLabel, resolvedDescription, "circle", false),
                    null,
                ),
            )
        }.getOrNull() ?: return createReject(LayoutV3EditorIssue.VALIDATION_FAILED)
        return applyCreatedElement(variant, element)
    }

    fun addRadialElement(
        rect: IntRect,
        actions: List<LayoutV3NewRadialActionRequest>,
        label: String? = null,
    ): LayoutV3ElementCreateResult {
        val variant = selectedVariant() ?: return createReject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        if (actions.size < MIN_RADIAL_ACTIONS) {
            return createReject(LayoutV3EditorIssue.MINIMUM_ACTIONS)
        }
        if (actions.size > MAX_RADIAL_ACTIONS) {
            return createReject(LayoutV3EditorIssue.ACTION_LIMIT)
        }
        val resolvedLabel = label ?: DEFAULT_RADIAL_LABEL
        if (!validOptionalLabel(resolvedLabel)) {
            return createReject(LayoutV3EditorIssue.INVALID_LABEL)
        }
        val canonicalActions = ArrayList<Pair<String?, List<InputCode>>>(actions.size)
        for (request in actions) {
            val canonical = validateAndCanonicalizeChord(request.keys)
                ?: return createReject(chordIssue(request.keys))
            if (!validOptionalLabel(request.label)) {
                return createReject(LayoutV3EditorIssue.INVALID_LABEL)
            }
            canonicalActions += request.label to canonical
        }
        if (runCatching { LayoutV3Geometry.rebase(variant.canvas, rect) }.isFailure) {
            return createReject(LayoutV3EditorIssue.VALIDATION_FAILED)
        }

        val elementId = canonicalUuid()
            ?: return createReject(LayoutV3EditorIssue.INVALID_IDENTITY)
        val identities = mutableSetOf(elementId)
        val createdActions = ArrayList<RadialAction>(canonicalActions.size)
        canonicalActions.forEachIndexed { order, (actionLabel, chord) ->
            val actionId = generateRadialActionId(identities)
                ?: return createReject(LayoutV3EditorIssue.ID_GENERATION_FAILED)
            identities += actionId
            createdActions += RadialAction(actionId, order, chord, actionLabel)
        }
        val element = runCatching {
            newElement(
                variant,
                elementId,
                ControlKind.RADIAL,
                rect,
                RadialPayload(resolvedLabel, createdActions),
            )
        }.getOrNull() ?: return createReject(LayoutV3EditorIssue.VALIDATION_FAILED)
        return applyCreatedElement(variant, element, createdActions.map { it.actionId })
    }

    /**
     * FROZEN v3 atomic batch seam. The caller supplies a set; canonical ordering,
     * identity, geometry, z-order, and ordinal advancement are all owned here.
     */
    fun addKeyboardKeys(keys: Set<InputCode>): LayoutV3EditResult {
        val active = document ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val identity = state.draft?.identity ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val canonical = runCatching { LayoutV3KeyboardBatchIdentity.canonicalKeys(keys) }
            .getOrNull() ?: return reject(
            if (keys.isEmpty()) LayoutV3EditorIssue.EMPTY_KEY_SET else LayoutV3EditorIssue.LIMIT_EXCEEDED,
        )
        if (variant.elements.size + canonical.size > MAX_ELEMENTS) {
            return reject(LayoutV3EditorIssue.LIMIT_EXCEEDED)
        }
        val firstZ = (variant.elements.maxOfOrNull { it.zOrder } ?: -1).toLong() + 1L
        if (firstZ < 0L || firstZ + canonical.lastIndex > MAX_Z_ORDER) {
            return reject(LayoutV3EditorIssue.INVALID_Z_ORDER)
        }
        val ordinal = active.nextKeyboardBatchOrdinal
        if (ordinal == Long.MAX_VALUE) return reject(LayoutV3EditorIssue.LIMIT_EXCEEDED)
        val verticalOffset = LayoutV3Geometry.roundHalfUp(
            variant.canvas.height.toLong() - KEYBOARD_SIZE,
            2L,
        )
        val anchored = AnchoredRect(0, verticalOffset, KEYBOARD_SIZE, KEYBOARD_SIZE)
        val created = canonical.mapIndexed { index, inputCode ->
            TouchLayoutV3Element(
                elementId = LayoutV3KeyboardBatchIdentity.elementId(active.layoutId, ordinal, inputCode),
                kind = ControlKind.KEYBOARD,
                rect = anchored,
                anchorX = HorizontalAnchor.CENTER,
                anchorY = VerticalAnchor.BOTTOM,
                zOrder = (firstZ + index).toInt(),
                enabled = true,
                hidden = false,
                payload = KeyboardPayload(
                    inputCode,
                    Appearance(
                        label = inputCode.code.toString(),
                        description = "",
                        shape = "circle",
                        showPhysicalKeyNames = false,
                    ),
                    Trigger.HOLD,
                    null,
                ),
                sourceReference = null,
            )
        }
        if ((variant.elements.asSequence().map { it.elementId } + created.asSequence().map { it.elementId })
                .toSet().size != variant.elements.size + created.size
        ) {
            return reject(LayoutV3EditorIssue.INVALID_IDENTITY)
        }
        val nextVariants = active.variants.map {
            if (it.variantId == identity.variantId) it.copy(elements = it.elements + created) else it
        }
        return replaceDocument(
            active.copy(
                nextKeyboardBatchOrdinal = ordinal + 1L,
                variants = nextVariants,
                contentHash = "",
            ),
            identity,
        )
    }

    @Synchronized
    fun flushJournal(): LayoutV3JournalWriteResult {
        journalWrite?.cancel(false)
        val active = document ?: return LayoutV3JournalWriteResult.INVALID
        val identity = state.draft?.identity ?: return LayoutV3JournalWriteResult.INVALID
        publish(state.copy(recoveryProtection = LayoutV3RecoveryProtection.PENDING))
        val raw = draftRaw(active) ?: return journalFailure()
        return when (val result = journal.write(identity, raw)) {
            LayoutV3JournalWriteResult.SAVED -> {
                publish(state.copy(
                    recoveryProtection = LayoutV3RecoveryProtection.SAVED,
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
    fun checkpointAndRelease(expectedDraftId: String): LayoutV3EditorHandoffResult {
        if (closed) {
            return LayoutV3EditorHandoffResult.Rejected(LayoutV3EditorHandoffIssue.CLOSED)
        }
        val activeDraftId = state.draft?.identity?.layoutId
            ?: return LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.NO_ACTIVE_DRAFT,
            )
        if (activeDraftId != expectedDraftId) {
            return LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.DRAFT_ID_MISMATCH,
            )
        }
        if (
            state.recoveryProtection != LayoutV3RecoveryProtection.SAVED &&
            flushJournal() != LayoutV3JournalWriteResult.SAVED
        ) {
            return LayoutV3EditorHandoffResult.Rejected(
                LayoutV3EditorHandoffIssue.CHECKPOINT_FAILED,
            )
        }
        releaseInMemoryDraft()
        return LayoutV3EditorHandoffResult.LaunchReady(activeDraftId)
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
                issue = if (ready) null else LayoutV3EditorIssue.VALIDATION_FAILED,
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

    fun saveDraft(): LayoutV3SaveResult {
        val active = document ?: return saveRejected(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val identity = state.draft?.identity ?: return saveRejected(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        publish(state.copy(phase = LayoutV3EditorPhase.SAVING, saving = true, issue = null))
        val raw = catalogRaw(active) ?: return saveRejected(LayoutV3EditorIssue.VALIDATION_FAILED)
        val descriptor = descriptor(active)
        val result = generations.commit(descriptor, raw) {
            registerCommitted(
                LayoutV3RegisteredGeneration(
                    it,
                    LayoutV3LocalOrigin.LOCAL_COPY,
                    LayoutV3WorkspaceState.DRAFT,
                    raw,
                ),
            )
        }
        if (result.code != LayoutV3GenerationWriteCode.SAVED) {
            return saveRejected(result.code.toEditorIssue())
        }
        if (!journal.discard(identity.layoutId)) {
            return saveRejected(LayoutV3EditorIssue.JOURNAL_WRITE_FAILED)
        }
        publish(state.copy(
            phase = LayoutV3EditorPhase.SAVED,
            dirty = false,
            saving = false,
            issue = null,
            candidateReady = true,
            recoveryProtection = LayoutV3RecoveryProtection.NOT_REQUIRED,
            recoverableDrafts = journal.summaries(),
        ))
        return LayoutV3SaveResult.Saved(identity.layoutId, identity.revision, identity.variantId)
    }

    fun exportCommittedArtifact(layoutId: String, revision: Long): LayoutV3ExportResult =
        generations.export(layoutId, revision)
            ?.let { LayoutV3ExportResult.Ready(LayoutV3ExportArtifact(it)) }
            ?: LayoutV3ExportResult.ContentNotReady

    fun discardDraft(): Boolean {
        journalWrite?.cancel(false)
        val draftId = state.draft?.identity?.layoutId
        val discarded = draftId == null || journal.discard(draftId)
        document = null
        candidateHash = null
        publish(LayoutV3EditorState(recoverableDrafts = journal.summaries()))
        return discarded
    }

    @Synchronized
    override fun close() {
        if (closed) return
        if (state.dirty) flushJournal()
        closed = true
        sessionGeneration++
        gestureTokens.clear()
        journalExecutor.shutdown()
    }

    private fun copyFromSource(
        source: LayoutV3CreatorSource,
        variantId: String,
        expectedOrigin: LayoutV3DraftOrigin,
    ): LayoutV3EditResult {
        if (
            source.origin != expectedOrigin ||
            source.availability != true
        ) return reject(LayoutV3EditorIssue.SOURCE_NOT_READY)
        if (
            expectedOrigin == LayoutV3DraftOrigin.PACKAGED_TEMPLATE &&
            source.workspace != LayoutV3WorkspaceState.NONE
        ) return reject(LayoutV3EditorIssue.SOURCE_NOT_READY)
        if (source.descriptor.publicationStatus == "retired") {
            return reject(LayoutV3EditorIssue.RETIRED)
        }
        val selected = source.content.document.variants.firstOrNull { it.variantId == variantId }
            ?: return reject(LayoutV3EditorIssue.VARIANT_NOT_FOUND)
        val layoutId = canonicalUuid() ?: return reject(LayoutV3EditorIssue.INVALID_IDENTITY)
        val newVariantId = canonicalUuid() ?: return reject(LayoutV3EditorIssue.INVALID_IDENTITY)
        val copied = selected.copy(variantId = newVariantId)
        val document = source.content.document.copy(
            layoutId = layoutId,
            revision = 1,
            variants = listOf(copied),
            contentHash = "",
        )
        return activate(
            document,
            LayoutV3DraftIdentity(
                layoutId, 1, newVariantId, expectedOrigin,
                source.descriptor.layoutId, source.descriptor.revision,
            ),
        )
    }

    private fun activate(
        value: TouchLayoutV3Document,
        identity: LayoutV3DraftIdentity,
        dirty: Boolean = true,
        recoveryAlreadySaved: Boolean = false,
    ): LayoutV3EditResult {
        sessionGeneration++
        gestureTokens.clear()
        document = value
        candidateHash = draftRaw(value)?.let { TouchLayoutV3Codec.decodeDraft(it).contentHash }
        publish(LayoutV3EditorState(
            phase = LayoutV3EditorPhase.EDITING,
            draft = value.toEditorDraft(identity),
            dirty = dirty,
            candidateReady = catalogRaw(value) != null,
            recoveryProtection = if (recoveryAlreadySaved) {
                LayoutV3RecoveryProtection.SAVED
            } else {
                LayoutV3RecoveryProtection.PENDING
            },
            recoverableDrafts = journal.summaries(),
        ))
        if (!recoveryAlreadySaved) scheduleJournal()
        return LayoutV3EditResult.Applied
    }

    private fun updateElement(
        elementId: String,
        transform: (TouchLayoutV3Element) -> TouchLayoutV3Element?,
    ): LayoutV3EditResult {
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val index = variant.elements.indexOfFirst { it.elementId == elementId }
        if (index < 0) return reject(LayoutV3EditorIssue.UNKNOWN_ELEMENT, elementId)
        val current = variant.elements[index]
        if (!current.editable()) return reject(LayoutV3EditorIssue.READ_ONLY_KIND, elementId)
        val updated = transform(current)
            ?: return reject(LayoutV3EditorIssue.INVALID_PAYLOAD, elementId)
        val next = variant.elements.toMutableList().also { it[index] = updated }
        return replaceElements(next)
    }

    private fun updateElementAndPersist(
        elementId: String,
        transform: (TouchLayoutV3Element) -> TouchLayoutV3Element?,
    ): LayoutV3EditResult {
        val active = document ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val identity = state.draft?.identity ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val index = variant.elements.indexOfFirst { it.elementId == elementId }
        if (index < 0) return reject(LayoutV3EditorIssue.UNKNOWN_ELEMENT, elementId)
        val current = variant.elements[index]
        if (!current.editable()) return reject(LayoutV3EditorIssue.READ_ONLY_KIND, elementId)
        val updated = transform(current)
            ?: return reject(LayoutV3EditorIssue.INVALID_PAYLOAD, elementId)
        val elements = variant.elements.toMutableList().also { it[index] = updated }
            .sortedWith(ELEMENT_ORDER)
        val variants = active.variants.map {
            if (it.variantId == identity.variantId) it.copy(elements = elements) else it
        }
        return replaceDocument(active.copy(variants = variants, contentHash = ""), identity)
    }

    private fun radialElement(elementId: String): TouchLayoutV3Element? =
        selectedVariant()?.elements?.firstOrNull { it.elementId == elementId && it.kind == ControlKind.RADIAL }

    private fun radialTargetIssue(elementId: String): LayoutV3EditorIssue {
        val target = selectedVariant()?.elements?.firstOrNull { it.elementId == elementId }
        return if (target == null) LayoutV3EditorIssue.UNKNOWN_ELEMENT else LayoutV3EditorIssue.WRONG_KIND
    }

    private fun radialActionTargetIssue(
        elementId: String,
        actionId: String,
    ): LayoutV3EditorIssue? {
        val target = selectedVariant()?.elements?.firstOrNull { it.elementId == elementId }
            ?: return LayoutV3EditorIssue.UNKNOWN_ELEMENT
        if (target.kind != ControlKind.RADIAL) return LayoutV3EditorIssue.WRONG_KIND
        val payload = target.payload as RadialPayload
        return if (payload.actions.none { it.actionId == actionId }) {
            LayoutV3EditorIssue.UNKNOWN_ACTION
        } else {
            null
        }
    }

    private fun newElement(
        variant: TouchLayoutV3Variant,
        elementId: String,
        kind: ControlKind,
        rect: IntRect,
        payload: ControlPayload,
    ): TouchLayoutV3Element {
        val nextZ = (variant.elements.maxOfOrNull { it.zOrder } ?: -1) + 1
        val rebased = LayoutV3Geometry.rebase(variant.canvas, rect)
        return TouchLayoutV3Element(
            elementId, kind, rebased.second, rebased.first.first, rebased.first.second, nextZ,
            enabled = true, hidden = false, payload, null,
        )
    }

    private fun applyCreatedElement(
        variant: TouchLayoutV3Variant,
        element: TouchLayoutV3Element,
        actionIds: List<String> = emptyList(),
    ): LayoutV3ElementCreateResult {
        return when (replaceElements(variant.elements + element)) {
            LayoutV3EditResult.Applied -> {
                val readback = state.draft?.elements?.firstOrNull { it.elementId == element.elementId }
                    ?: return createReject(LayoutV3EditorIssue.VALIDATION_FAILED)
                LayoutV3ElementCreateResult.Created(element.elementId, actionIds, readback)
            }
            is LayoutV3EditResult.Rejected -> createReject(LayoutV3EditorIssue.VALIDATION_FAILED)
        }
    }

    private fun createReject(issue: LayoutV3EditorIssue): LayoutV3ElementCreateResult {
        publish(state.copy(issue = issue))
        return LayoutV3ElementCreateResult.Rejected(issue)
    }

    private fun updateRadialAction(
        elementId: String,
        actionId: String,
        transform: (RadialAction) -> RadialAction,
    ): LayoutV3RadialEditResult {
        val current = radialElement(elementId) ?: return radialReject(radialTargetIssue(elementId))
        val payload = current.payload as RadialPayload
        if (payload.actions.none { it.actionId == actionId }) {
            return radialReject(LayoutV3EditorIssue.UNKNOWN_ACTION)
        }
        return applyRadial(
            elementId,
            current,
            payload.copy(actions = payload.actions.map { if (it.actionId == actionId) transform(it) else it }),
        )
    }

    private fun applyRadial(
        elementId: String,
        current: TouchLayoutV3Element,
        payload: RadialPayload,
        createdActionId: String? = null,
    ): LayoutV3RadialEditResult {
        val result = updateElementAndPersist(elementId) { current.copy(payload = payload) }
        if (result is LayoutV3EditResult.Rejected) return radialReject(result.issue)
        return LayoutV3RadialEditResult.Applied(
            createdActionId,
            LayoutV3EditableProperties.Radial(payload.label, payload.actions),
        )
    }

    private fun generateRadialActionId(existing: Set<String>): String? {
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = canonicalUuid()
            if (candidate != null && candidate !in existing) return candidate
        }
        return null
    }

    private fun radialReject(issue: LayoutV3EditorIssue): LayoutV3RadialEditResult {
        publish(state.copy(issue = issue))
        return LayoutV3RadialEditResult.Rejected(issue)
    }

    private fun updateResolvedRect(
        elementId: String,
        transform: (IntRect) -> IntRect,
    ): LayoutV3EditResult {
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        return updateElement(elementId) { current ->
            val resolved = runCatching {
                transform(LayoutV3Geometry.resolve(variant.canvas, current))
            }.getOrNull() ?: return@updateElement null
            val rebased = runCatching { LayoutV3Geometry.rebase(variant.canvas, resolved) }.getOrNull()
                ?: return@updateElement null
            current.copy(
                rect = rebased.second,
                anchorX = rebased.first.first,
                anchorY = rebased.first.second,
            )
        }
    }

    private fun resizeResolvedRect(
        elementId: String,
        targetRect: IntRect,
    ): LayoutV3EditResult {
        val variant = selectedVariant() ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val current = variant.elements.firstOrNull { it.elementId == elementId }
            ?: return reject(LayoutV3EditorIssue.UNKNOWN_ELEMENT, elementId)
        if (!current.editable()) return reject(LayoutV3EditorIssue.READ_ONLY_KIND, elementId)
        return when (
            val decision = LayoutV3ResizePolicy.constrain(
                current.toEditorElement(variant.canvas),
                targetRect,
            )
        ) {
            is LayoutV3ResizeDecision.Ready ->
                updateResolvedRect(elementId) { decision.rect }
            is LayoutV3ResizeDecision.Rejected ->
                reject(decision.issue, elementId)
        }
    }

    private fun replaceElements(elements: List<TouchLayoutV3Element>): LayoutV3EditResult {
        val active = document ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val identity = state.draft?.identity ?: return reject(LayoutV3EditorIssue.NO_ACTIVE_DRAFT)
        val variants = active.variants.map {
            if (it.variantId == identity.variantId) it.copy(elements = elements.sortedWith(ELEMENT_ORDER))
            else it
        }
        return replaceDocument(active.copy(variants = variants, contentHash = ""), identity)
    }

    private fun replaceDocument(
        candidate: TouchLayoutV3Document,
        identity: LayoutV3DraftIdentity,
    ): LayoutV3EditResult {
        if (draftRaw(candidate) == null) return reject(LayoutV3EditorIssue.VALIDATION_FAILED)
        document = candidate
        candidateHash = draftRaw(candidate)?.let { TouchLayoutV3Codec.decodeDraft(it).contentHash }
        publish(state.copy(
            phase = LayoutV3EditorPhase.EDITING,
            draft = candidate.toEditorDraft(identity),
            dirty = true,
            issue = null,
            candidateReady = catalogRaw(candidate) != null,
            recoveryProtection = LayoutV3RecoveryProtection.PENDING,
        ))
        scheduleJournal()
        return LayoutV3EditResult.Applied
    }

    private fun selectedVariant(): TouchLayoutV3Variant? {
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
        publish(LayoutV3EditorState(recoverableDrafts = journal.summaries()))
    }
    private fun draftRaw(value: TouchLayoutV3Document): ByteArray? = runCatching {
        val raw = TouchLayoutV3Encoder.encode(value)
        TouchLayoutV3Codec.decodeDraft(raw)
        raw
    }.getOrNull()
    private fun catalogRaw(value: TouchLayoutV3Document): ByteArray? = runCatching {
        val raw = TouchLayoutV3Encoder.encode(value)
        val result = TouchLayoutV3ContentVerifier.verify(raw)
        require(result is LayoutV3ContentVerificationResult.Verified)
        raw
    }.getOrNull()
    private fun descriptor(value: TouchLayoutV3Document) = LayoutDescriptorV1(
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
    private fun TouchLayoutV3Document.toEditorDraft(identity: LayoutV3DraftIdentity):
        LayoutV3EditorDraft {
        val variant = variants.first { it.variantId == identity.variantId }
        return LayoutV3EditorDraft(
            identity, displayName, variant.canvas, variant.deviceClasses, variant.orientations,
            variant.recommendation, opacityPermille, variant.elements.map { it.toEditorElement(variant.canvas) },
        )
    }
    private fun reject(issue: LayoutV3EditorIssue, elementId: String? = null): LayoutV3EditResult {
        publish(state.copy(issue = issue))
        return LayoutV3EditResult.Rejected(issue, elementId)
    }
    private fun journalFailure(): LayoutV3JournalWriteResult {
        publish(state.copy(
            recoveryProtection = LayoutV3RecoveryProtection.NOT_SAVED,
            issue = LayoutV3EditorIssue.JOURNAL_WRITE_FAILED,
        ))
        return LayoutV3JournalWriteResult.WRITE_FAILED
    }
    private fun saveRejected(issue: LayoutV3EditorIssue): LayoutV3SaveResult {
        publish(state.copy(
            phase = LayoutV3EditorPhase.FAILED,
            saving = false,
            issue = issue,
        ))
        return LayoutV3SaveResult.Rejected(issue)
    }
    private fun canonicalUuid(): String? = runCatching { uuid().lowercase() }.getOrNull()?.takeIf {
        LayoutContractV1Validator.normalizeUuid(it) == it
    }
    private fun publish(next: LayoutV3EditorState) {
        state = next
        onStateChanged(next)
    }

    private companion object {
        const val JOURNAL_DEBOUNCE_MILLIS = 350L
        const val MIN_OPACITY_PERMILLE = 0
        const val MAX_OPACITY_PERMILLE = 1000
        val EDITABLE_KINDS = setOf(
            ControlKind.KEYBOARD, ControlKind.MOUSE, ControlKind.ANALOG,
            ControlKind.DPAD, ControlKind.COMBO, ControlKind.RADIAL, ControlKind.SOFT_KEYBOARD,
        )
        const val KEYBOARD_SIZE = 96
        const val MAX_ELEMENTS = 512
        const val MIN_RADIAL_ACTIONS = 2
        const val MAX_RADIAL_ACTIONS = 16
        const val MAX_ID_ATTEMPTS = 3
        const val MAX_Z_ORDER = 32767L
        const val DEFAULT_COMBO_LABEL = "Combo"
        const val DEFAULT_RADIAL_LABEL = "Radial"
        val ELEMENT_ORDER = compareBy<TouchLayoutV3Element>({ it.zOrder }, { it.elementId })
    }
}

private fun TouchLayoutV3Element.editable(): Boolean =
    kind in setOf(
        ControlKind.KEYBOARD, ControlKind.MOUSE, ControlKind.ANALOG,
        ControlKind.DPAD, ControlKind.COMBO, ControlKind.RADIAL, ControlKind.SOFT_KEYBOARD,
    )

private fun LayoutV3EditableProperties.toPayload(kind: ControlKind): ControlPayload? = when (this) {
    is LayoutV3EditableProperties.Keyboard -> takeIf { kind == ControlKind.KEYBOARD }?.let {
        KeyboardPayload(inputCode, appearance, trigger, timedHoldMs)
    }
    is LayoutV3EditableProperties.Mouse -> takeIf { kind == ControlKind.MOUSE }?.let {
        MousePayload(button, appearance, trigger, timedHoldMs)
    }
    is LayoutV3EditableProperties.Analog -> takeIf { kind == ControlKind.ANALOG }?.let {
        DirectionalPayload(kind, up, down, left, right, press, diagonalPolicy)
    }
    is LayoutV3EditableProperties.Dpad -> takeIf { kind == ControlKind.DPAD }?.let {
        DirectionalPayload(kind, up, down, left, right, press, diagonalPolicy)
    }
    LayoutV3EditableProperties.SoftKeyboard ->
        SoftKeyboardPayload.takeIf { kind == ControlKind.SOFT_KEYBOARD }
    is LayoutV3EditableProperties.Combo,
    is LayoutV3EditableProperties.Radial -> null
}

private fun canonicalChord(keys: List<InputCode>): List<InputCode>? {
    if (keys.isEmpty() || keys.size > 16 || keys.toSet().size != keys.size) return null
    if (keys.any { it.code !in 0..65535 }) return null
    return keys.sortedWith(compareBy<InputCode>({ it.namespace.ordinal }, { it.code }))
}

private fun validateAndCanonicalizeChord(keys: List<InputCode>): List<InputCode>? =
    canonicalChord(keys)

private fun chordIssue(keys: List<InputCode>): LayoutV3EditorIssue = when {
    keys.isEmpty() -> LayoutV3EditorIssue.EMPTY_CHORD
    keys.size > 16 -> LayoutV3EditorIssue.TOO_MANY_KEYS
    keys.any { it.code !in 0..65535 } -> LayoutV3EditorIssue.UNSUPPORTED_INPUT_CODE
    keys.toSet().size != keys.size -> LayoutV3EditorIssue.DUPLICATE_KEY
    else -> LayoutV3EditorIssue.INVALID_PAYLOAD
}

private fun validOptionalLabel(label: String?): Boolean =
    label == null || (label.length in 1..32 && label.none { it.code < 0x20 || it.code in 0x7f..0x9f })

private fun validAppearanceText(value: String, maxLength: Int): Boolean =
    value.length <= maxLength && value.none { it.code < 0x20 || it.code in 0x7f..0x9f }

private fun LayoutV3GenerationWriteCode.toEditorIssue() = when (this) {
    LayoutV3GenerationWriteCode.SAVED -> error("not an error")
    LayoutV3GenerationWriteCode.INVALID_DESCRIPTOR -> LayoutV3EditorIssue.INVALID_IDENTITY
    LayoutV3GenerationWriteCode.CONTENT_REJECTED,
    LayoutV3GenerationWriteCode.ALIGNMENT_INVALID -> LayoutV3EditorIssue.VALIDATION_FAILED
    LayoutV3GenerationWriteCode.WRITE_FAILED -> LayoutV3EditorIssue.WRITE_FAILED
    LayoutV3GenerationWriteCode.READBACK_FAILED -> LayoutV3EditorIssue.READBACK_FAILED
    LayoutV3GenerationWriteCode.REGISTRATION_FAILED -> LayoutV3EditorIssue.REGISTRATION_FAILED
    LayoutV3GenerationWriteCode.ROLLBACK_FAILED -> LayoutV3EditorIssue.ROLLBACK_FAILED
}
