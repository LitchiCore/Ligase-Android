package com.limelight.ligase.feature.input.infrastructure

import android.content.Context
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigasePreferences
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.input.EffectiveStreamingTouchMode
import com.limelight.ligase.input.EffectiveStreamingTouchModePolicy
import com.limelight.preferences.PreferenceConfiguration

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

    fun effectiveStreamingTouchMode(): EffectiveStreamingTouchMode {
        val config = PreferenceConfiguration.readPreferences(context)
        return EffectiveStreamingTouchModePolicy.resolve(
            enableMultiTouchScreen = config.enableMultiTouchScreen,
            touchscreenTrackpad = config.touchscreenTrackpad,
        )
    }
}
