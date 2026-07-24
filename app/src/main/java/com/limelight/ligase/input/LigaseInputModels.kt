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

data class LigaseTouchLayout(
    val id: String,
    val displayName: String,
)

enum class LigaseTouchOverlayMode(val storedValue: String) {
    TOUCHKIT_KEYBOARD("touchkitKeyboard"),
    VIRTUAL_GAMEPAD("virtualGamepad"),
    GESTURES_ONLY("gesturesOnly");

    companion object {
        fun fromStoredValue(value: String?): LigaseTouchOverlayMode? =
            entries.firstOrNull { it.storedValue == value }
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
