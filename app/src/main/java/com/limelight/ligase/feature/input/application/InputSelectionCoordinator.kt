package com.limelight.ligase.feature.input.application

import android.content.Context
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.feature.input.infrastructure.LegacyInputSelectionPreferences
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputDeviceRepository
import com.limelight.ligase.input.LigaseTouchLayout
import com.limelight.ligase.input.LigaseTouchLayoutRepository
import com.limelight.ligase.input.LigaseTouchOverlayMode

data class InputSelectionState(
    val onboarding: Boolean,
    val selectedMode: InputDeviceMode?,
    val devices: List<LigaseInputDevice>,
    val selectedGamepadKey: String?,
    val selectedKeyboardKey: String?,
    val selectedMouseKey: String?,
    val touchLayouts: List<LigaseTouchLayout>,
    val selectedTouchLayoutId: String?,
    val overlayMode: LigaseTouchOverlayMode,
)

internal class InputDeviceSession(
    val start: () -> Unit,
    val stop: () -> Unit,
)

/**
 * Owns input selection state, existing v1 preference writes, and the bounded
 * Android input-device enumeration lifecycle.
 *
 * Touch layout bytes and global layout persistence remain owned by
 * [LigaseTouchLayoutRepository].
 */
class InputSelectionCoordinator private constructor(
    private val hasInputMode: () -> Boolean,
    private val readInputMode: () -> InputDeviceMode,
    private val writeInputMode: (InputDeviceMode) -> Unit,
    private val readSelectedDevice: (LigaseInputCategory) -> String?,
    private val writeSelectedDevice: (LigaseInputCategory, String) -> Unit,
    private val readOverlayMode: () -> LigaseTouchOverlayMode,
    private val writeOverlayMode: (LigaseTouchOverlayMode) -> Unit,
    private val loadLayouts: () -> List<LigaseTouchLayout>,
    private val initializeLayout: (List<LigaseTouchLayout>) -> String?,
    private val selectLayout: (String, List<LigaseTouchLayout>) -> Boolean,
    private val createDeviceSession: ((List<LigaseInputDevice>) -> Unit) -> InputDeviceSession,
    private val onStateChanged: (InputSelectionState) -> Unit,
) {
    constructor(
        context: Context,
        onStateChanged: (InputSelectionState) -> Unit,
    ) : this(
        preferences = LegacyInputSelectionPreferences(context),
        touchLayouts = LigaseTouchLayoutRepository(context),
        createDeviceSession = { callback ->
            val repository = LigaseInputDeviceRepository(context, callback)
            InputDeviceSession(repository::start, repository::stop)
        },
        onStateChanged = onStateChanged,
    )

    private constructor(
        preferences: LegacyInputSelectionPreferences,
        touchLayouts: LigaseTouchLayoutRepository,
        createDeviceSession: ((List<LigaseInputDevice>) -> Unit) -> InputDeviceSession,
        onStateChanged: (InputSelectionState) -> Unit,
    ) : this(
        hasInputMode = preferences::hasInputMode,
        readInputMode = preferences::inputMode,
        writeInputMode = preferences::setInputMode,
        readSelectedDevice = preferences::selectedDevice,
        writeSelectedDevice = preferences::setSelectedDevice,
        readOverlayMode = preferences::overlayMode,
        writeOverlayMode = preferences::setOverlayMode,
        loadLayouts = touchLayouts::layouts,
        initializeLayout = touchLayouts::initializeSelection,
        selectLayout = touchLayouts::select,
        createDeviceSession = createDeviceSession,
        onStateChanged = onStateChanged,
    )

    internal constructor(
        hasInputMode: () -> Boolean,
        readInputMode: () -> InputDeviceMode,
        writeInputMode: (InputDeviceMode) -> Unit,
        readSelectedDevice: (LigaseInputCategory) -> String?,
        writeSelectedDevice: (LigaseInputCategory, String) -> Unit,
        readOverlayMode: () -> LigaseTouchOverlayMode,
        writeOverlayMode: (LigaseTouchOverlayMode) -> Unit,
        loadLayouts: () -> List<LigaseTouchLayout>,
        initializeLayout: (List<LigaseTouchLayout>) -> String?,
        selectLayout: (String, List<LigaseTouchLayout>) -> Boolean,
        createDeviceSession: ((List<LigaseInputDevice>) -> Unit) -> InputDeviceSession,
        onStateChanged: (InputSelectionState) -> Unit,
        @Suppress("UNUSED_PARAMETER") testing: Unit = Unit,
    ) : this(
        hasInputMode,
        readInputMode,
        writeInputMode,
        readSelectedDevice,
        writeSelectedDevice,
        readOverlayMode,
        writeOverlayMode,
        loadLayouts,
        initializeLayout,
        selectLayout,
        createDeviceSession,
        onStateChanged,
    )

    var state: InputSelectionState = initialState()
        private set

    private var foregroundGeneration = 0L
    private var deviceSession: InputDeviceSession? = null

    init {
        onStateChanged(state)
    }

    fun start() {
        stop()
        val generation = ++foregroundGeneration
        val session = createDeviceSession { devices ->
            if (generation != foregroundGeneration || deviceSession == null) return@createDeviceSession
            update(state.copy(devices = devices.toList()))
        }
        deviceSession = session
        session.start()
    }

    fun stop() {
        foregroundGeneration++
        deviceSession?.stop?.invoke()
        deviceSession = null
    }

    fun selectMode(mode: InputDeviceMode) {
        if (!state.onboarding) writeInputMode(mode)
        update(state.copy(selectedMode = mode))
    }

    fun confirmSelection(): Boolean {
        val mode = state.selectedMode ?: return false
        writeInputMode(mode)
        update(state.copy(onboarding = false))
        return true
    }

    fun effectiveMode(): InputDeviceMode = state.selectedMode ?: readInputMode()

    fun selectDevice(category: LigaseInputCategory, stableKey: String) {
        writeSelectedDevice(category, stableKey)
        update(
            when (category) {
                LigaseInputCategory.GAMEPAD -> state.copy(selectedGamepadKey = stableKey)
                LigaseInputCategory.KEYBOARD -> state.copy(selectedKeyboardKey = stableKey)
                LigaseInputCategory.MOUSE -> state.copy(selectedMouseKey = stableKey)
            },
        )
    }

    fun refreshLayouts() {
        val layouts = loadLayouts()
        update(
            state.copy(
                touchLayouts = layouts,
                selectedTouchLayoutId = initializeLayout(layouts),
            ),
        )
    }

    fun selectTouchLayout(layoutId: String): Boolean {
        if (!selectLayout(layoutId, state.touchLayouts)) return false
        update(state.copy(selectedTouchLayoutId = layoutId))
        return true
    }

    fun selectOverlayMode(mode: LigaseTouchOverlayMode) {
        writeOverlayMode(mode)
        update(state.copy(overlayMode = mode))
    }

    private fun initialState(): InputSelectionState {
        val onboarding = !hasInputMode()
        val layouts = loadLayouts()
        return InputSelectionState(
            onboarding = onboarding,
            selectedMode = if (onboarding) null else readInputMode(),
            devices = emptyList(),
            selectedGamepadKey = readSelectedDevice(LigaseInputCategory.GAMEPAD),
            selectedKeyboardKey = readSelectedDevice(LigaseInputCategory.KEYBOARD),
            selectedMouseKey = readSelectedDevice(LigaseInputCategory.MOUSE),
            touchLayouts = layouts,
            selectedTouchLayoutId = initializeLayout(layouts),
            overlayMode = readOverlayMode(),
        )
    }

    private fun update(next: InputSelectionState) {
        state = next
        onStateChanged(next)
    }
}
