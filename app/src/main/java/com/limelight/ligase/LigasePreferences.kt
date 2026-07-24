package com.limelight.ligase

import android.content.Context
import android.app.Activity
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.limelight.ligase.library.HostSortMode
import com.limelight.ligase.library.LibraryLayoutMode
import com.limelight.ligase.input.LigaseTouchOverlayMode

enum class InputDeviceMode(val storedValue: String) {
    GAMEPAD("gamepad"),
    KEYBOARD_MOUSE("keyboard_mouse"),
    TOUCH("touch");

    companion object {
        fun fromStoredValue(value: String?): InputDeviceMode? =
            entries.firstOrNull { it.storedValue == value }
    }
}

enum class LigaseThemeMode(val storedValue: String, val nightMode: Int) {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromStoredValue(value: String?): LigaseThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

enum class LigaseLanguageMode(val storedValue: String) {
    SYSTEM("default"),
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en");

    companion object {
        fun fromStoredValue(value: String?): LigaseLanguageMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

object LigasePreferences {
    private const val FILE_NAME = "ligase_product_preferences"
    private const val KEY_INPUT_DEVICE = "stream_input_device"
    private const val KEY_THEME = "theme"
    private const val KEY_LIBRARY_SORT_PREFIX = "library_sort:"
    private const val KEY_LIBRARY_LAYOUT = "library_layout"
    private const val KEY_LANGUAGE = "list_languages"
    private const val KEY_GLOBAL_TOUCH_LAYOUT = "global_touch_layout"
    private const val KEY_TOUCH_VIRTUAL_GAMEPAD = "touch_virtual_gamepad"
    private const val KEY_TOUCHKIT_KEYBOARD = "touch_touchkit_keyboard"
    private const val KEY_TOUCH_OVERLAY_MODE = "touch_overlay_mode"
    private const val KEY_GAMEPAD_DEVICE = "input_device:gamepad"
    private const val KEY_KEYBOARD_DEVICE = "input_device:keyboard"
    private const val KEY_MOUSE_DEVICE = "input_device:mouse"

    private fun preferences(context: Context) =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun hasInputDeviceMode(context: Context): Boolean =
        preferences(context).contains(KEY_INPUT_DEVICE)

    @JvmStatic
    fun getInputDeviceMode(context: Context): InputDeviceMode =
        InputDeviceMode.fromStoredValue(
            preferences(context).getString(KEY_INPUT_DEVICE, null),
        ) ?: InputDeviceMode.TOUCH

    @JvmStatic
    fun setInputDeviceMode(context: Context, mode: InputDeviceMode) {
        preferences(context).edit().putString(KEY_INPUT_DEVICE, mode.storedValue).apply()
    }

    @JvmStatic
    fun getGlobalTouchLayoutId(context: Context): String? =
        preferences(context).getString(KEY_GLOBAL_TOUCH_LAYOUT, null)

    @JvmStatic
    fun setGlobalTouchLayoutId(context: Context, layoutId: String) {
        preferences(context).edit().putString(KEY_GLOBAL_TOUCH_LAYOUT, layoutId).apply()
    }

    @JvmStatic
    fun getTouchOverlayMode(context: Context): LigaseTouchOverlayMode {
        val prefs = preferences(context)
        LigaseTouchOverlayMode.fromStoredValue(
            prefs.getString(KEY_TOUCH_OVERLAY_MODE, null),
        )?.let { return it }
        // The short-lived two-toggle build allowed both layers. Migrate that
        // ambiguous state to TouchKit keyboard so no update can stack them.
        return when {
            prefs.getBoolean(KEY_TOUCHKIT_KEYBOARD, true) ->
                LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD
            prefs.getBoolean(KEY_TOUCH_VIRTUAL_GAMEPAD, true) ->
                LigaseTouchOverlayMode.VIRTUAL_GAMEPAD
            else -> LigaseTouchOverlayMode.GESTURES_ONLY
        }
    }

    @JvmStatic
    fun setTouchOverlayMode(context: Context, mode: LigaseTouchOverlayMode) {
        preferences(context).edit()
            .putString(KEY_TOUCH_OVERLAY_MODE, mode.storedValue)
            .remove(KEY_TOUCH_VIRTUAL_GAMEPAD)
            .remove(KEY_TOUCHKIT_KEYBOARD)
            .apply()
    }

    @JvmStatic
    fun getSelectedInputDevice(
        context: Context,
        category: com.limelight.ligase.input.LigaseInputCategory,
    ): String? = preferences(context).getString(devicePreferenceKey(category), null)

    @JvmStatic
    fun setSelectedInputDevice(
        context: Context,
        category: com.limelight.ligase.input.LigaseInputCategory,
        stableKey: String,
    ) {
        preferences(context)
            .edit()
            .putString(devicePreferenceKey(category), stableKey)
            .apply()
    }

    @JvmStatic
    fun getThemeMode(context: Context): LigaseThemeMode =
        LigaseThemeMode.fromStoredValue(
            preferences(context).getString(KEY_THEME, LigaseThemeMode.SYSTEM.storedValue),
        )

    @JvmStatic
    fun setThemeMode(context: Context, mode: LigaseThemeMode) {
        preferences(context).edit().putString(KEY_THEME, mode.storedValue).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    @JvmStatic
    fun getLibrarySortMode(context: Context, hostUniqueId: String): HostSortMode =
        HostSortMode.fromWireValue(
            preferences(context).getString(KEY_LIBRARY_SORT_PREFIX + hostUniqueId, null),
        )

    @JvmStatic
    fun setLibrarySortMode(
        context: Context,
        hostUniqueId: String,
        sortMode: HostSortMode,
    ) {
        preferences(context)
            .edit()
            .putString(KEY_LIBRARY_SORT_PREFIX + hostUniqueId, sortMode.wireValue)
            .apply()
    }

    @JvmStatic
    fun getLibraryLayoutMode(context: Context): LibraryLayoutMode =
        LibraryLayoutMode.fromStoredValue(
            preferences(context).getString(KEY_LIBRARY_LAYOUT, null),
        )

    @JvmStatic
    fun setLibraryLayoutMode(context: Context, layoutMode: LibraryLayoutMode) {
        preferences(context)
            .edit()
            .putString(KEY_LIBRARY_LAYOUT, layoutMode.storedValue)
            .apply()
    }

    @JvmStatic
    fun getLanguageMode(context: Context): LigaseLanguageMode =
        LigaseLanguageMode.fromStoredValue(
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY_LANGUAGE, LigaseLanguageMode.SYSTEM.storedValue),
        )

    @JvmStatic
    fun setLanguageMode(context: Context, mode: LigaseLanguageMode) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(KEY_LANGUAGE, mode.storedValue)
            .apply()
    }

    @JvmStatic
    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getThemeMode(context).nightMode)
    }

    @JvmStatic
    fun applySystemBarAppearance(activity: Activity) {
        val isDark = (
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            ) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = !isDark
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightNavigationBars = !isDark
    }

    private fun devicePreferenceKey(
        category: com.limelight.ligase.input.LigaseInputCategory,
    ): String = when (category) {
        com.limelight.ligase.input.LigaseInputCategory.GAMEPAD -> KEY_GAMEPAD_DEVICE
        com.limelight.ligase.input.LigaseInputCategory.KEYBOARD -> KEY_KEYBOARD_DEVICE
        com.limelight.ligase.input.LigaseInputCategory.MOUSE -> KEY_MOUSE_DEVICE
    }
}
