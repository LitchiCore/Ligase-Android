package com.limelight;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Stable layout IDs with user-editable display names. */
public final class TouchKitLayoutNames {
    public static final String DEFAULT_LAYOUT_PREF = "touchkit_default_keyboard_layout";
    private static final String NAME_STORE = "touchkit_layout_names";
    private static final String REGISTRY_STORE = "touchkit_layout_registry";
    private static final String REGISTRY_KEY = "active_layout_ids";

    private TouchKitLayoutNames() { }

    public static String[] getValues(Context context) {
        String[] defaults = context.getResources().getStringArray(R.array.keyboard_axi_values);
        String encoded = context.getSharedPreferences(REGISTRY_STORE, Context.MODE_PRIVATE)
                .getString(REGISTRY_KEY, null);
        if (encoded == null) {
            return defaults;
        }
        try {
            JSONArray array = new JSONArray(encoded);
            List<String> values = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty() && !values.contains(value)) {
                    values.add(value);
                }
            }
            return values.isEmpty() ? defaults : values.toArray(new String[0]);
        } catch (JSONException e) {
            return defaults;
        }
    }

    public static String[] getNames(Context context) {
        String[] values = getValues(context);
        String[] baseValues = context.getResources().getStringArray(R.array.keyboard_axi_values);
        String[] baseNames = context.getResources().getStringArray(R.array.keyboard_axi_names);
        String[] result = new String[values.length];
        SharedPreferences names = context.getSharedPreferences(NAME_STORE, Context.MODE_PRIVATE);
        for (int i = 0; i < values.length; i++) {
            String fallback = values[i];
            int baseIndex = Arrays.asList(baseValues).indexOf(values[i]);
            if (baseIndex >= 0 && baseIndex < baseNames.length) {
                fallback = baseNames[baseIndex];
            }
            result[i] = names.getString(values[i], fallback);
        }
        return result;
    }

    public static void rename(Context context, String layoutId, String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return;
        }
        context.getSharedPreferences(NAME_STORE, Context.MODE_PRIVATE).edit()
                .putString(layoutId, displayName.trim()).apply();
    }

    public static String getDefaultLayout(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String legacyCurrent = preferences.getString(
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
        String selected = preferences.getString(DEFAULT_LAYOUT_PREF, legacyCurrent);
        return contains(context, selected) ? selected : getValues(context)[0];
    }

    public static boolean contains(Context context, String layoutId) {
        for (String value : getValues(context)) {
            if (value.equals(layoutId)) {
                return true;
            }
        }
        return false;
    }

    public static String add(Context context, String displayName) {
        List<String> values = new ArrayList<>(Arrays.asList(getValues(context)));
        String id;
        do {
            id = "OSC_Keyboard_custom_" + System.currentTimeMillis();
        } while (values.contains(id));
        values.add(id);
        saveRegistry(context, values);
        rename(context, id, displayName);
        return id;
    }

    /**
     * Registers a caller-generated stable layout ID after its preference file is complete.
     * This keeps partially written copies invisible to the catalog.
     */
    public static boolean addWithStableId(Context context, String layoutId, String displayName) {
        if (layoutId == null || !layoutId.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return false;
        }
        List<String> values = new ArrayList<>(Arrays.asList(getValues(context)));
        if (values.contains(layoutId)) {
            return false;
        }
        String normalizedName = displayName == null ? "" : displayName.trim();
        if (normalizedName.isEmpty()) {
            return false;
        }
        SharedPreferences names = context.getSharedPreferences(NAME_STORE, Context.MODE_PRIVATE);
        if (!names.edit().putString(layoutId, normalizedName).commit()) {
            return false;
        }
        values.add(layoutId);
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        boolean registered = context.getSharedPreferences(
                REGISTRY_STORE, Context.MODE_PRIVATE).edit()
                .putString(REGISTRY_KEY, array.toString()).commit();
        if (!registered) {
            names.edit().remove(layoutId).commit();
        }
        return registered;
    }

    public static boolean delete(Context context, String layoutId) {
        List<String> values = new ArrayList<>(Arrays.asList(getValues(context)));
        if (values.size() <= 1 || !values.remove(layoutId)) {
            return false;
        }
        saveRegistry(context, values);
        context.getSharedPreferences(NAME_STORE, Context.MODE_PRIVATE).edit()
                .remove(layoutId).apply();
        context.getSharedPreferences(layoutId, Context.MODE_PRIVATE).edit().clear().apply();
        TouchKitGameLayoutStore.removeLayout(context, layoutId);

        SharedPreferences defaults = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = defaults.edit();
        String fallback = values.get(0);
        if (layoutId.equals(defaults.getString(DEFAULT_LAYOUT_PREF, ""))) {
            editor.putString(DEFAULT_LAYOUT_PREF, fallback);
        }
        if (layoutId.equals(defaults.getString(
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, ""))) {
            editor.putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, fallback);
        }
        editor.apply();
        return true;
    }

    private static void saveRegistry(Context context, List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        context.getSharedPreferences(REGISTRY_STORE, Context.MODE_PRIVATE).edit()
                .putString(REGISTRY_KEY, array.toString()).apply();
    }
}
