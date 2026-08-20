package com.limelight.ligase.feature.stream.application

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.stream.infrastructure.LegacyGameStreamLauncher
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputLaunchDecision
import com.limelight.ligase.input.LigaseInputLaunchPolicy
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.input.LigaseCloudTouchMode
import com.limelight.ligase.feature.stream.domain.DeviceStreamCapabilities
import com.limelight.ligase.feature.stream.domain.StreamDisplayPolicy
import com.limelight.ligase.feature.stream.domain.StreamFrameRateMode
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.LibraryOperationGate
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import java.util.Locale

enum class StreamLaunchConfirmation {
    NONE,
    QUIT_RUNNING_GAME,
    VIRTUAL_DISPLAY_UNAVAILABLE,
}

enum class StreamLaunchBlockReason {
    OFFLINE,
    PERMISSION_DENIED,
    MISSING_APP_MAPPING,
    APP_IDENTITY_MISMATCH,
    GAMEPAD_DISCONNECTED,
    KEYBOARD_MOUSE_DISCONNECTED,
    V3_LAYOUT_RUNTIME_UNAVAILABLE,
    MANAGER_UNAVAILABLE,
}

sealed interface StreamLaunchPlanningResult {
    data class Ready(val plan: StreamLaunchPlan) : StreamLaunchPlanningResult
    data class Blocked(val reason: StreamLaunchBlockReason) : StreamLaunchPlanningResult
}

enum class StreamLaunchExecutionResult {
    STARTED,
    STALE_HOST,
    ALREADY_LAUNCHED,
    OFFLINE,
    PERMISSION_DENIED,
    MANAGER_UNAVAILABLE,
}

data class StreamLaunchRequest(
    val item: LigaseLibraryItem,
    val snapshot: LigaseSyncSnapshotDto,
    val connectivity: LibraryConnectivity,
    val inputMode: InputDeviceMode,
    val selectedGamepadKey: String?,
    val selectedKeyboardKey: String?,
    val selectedMouseKey: String?,
    val connectedInputDevices: List<LigaseInputDevice>,
    val overlayMode: LigaseTouchOverlayMode,
    val cloudTouchMode: LigaseCloudTouchMode = LigaseCloudTouchMode.SINGLE_TOUCH,
    val frameRateMode: StreamFrameRateMode = StreamFrameRateMode.FOLLOW_DISPLAY,
    val deviceCapabilities: DeviceStreamCapabilities =
        DeviceStreamCapabilities(1920, 1080, 60f, false),
    val preferVirtualDisplay: Boolean,
)

data class StreamLaunchPlan internal constructor(
    val host: ComputerDetails,
    val app: NvApp,
    val canonicalAppUuid: String,
    val numericAppId: Int,
    val input: LigaseInputLaunchDecision,
    val withVirtualDisplay: Boolean,
    val width: Int,
    val height: Int,
    val fps: Float,
    val hostHdrSupported: Boolean,
    val confirmation: StreamLaunchConfirmation,
    internal val ticket: Long,
)

/**
 * Pure launch planning plus a one-shot legacy launcher handoff.
 *
 * Names and numeric IDs are never used to infer identity. The canonical app
 * UUID selects Sync settings while numeric appId remains launch ABI only.
 */
class StreamLaunchCoordinator(
    private val currentHost: () -> ComputerDetails?,
    private val launcher: LegacyGameStreamLauncher,
) {
    private val lock = Any()
    private var nextTicket = 1L
    private var activeTicket: Long? = null

    fun plan(request: StreamLaunchRequest): StreamLaunchPlanningResult {
        val host = currentHost()
            ?: return StreamLaunchPlanningResult.Blocked(StreamLaunchBlockReason.OFFLINE)
        if (
            request.connectivity != LibraryConnectivity.ONLINE ||
            host.state != ComputerDetails.State.ONLINE
        ) {
            return StreamLaunchPlanningResult.Blocked(StreamLaunchBlockReason.OFFLINE)
        }
        if (!LibraryOperationGate.canOperate(request.connectivity, host.ligaseClientAccessMode)) {
            return StreamLaunchPlanningResult.Blocked(StreamLaunchBlockReason.PERMISSION_DENIED)
        }
        if (!launcher.isAvailable()) {
            return StreamLaunchPlanningResult.Blocked(StreamLaunchBlockReason.MANAGER_UNAVAILABLE)
        }
        val app = request.item.launchApp
            ?: return StreamLaunchPlanningResult.Blocked(
                StreamLaunchBlockReason.MISSING_APP_MAPPING,
            )
        val appUuid = request.item.hostAppUuid?.canonicalUuid()
            ?: return StreamLaunchPlanningResult.Blocked(
                StreamLaunchBlockReason.MISSING_APP_MAPPING,
            )
        if (app.appUUID?.canonicalUuid() != appUuid) {
            return StreamLaunchPlanningResult.Blocked(
                StreamLaunchBlockReason.APP_IDENTITY_MISMATCH,
            )
        }
        when (request.inputMode) {
            InputDeviceMode.GAMEPAD -> if (
                request.selectedGamepadKey == null ||
                request.connectedInputDevices.none {
                    it.category == LigaseInputCategory.GAMEPAD &&
                        it.stableKey == request.selectedGamepadKey
                }
            ) {
                return StreamLaunchPlanningResult.Blocked(
                    StreamLaunchBlockReason.GAMEPAD_DISCONNECTED,
                )
            }
            InputDeviceMode.KEYBOARD_MOUSE -> if (
                request.connectedInputDevices.none {
                    (
                        it.category == LigaseInputCategory.KEYBOARD &&
                            it.stableKey == request.selectedKeyboardKey
                        ) ||
                        (
                            it.category == LigaseInputCategory.MOUSE &&
                                it.stableKey == request.selectedMouseKey
                            )
                }
            ) {
                return StreamLaunchPlanningResult.Blocked(
                    StreamLaunchBlockReason.KEYBOARD_MOUSE_DISCONNECTED,
                )
            }
            InputDeviceMode.TOUCH -> Unit
        }
        val input = LigaseInputLaunchPolicy.resolve(
            mode = request.inputMode,
            overlayMode = request.overlayMode,
            cloudTouchMode = request.cloudTouchMode,
        ) ?: return StreamLaunchPlanningResult.Blocked(
            StreamLaunchBlockReason.V3_LAYOUT_RUNTIME_UNAVAILABLE,
        )
        val withVirtualDisplay = !request.item.isSystem && request.preferVirtualDisplay
        val confirmation = when {
            host.runningGameId != 0 && host.runningGameId != app.appId ->
                StreamLaunchConfirmation.QUIT_RUNNING_GAME
            withVirtualDisplay && !(host.vDisplaySupported && host.vDisplayDriverReady) ->
                StreamLaunchConfirmation.VIRTUAL_DISPLAY_UNAVAILABLE
            else -> StreamLaunchConfirmation.NONE
        }
        val resolution = request.snapshot.streaming.resolutionFor(appUuid)
        val ticket = synchronized(lock) {
            nextTicket++.also { activeTicket = it }
        }
        return StreamLaunchPlanningResult.Ready(
            StreamLaunchPlan(
                host = host,
                app = app,
                canonicalAppUuid = appUuid,
                numericAppId = app.appId,
                input = input,
                withVirtualDisplay = withVirtualDisplay,
                width = resolution.width,
                height = resolution.height,
                fps = StreamDisplayPolicy.launchFps(
                    request.frameRateMode,
                    request.deviceCapabilities,
                ),
                hostHdrSupported = request.snapshot.capabilities.hdrEncodingSupported,
                confirmation = confirmation,
                ticket = ticket,
            ),
        )
    }

    fun launch(plan: StreamLaunchPlan): StreamLaunchExecutionResult {
        val ownsTicket = synchronized(lock) {
            if (activeTicket != plan.ticket) {
                false
            } else {
                activeTicket = null
                true
            }
        }
        if (!ownsTicket) return StreamLaunchExecutionResult.ALREADY_LAUNCHED
        val liveHost = currentHost()
        if (!liveHost?.uuid.equals(plan.host.uuid, ignoreCase = true)) {
            return StreamLaunchExecutionResult.STALE_HOST
        }
        if (liveHost?.state != ComputerDetails.State.ONLINE) {
            return StreamLaunchExecutionResult.OFFLINE
        }
        if (liveHost.ligaseClientAccessMode != "operate") {
            return StreamLaunchExecutionResult.PERMISSION_DENIED
        }
        return if (launcher.launch(plan)) {
            StreamLaunchExecutionResult.STARTED
        } else {
            StreamLaunchExecutionResult.MANAGER_UNAVAILABLE
        }
    }

    private fun String.canonicalUuid(): String? {
        val normalized = trim().lowercase(Locale.ROOT)
        return normalized.takeIf(CANONICAL_UUID::matches)
    }

    private companion object {
        val CANONICAL_UUID =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
