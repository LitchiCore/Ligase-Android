package com.limelight.ligase.input

import android.content.Context
import androidx.preference.PreferenceManager
import com.limelight.TouchKitLayoutNames
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader
import com.limelight.ligase.LigasePreferences

class LigaseTouchLayoutRepository(
    private val context: Context,
) {
    fun layouts(): List<LigaseTouchLayout> {
        val ids = TouchKitLayoutNames.getValues(context)
        val names = TouchKitLayoutNames.getNames(context)
        return ids.mapIndexed { index, id ->
            LigaseTouchLayout(
                id = id,
                displayName = names.getOrElse(index) { id },
            )
        }
    }

    fun initializeSelection(available: List<LigaseTouchLayout>): String? {
        val stored = LigasePreferences.getGlobalTouchLayoutId(context)
        if (stored != null) return stored
        val legacy = TouchKitLayoutNames.getDefaultLayout(context)
        val initial = available.firstOrNull { it.id == legacy }?.id
            ?: available.firstOrNull()?.id
            ?: return null
        select(initial, available)
        return initial
    }

    fun select(layoutId: String, available: List<LigaseTouchLayout>): Boolean {
        if (available.none { it.id == layoutId }) return false
        LigasePreferences.setGlobalTouchLayoutId(context, layoutId)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(TouchKitLayoutNames.DEFAULT_LAYOUT_PREF, layoutId)
            .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, layoutId)
            .apply()
        return true
    }
}
