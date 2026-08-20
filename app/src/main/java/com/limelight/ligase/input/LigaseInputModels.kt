package com.limelight.ligase.input

enum class LigaseInputCategory(val storedValue: String) {
    GAMEPAD("gamepad"),
    KEYBOARD("keyboard"),
    MOUSE("mouse"),
}

enum class LigaseInputConnection {
    USB_OTG,
    EXTERNAL,
}

data class LigaseInputDevice(
    val stableKey: String,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val category: LigaseInputCategory,
    val name: String,
    val connection: LigaseInputConnection,
)

enum class LigaseTouchOverlayMode(val storedValue: String) {
    VIRTUAL_GAMEPAD("virtualGamepad"),
    CLOUD_CONTROLS("cloudControls"),
    HIDDEN("hidden");

    companion object {
        fun fromStoredValue(value: String?): LigaseTouchOverlayMode? =
            entries.firstOrNull { it.storedValue == value } ?: when (value) {
                "touchkitKeyboard" -> CLOUD_CONTROLS
                "gesturesOnly" -> HIDDEN
                else -> null
            }
    }
}

enum class LigaseCloudTouchMode(val storedValue: String) {
    SINGLE_TOUCH("singleTouch"),
    MULTI_TOUCH("multiTouch"),
    TRACKPAD("trackpad");

    companion object {
        fun fromStoredValue(value: String?): LigaseCloudTouchMode? =
            entries.firstOrNull { it.storedValue == value }
    }
}

data class LigaseInputProfile(
    val mode: com.limelight.ligase.InputDeviceMode,
    val overlayMode: LigaseTouchOverlayMode,
    val cloudTouchMode: LigaseCloudTouchMode,
)

data class LigaseEffectiveInputProfile(
    val profile: LigaseInputProfile,
    val source: Source,
    val writable: Boolean,
) {
    enum class Source { GLOBAL, GAME_OVERRIDE, OBSERVE_FORCED_HIDDEN }
}

object LigaseCanonicalGameUuid {
    private val pattern =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun parse(value: String?): String? = value?.takeIf(pattern::matches)
}

object LigaseInputProfilePolicy {
    fun resolve(
        global: LigaseInputProfile,
        gameOverride: LigaseInputProfile?,
        canOperate: Boolean,
    ): LigaseEffectiveInputProfile {
        val selected = gameOverride ?: global
        if (!canOperate) {
            return LigaseEffectiveInputProfile(
                profile = selected.copy(overlayMode = LigaseTouchOverlayMode.HIDDEN),
                source = LigaseEffectiveInputProfile.Source.OBSERVE_FORCED_HIDDEN,
                writable = false,
            )
        }
        return LigaseEffectiveInputProfile(
            profile = selected,
            source = if (gameOverride == null) {
                LigaseEffectiveInputProfile.Source.GLOBAL
            } else {
                LigaseEffectiveInputProfile.Source.GAME_OVERRIDE
            },
            writable = true,
        )
    }
}

enum class LigaseInputOverrideWriteResult {
    SAVED,
    CLEARED,
    INVALID_GAME_UUID,
    READ_ONLY,
    WRITE_FAILED,
}

/** Safe projection of the touch transport that will actually handle stream gestures. */
enum class EffectiveStreamingTouchMode {
    DIRECT_TOUCH,
    ABSOLUTE_POINTER,
    TRACKPAD,
}

object EffectiveStreamingTouchModePolicy {
    fun resolve(
        enableMultiTouchScreen: Boolean,
        touchscreenTrackpad: Boolean,
    ): EffectiveStreamingTouchMode = when {
        touchscreenTrackpad -> EffectiveStreamingTouchMode.TRACKPAD
        enableMultiTouchScreen -> EffectiveStreamingTouchMode.DIRECT_TOUCH
        else -> EffectiveStreamingTouchMode.ABSOLUTE_POINTER
    }

    fun resolve(mode: LigaseCloudTouchMode): EffectiveStreamingTouchMode = when (mode) {
        LigaseCloudTouchMode.SINGLE_TOUCH -> EffectiveStreamingTouchMode.ABSOLUTE_POINTER
        LigaseCloudTouchMode.MULTI_TOUCH -> EffectiveStreamingTouchMode.DIRECT_TOUCH
        LigaseCloudTouchMode.TRACKPAD -> EffectiveStreamingTouchMode.TRACKPAD
    }
}

enum class LigaseInputSelectionStatus {
    UNSELECTED,
    CONNECTED,
    DISCONNECTED,
}

object LigaseInputSelection {
    fun status(
        selectedStableKey: String?,
        connectedStableKeys: Set<String>,
    ): LigaseInputSelectionStatus = when {
        selectedStableKey == null -> LigaseInputSelectionStatus.UNSELECTED
        selectedStableKey in connectedStableKeys -> LigaseInputSelectionStatus.CONNECTED
        else -> LigaseInputSelectionStatus.DISCONNECTED
    }
}

object LigaseInputIdentity {
    fun stableKey(
        descriptor: String,
        vendorId: Int,
        productId: Int,
        category: LigaseInputCategory,
    ): String? {
        val normalizedDescriptor = descriptor.trim()
        if (normalizedDescriptor.isEmpty()) return null
        return listOf(
            category.storedValue,
            vendorId.toString(),
            productId.toString(),
            normalizedDescriptor,
        ).joinToString("|")
    }
}

object LigaseInputSourceClassifier {
    fun categories(
        sources: Int,
        keyboardType: Int,
        sourceGamepad: Int,
        sourceJoystick: Int,
        sourceKeyboard: Int,
        sourceMouse: Int,
        sourceMouseRelative: Int,
        keyboardTypeAlphabetic: Int,
    ): Set<LigaseInputCategory> = buildSet {
        if (sources.hasSource(sourceGamepad) || sources.hasSource(sourceJoystick)) {
            add(LigaseInputCategory.GAMEPAD)
        }
        if (
            keyboardType == keyboardTypeAlphabetic &&
            sources.hasSource(sourceKeyboard)
        ) {
            add(LigaseInputCategory.KEYBOARD)
        }
        if (sources.hasSource(sourceMouse) || sources.hasSource(sourceMouseRelative)) {
            add(LigaseInputCategory.MOUSE)
        }
    }

    private fun Int.hasSource(source: Int): Boolean = this and source == source
}
