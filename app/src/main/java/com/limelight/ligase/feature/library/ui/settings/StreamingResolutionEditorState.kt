package com.limelight.ligase.feature.library.ui.settings

import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.feature.stream.domain.DeviceStreamCapabilities
import java.util.Locale

internal enum class StreamingResolutionTarget {
    GLOBAL,
    APP,
}

internal data class StreamingResolutionEditorRequest(
    val target: StreamingResolutionTarget,
    val title: String,
    val hostKey: String,
    val baseRevision: Long,
    val initialResolution: LigaseResolutionDto,
    val appUuid: String? = null,
    val useGlobal: Boolean = false,
    val deviceCapabilities: DeviceStreamCapabilities =
        DeviceStreamCapabilities(1920, 1080, 60f, false),
) {
    val allowUseGlobal: Boolean
        get() = target == StreamingResolutionTarget.APP
}

internal data class StreamingResolutionDraft(
    val widthText: String,
    val heightText: String,
    val useGlobal: Boolean,
)

internal sealed interface StreamingResolutionDraftResult {
    data class Ready(val resolution: LigaseResolutionDto?) : StreamingResolutionDraftResult
    data object Invalid : StreamingResolutionDraftResult
}

internal fun parseStreamingResolutionDraft(
    request: StreamingResolutionEditorRequest,
    draft: StreamingResolutionDraft,
): StreamingResolutionDraftResult {
    if (request.allowUseGlobal && draft.useGlobal) {
        return StreamingResolutionDraftResult.Ready(null)
    }
    val width = draft.widthText.toIntOrNull()
    val height = draft.heightText.toIntOrNull()
    val resolution = if (width != null && height != null) {
        LigaseResolutionDto(width, height)
    } else {
        null
    }
    return if (resolution?.isValid() == true) {
        StreamingResolutionDraftResult.Ready(resolution)
    } else {
        StreamingResolutionDraftResult.Invalid
    }
}

internal enum class StreamingResolutionSubmissionDecision {
    READY,
    STALE_HOST,
    OFFLINE,
    PERMISSION_DENIED,
    CONTENT_UNAVAILABLE,
    REVISION_CONFLICT,
}

internal fun streamingResolutionSubmissionDecision(
    request: StreamingResolutionEditorRequest,
    selectedHostKey: String?,
    connectivity: LibraryConnectivity,
    accessMode: String?,
    currentRevision: Long?,
): StreamingResolutionSubmissionDecision {
    if (selectedHostKey?.normalizedKey() != request.hostKey.normalizedKey()) {
        return StreamingResolutionSubmissionDecision.STALE_HOST
    }
    if (connectivity != LibraryConnectivity.ONLINE) {
        return StreamingResolutionSubmissionDecision.OFFLINE
    }
    if (accessMode != "operate") {
        return StreamingResolutionSubmissionDecision.PERMISSION_DENIED
    }
    if (currentRevision == null) {
        return StreamingResolutionSubmissionDecision.CONTENT_UNAVAILABLE
    }
    if (currentRevision != request.baseRevision) {
        return StreamingResolutionSubmissionDecision.REVISION_CONFLICT
    }
    return StreamingResolutionSubmissionDecision.READY
}

private fun String.normalizedKey(): String = trim().lowercase(Locale.ROOT)
