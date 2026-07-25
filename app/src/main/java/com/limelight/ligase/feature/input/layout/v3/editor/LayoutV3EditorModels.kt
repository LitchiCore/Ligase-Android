package com.limelight.ligase.feature.input.layout.v3.editor

import com.limelight.ligase.feature.input.layout.v3.domain.*

enum class LayoutV3EditorPhase { IDLE, EDITING, SAVING, SAVED, FAILED }
enum class LayoutV3DraftOrigin { BLANK, PACKAGED_TEMPLATE, LOCAL_COPY }
enum class LayoutV3ElementCapability {
    SELECT, MOVE, RESIZE, DELETE, EDIT_TYPED_PAYLOAD, CHANGE_Z_ORDER, INSPECT_ONLY,
}
enum class LayoutV3ReadonlyReason { UNSUPPORTED_EDITOR_KIND }
enum class LayoutV3RuntimeCompatibility { UNVERIFIED_NO_VIDEO_VIEWPORT }
enum class LayoutV3RecoveryProtection { NOT_REQUIRED, PENDING, SAVED, NOT_SAVED }
enum class LayoutV3RecoveryIssue { JOURNAL_WRITE_FAILED, QUARANTINED_CORRUPT, CLOCK_INVALID }

data class LayoutV3DraftIdentity(
    val layoutId: String,
    val revision: Long,
    val variantId: String,
    val origin: LayoutV3DraftOrigin,
    val sourceLayoutId: String? = null,
    val sourceRevision: Long? = null,
)

sealed interface LayoutV3EditableProperties {
    data class Keyboard(
        val inputCode: InputCode,
        val appearance: Appearance,
        val trigger: Trigger,
        val timedHoldMs: Int?,
    ) : LayoutV3EditableProperties
    data class Mouse(
        val button: String,
        val appearance: Appearance,
        val trigger: Trigger,
        val timedHoldMs: Int?,
    ) : LayoutV3EditableProperties
    data class Analog(
        val up: List<InputCode>,
        val down: List<InputCode>,
        val left: List<InputCode>,
        val right: List<InputCode>,
        val press: List<InputCode>?,
        val diagonalPolicy: String,
    ) : LayoutV3EditableProperties
    data class Dpad(
        val up: List<InputCode>,
        val down: List<InputCode>,
        val left: List<InputCode>,
        val right: List<InputCode>,
        val press: List<InputCode>?,
        val diagonalPolicy: String,
    ) : LayoutV3EditableProperties
    data object SoftKeyboard : LayoutV3EditableProperties
}

data class LayoutV3InspectOnlySummary(
    val kind: ControlKind,
    val itemCount: Int? = null,
    val label: String? = null,
    val readonlyReason: LayoutV3ReadonlyReason = LayoutV3ReadonlyReason.UNSUPPORTED_EDITOR_KIND,
)

data class LayoutV3EditorElement(
    val elementId: String,
    val kind: ControlKind,
    val rect: AnchoredRect,
    val resolvedRect: IntRect,
    val anchorX: HorizontalAnchor,
    val anchorY: VerticalAnchor,
    val zOrder: Int,
    val enabled: Boolean,
    val hidden: Boolean,
    val opacityPermille: Int,
    val editableProperties: LayoutV3EditableProperties?,
    val inspectOnlySummary: LayoutV3InspectOnlySummary?,
    val capabilities: Set<LayoutV3ElementCapability>,
)

data class LayoutV3EditorDraft(
    val identity: LayoutV3DraftIdentity,
    val displayName: String,
    val canvas: IntSize,
    val deviceClasses: List<DeviceClass>,
    val orientations: List<LayoutOrientation>,
    val recommendation: LayoutRecommendation,
    val elements: List<LayoutV3EditorElement>,
)

data class RecoverableDraftSummary(
    val draftId: String,
    val displayName: String,
    val sourceKind: LayoutV3DraftOrigin,
    val updatedAtEpochMillis: Long,
    val issue: LayoutV3RecoveryIssue? = null,
) {
    override fun toString(): String =
        "RecoverableDraftSummary(draftId=redacted,displayName=redacted," +
            "sourceKind=$sourceKind,updatedAt=redacted,issue=$issue)"
}

data class LayoutV3EditorState(
    val phase: LayoutV3EditorPhase = LayoutV3EditorPhase.IDLE,
    val draft: LayoutV3EditorDraft? = null,
    val selectedElementId: String? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val issue: LayoutV3EditorIssue? = null,
    val candidateReady: Boolean = false,
    val recoveryProtection: LayoutV3RecoveryProtection = LayoutV3RecoveryProtection.NOT_REQUIRED,
    val recoverableDrafts: List<RecoverableDraftSummary> = emptyList(),
    val runtimeCompatibility: LayoutV3RuntimeCompatibility =
        LayoutV3RuntimeCompatibility.UNVERIFIED_NO_VIDEO_VIEWPORT,
)

enum class LayoutV3EditorIssue {
    NO_ACTIVE_DRAFT, INVALID_IDENTITY, SOURCE_NOT_READY, RETIRED, VARIANT_NOT_FOUND,
    UNKNOWN_ELEMENT, READ_ONLY_KIND, INVALID_RECT, OUT_OF_CANVAS, INVALID_ANCHOR,
    INVALID_Z_ORDER, COLLISION, INVALID_PAYLOAD, LIMIT_EXCEEDED, VALIDATION_FAILED,
    STALE_SOURCE_GENERATION, JOURNAL_WRITE_FAILED, WRITE_FAILED, READBACK_FAILED,
    REGISTRATION_FAILED, ROLLBACK_FAILED, CUTOVER_NOT_READY, STALE_GESTURE,
    EMPTY_KEY_SET, UNSUPPORTED_NAMESPACE, ID_EXHAUSTED, Z_ORDER_EXHAUSTED,
}

data class LayoutV3GestureCommitToken internal constructor(
    internal val value: String,
    val elementId: String,
) {
    override fun toString(): String = "LayoutV3GestureCommitToken(redacted)"
}

sealed interface LayoutV3GestureStartResult {
    data class Ready(val token: LayoutV3GestureCommitToken) : LayoutV3GestureStartResult
    data class Rejected(val issue: LayoutV3EditorIssue) : LayoutV3GestureStartResult
}

enum class LayoutV3EditorHandoffIssue {
    INVALID_DRAFT_ID,
    NO_ACTIVE_DRAFT,
    DRAFT_ID_MISMATCH,
    CHECKPOINT_FAILED,
    ALREADY_OWNED,
    MISSING,
    QUARANTINED,
    CLOSED,
}

sealed interface LayoutV3EditorHandoffResult {
    data class LaunchReady(val draftId: String) : LayoutV3EditorHandoffResult {
        override fun toString(): String = "LayoutV3EditorHandoffResult.LaunchReady(draftId=redacted)"
    }

    data class Rejected(val issue: LayoutV3EditorHandoffIssue) : LayoutV3EditorHandoffResult
}

enum class LayoutV3EditorExitCode { SAVED, DISCARDED, LEFT_RECOVERABLE, FAILED }

data class LayoutV3EditorExitResult(
    val code: LayoutV3EditorExitCode,
    val issue: LayoutV3EditorIssue? = null,
) {
    override fun toString(): String = "LayoutV3EditorExitResult(code=$code,issue=$issue)"
}

sealed interface LayoutV3EditResult {
    data object Applied : LayoutV3EditResult
    data class Rejected(val issue: LayoutV3EditorIssue, val elementId: String? = null) :
        LayoutV3EditResult
}

sealed interface LayoutV3SaveResult {
    data class Saved(val layoutId: String, val revision: Long, val variantId: String) :
        LayoutV3SaveResult
    data class Rejected(val issue: LayoutV3EditorIssue) : LayoutV3SaveResult
}

class LayoutV3ExportArtifact internal constructor(internal val bytes: ByteArray) {
    override fun toString(): String = "LayoutV3ExportArtifact(content=redacted)"
}

sealed interface LayoutV3ExportResult {
    data class Ready(val artifact: LayoutV3ExportArtifact) : LayoutV3ExportResult
    data object ContentNotReady : LayoutV3ExportResult
}

internal fun TouchLayoutV3Element.toEditorElement(canvas: IntSize): LayoutV3EditorElement {
    val editable = when (val value = payload) {
        is KeyboardPayload -> LayoutV3EditableProperties.Keyboard(
            value.inputCode, value.appearance, value.trigger, value.timedHoldMs,
        )
        is MousePayload -> LayoutV3EditableProperties.Mouse(
            value.button, value.appearance, value.trigger, value.timedHoldMs,
        )
        is DirectionalPayload -> when (kind) {
            ControlKind.ANALOG -> LayoutV3EditableProperties.Analog(
                value.up, value.down, value.left, value.right, value.press, value.diagonalPolicy,
            )
            ControlKind.DPAD -> LayoutV3EditableProperties.Dpad(
                value.up, value.down, value.left, value.right, value.press, value.diagonalPolicy,
            )
            else -> null
        }
        SoftKeyboardPayload -> LayoutV3EditableProperties.SoftKeyboard
        else -> null
    }
    val editableCapabilities = setOf(
        LayoutV3ElementCapability.SELECT,
        LayoutV3ElementCapability.MOVE,
        LayoutV3ElementCapability.RESIZE,
        LayoutV3ElementCapability.DELETE,
        LayoutV3ElementCapability.EDIT_TYPED_PAYLOAD,
        LayoutV3ElementCapability.CHANGE_Z_ORDER,
    )
    val summary = if (editable == null) {
        LayoutV3InspectOnlySummary(
            kind,
            itemCount = when (val value = payload) {
                is ChordPayload -> value.keys.size
                is RadialPayload -> value.actions.size
                else -> null
            },
            label = when (val value = payload) {
                is ChordPayload -> value.appearance.label
                is RadialPayload -> value.label
                is ScrollPayload -> value.appearance.label
                else -> null
            },
        )
    } else {
        null
    }
    return LayoutV3EditorElement(
        elementId, kind, rect, LayoutV3Geometry.resolve(canvas, this), anchorX, anchorY, zOrder, enabled, hidden,
        opacityPermille, editable, summary,
        if (editable == null) {
            setOf(LayoutV3ElementCapability.SELECT, LayoutV3ElementCapability.INSPECT_ONLY)
        } else {
            editableCapabilities
        },
    )
}
