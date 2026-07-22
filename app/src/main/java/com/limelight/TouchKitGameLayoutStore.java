package com.limelight;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;

/** Remembers the last keyboard-overlay layout used by each host/game pair. */
public final class TouchKitGameLayoutStore {
    private static final String STORE = "touchkit_game_layouts";

    private TouchKitGameLayoutStore() { }

    static String makeKey(String pcUuid, String appUuid, int appId) {
        String pc = pcUuid == null ? "unknown_pc" : pcUuid;
        String app = appUuid == null || appUuid.isEmpty() ? Integer.toString(appId) : appUuid;
        return pc + "|" + app;
    }

    public static void applyRemembered(Context context, String pcUuid,
                                       String appUuid, int appId) {
        String layout = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                .getString(makeKey(pcUuid, appUuid, appId), null);
        if (layout != null && !layout.isEmpty()) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, layout)
                    .apply();
        } else {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                            TouchKitLayoutNames.getDefaultLayout(context))
                    .apply();
        }
    }

    public static void rememberCurrent(Context context, String pcUuid,
                                       String appUuid, int appId) {
        SharedPreferences defaults = PreferenceManager.getDefaultSharedPreferences(context);
        String layout = defaults.getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putString(makeKey(pcUuid, appUuid, appId), layout)
                .apply();
    }

    public static void removeLayout(Context context, String layoutId) {
        SharedPreferences preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (layoutId.equals(preferences.getString(key, null))) {
                editor.remove(key);
            }
        }
        editor.apply();
    }
}
