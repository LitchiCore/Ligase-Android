package com.limelight.ligase.feature.input.infrastructure

import android.content.Context
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigasePreferences
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.input.LigaseCloudTouchMode
import com.limelight.ligase.input.LigaseInputProfile
import com.limelight.ligase.input.EffectiveStreamingTouchMode
import com.limelight.ligase.input.EffectiveStreamingTouchModePolicy

/**
 * Concrete adapter for the existing product preference keys.
 *
 * These keys remain the v1 production authority while Touch Layout v2 is under
 * review. This adapter deliberately performs no migration or dual write.
 */
class LegacyInputSelectionPreferences(
    private val context: Context,
) {
    fun hasInputMode(): Boolean = LigasePreferences.hasInputDeviceMode(context)

    fun inputMode(): InputDeviceMode = LigasePreferences.getInputDeviceMode(context)

    fun setInputMode(mode: InputDeviceMode) {
        LigasePreferences.setInputDeviceMode(context, mode)
    }

    fun selectedDevice(category: LigaseInputCategory): String? =
        LigasePreferences.getSelectedInputDevice(context, category)

    fun setSelectedDevice(category: LigaseInputCategory, stableKey: String) {
        LigasePreferences.setSelectedInputDevice(context, category, stableKey)
    }

    fun overlayMode(): LigaseTouchOverlayMode =
        LigasePreferences.getTouchOverlayMode(context)

    fun setOverlayMode(mode: LigaseTouchOverlayMode) {
        LigasePreferences.setTouchOverlayMode(context, mode)
    }

    fun cloudTouchMode(): LigaseCloudTouchMode = LigasePreferences.getCloudTouchMode(context)

    fun setCloudTouchMode(mode: LigaseCloudTouchMode) {
        LigasePreferences.setCloudTouchMode(context, mode)
    }

    fun globalProfile(): LigaseInputProfile = LigasePreferences.globalInputProfile(context)

    fun gameOverride(canonicalGameUuid: String): LigaseInputProfile? =
        LigasePreferences.gameInputOverride(context, canonicalGameUuid)

    fun setGameOverride(canonicalGameUuid: String, profile: LigaseInputProfile): Boolean =
        LigasePreferences.setGameInputOverride(context, canonicalGameUuid, profile)

    fun clearGameOverride(canonicalGameUuid: String): Boolean =
        LigasePreferences.clearGameInputOverride(context, canonicalGameUuid)

    fun effectiveStreamingTouchMode(): EffectiveStreamingTouchMode {
        return EffectiveStreamingTouchModePolicy.resolve(cloudTouchMode())
    }
}
