package com.limelight.ligase.feature.input.layout.v2.editor

import com.limelight.ligase.feature.input.layout.v2.domain.*

enum class LayoutV2EditorPhase { IDLE, EDITING, SAVING, SAVED, FAILED }
enum class LayoutV2DraftOrigin { BLANK, PACKAGED_TEMPLATE, LOCAL_COPY }
enum class LayoutV2ElementCapability {
    SELECT, MOVE, RESIZE, DELETE, EDIT_TYPED_PAYLOAD, CHANGE_Z_ORDER, INSPECT_ONLY,
}
enum class LayoutV2ReadonlyReason { UNSUPPORTED_EDITOR_KIND }
enum class LayoutV2RuntimeCompatibility { UNVERIFIED_NO_VIDEO_VIEWPORT }
enum class LayoutV2RecoveryProtection { NOT_REQUIRED, PENDING, SAVED, NOT_SAVED }
enum class LayoutV2RecoveryIssue { JOURNAL_WRITE_FAILED, QUARANTINED_CORRUPT, CLOCK_INVALID }

data class LayoutV2DraftIdentity(
    val layoutId: String,
    val revision: Long,
    val variantId: String,
    val origin: LayoutV2DraftOrigin,
    val sourceLayoutId: String? = null,
    val sourceRevision: Long? = null,
)

sealed interface LayoutV2EditableProperties {
    data class Keyboard(
        val inputCode: InputCode,
        val appearance: Appearance,
        val trigger: Trigger,
        val timedHoldMs: Int?,
    ) : LayoutV2EditableProperties
    data class Mouse(
        val button: String,
        val appearance: Appearance,
        val trigger: Trigger,
        val timedHoldMs: Int?,
    ) : LayoutV2EditableProperties
    data class Analog(
        val up: List<InputCode>,
        val down: List<InputCode>,
        val left: List<InputCode>,
        val right: List<InputCode>,
        val press: List<InputCode>?,
        val diagonalPolicy: String,
    ) : LayoutV2EditableProperties
    data class Dpad(
        val up: List<InputCode>,
        val down: List<InputCode>,
        val left: List<InputCode>,
        val right: List<InputCode>,
        val press: List<InputCode>?,
        val diagonalPolicy: String,
    ) : LayoutV2EditableProperties
    data object SoftKeyboard : LayoutV2EditableProperties
}

data class LayoutV2InspectOnlySummary(
    val kind: ControlKind,
    val itemCount: Int? = null,
    val label: String? = null,
    val readonlyReason: LayoutV2ReadonlyReason = LayoutV2ReadonlyReason.UNSUPPORTED_EDITOR_KIND,
)

data class LayoutV2EditorElement(
    val elementId: String,
    val kind: ControlKind,
    val rect: IntRect,
    val horizontalAnchor: HorizontalAnchor,
    val verticalAnchor: VerticalAnchor,
    val zOrder: Int,
    val enabled: Boolean,
    val hidden: Boolean,
    val opacityPermille: Int,
    val editableProperties: LayoutV2EditableProperties?,
    val inspectOnlySummary: LayoutV2InspectOnlySummary?,
    val capabilities: Set<LayoutV2ElementCapability>,
)

data class LayoutV2EditorDraft(
    val identity: LayoutV2DraftIdentity,
    val displayName: String,
    val canvas: IntSize,
    val deviceClasses: List<DeviceClass>,
    val orientations: List<LayoutOrientation>,
    val recommendation: LayoutRecommendation,
    val elements: List<LayoutV2EditorElement>,
)

data class RecoverableDraftSummary(
    val draftId: String,
    val displayName: String,
    val sourceKind: LayoutV2DraftOrigin,
    val updatedAtEpochMillis: Long,
    val issue: LayoutV2RecoveryIssue? = null,
) {
    override fun toString(): String =
        "RecoverableDraftSummary(draftId=redacted,displayName=redacted," +
            "sourceKind=$sourceKind,updatedAt=redacted,issue=$issue)"
}

data class LayoutV2EditorState(
    val phase: LayoutV2EditorPhase = LayoutV2EditorPhase.IDLE,
    val draft: LayoutV2EditorDraft? = null,
    val selectedElementId: String? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val issue: LayoutV2EditorIssue? = null,
    val candidateReady: Boolean = false,
    val recoveryProtection: LayoutV2RecoveryProtection = LayoutV2RecoveryProtection.NOT_REQUIRED,
    val recoverableDrafts: List<RecoverableDraftSummary> = emptyList(),
    val runtimeCompatibility: LayoutV2RuntimeCompatibility =
        LayoutV2RuntimeCompatibility.UNVERIFIED_NO_VIDEO_VIEWPORT,
)

enum class LayoutV2EditorIssue {
    NO_ACTIVE_DRAFT, INVALID_IDENTITY, SOURCE_NOT_READY, RETIRED, VARIANT_NOT_FOUND,
    UNKNOWN_ELEMENT, READ_ONLY_KIND, INVALID_RECT, OUT_OF_CANVAS, INVALID_ANCHOR,
    INVALID_Z_ORDER, COLLISION, INVALID_PAYLOAD, LIMIT_EXCEEDED, VALIDATION_FAILED,
    STALE_SOURCE_GENERATION, JOURNAL_WRITE_FAILED, WRITE_FAILED, READBACK_FAILED,
    REGISTRATION_FAILED, ROLLBACK_FAILED,
}

sealed interface LayoutV2EditResult {
    data object Applied : LayoutV2EditResult
    data class Rejected(val issue: LayoutV2EditorIssue, val elementId: String? = null) :
        LayoutV2EditResult
}

sealed interface LayoutV2SaveResult {
    data class Saved(val layoutId: String, val revision: Long, val variantId: String) :
        LayoutV2SaveResult
    data class Rejected(val issue: LayoutV2EditorIssue) : LayoutV2SaveResult
}

class LayoutV2ExportArtifact internal constructor(internal val bytes: ByteArray) {
    override fun toString(): String = "LayoutV2ExportArtifact(content=redacted)"
}

sealed interface LayoutV2ExportResult {
    data class Ready(val artifact: LayoutV2ExportArtifact) : LayoutV2ExportResult
    data object ContentNotReady : LayoutV2ExportResult
}

internal fun TouchLayoutV2Element.toEditorElement(): LayoutV2EditorElement {
    val editable = when (val value = payload) {
        is KeyboardPayload -> LayoutV2EditableProperties.Keyboard(
            value.inputCode, value.appearance, value.trigger, value.timedHoldMs,
        )
        is MousePayload -> LayoutV2EditableProperties.Mouse(
            value.button, value.appearance, value.trigger, value.timedHoldMs,
        )
        is DirectionalPayload -> when (kind) {
            ControlKind.ANALOG -> LayoutV2EditableProperties.Analog(
                value.up, value.down, value.left, value.right, value.press, value.diagonalPolicy,
            )
            ControlKind.DPAD -> LayoutV2EditableProperties.Dpad(
                value.up, value.down, value.left, value.right, value.press, value.diagonalPolicy,
            )
            else -> null
        }
        SoftKeyboardPayload -> LayoutV2EditableProperties.SoftKeyboard
        else -> null
    }
    val editableCapabilities = setOf(
        LayoutV2ElementCapability.SELECT,
        LayoutV2ElementCapability.MOVE,
        LayoutV2ElementCapability.RESIZE,
        LayoutV2ElementCapability.DELETE,
        LayoutV2ElementCapability.EDIT_TYPED_PAYLOAD,
        LayoutV2ElementCapability.CHANGE_Z_ORDER,
    )
    val summary = if (editable == null) {
        LayoutV2InspectOnlySummary(
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
    return LayoutV2EditorElement(
        elementId, kind, rect, horizontalAnchor, verticalAnchor, zOrder, enabled, hidden,
        opacityPermille, editable, summary,
        if (editable == null) {
            setOf(LayoutV2ElementCapability.SELECT, LayoutV2ElementCapability.INSPECT_ONLY)
        } else {
            editableCapabilities
        },
    )
}
