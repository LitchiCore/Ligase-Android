package com.limelight;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Imports and exports one TouchKit layout without including hosts or streaming settings. */
public final class TouchKitLayoutTransfer {
    private static final String FORMAT = "artemis-touchkit-layout";
    private static final int VERSION = 1;
    private static final int MAX_JSON_LENGTH = 2 * 1024 * 1024;

    private TouchKitLayoutTransfer() { }

    public static String exportLayout(Context context, String layoutId) throws JSONException {
        if (!TouchKitLayoutNames.contains(context, layoutId)) {
            throw new JSONException("Unknown layout");
        }

        JSONObject preferences = new JSONObject();
        for (Map.Entry<String, ?> entry : context.getSharedPreferences(
                layoutId, Context.MODE_PRIVATE).getAll().entrySet()) {
            preferences.put(entry.getKey(), encodeValue(entry.getValue()));
        }

        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("name", getDisplayName(context, layoutId));
        root.put("sourceLayoutId", layoutId);
        root.put("preferences", preferences);
        return root.toString(2);
    }

    public static ImportResult importLayout(Context context, String json,
                                            String fallbackName) throws JSONException {
        if (json == null || json.trim().isEmpty()) {
            throw new JSONException("Empty layout file");
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new JSONException("Layout file is too large");
        }

        JSONObject root = new JSONObject(json);
        JSONObject preferences;
        String name = normalizeName(fallbackName, "Imported layout");
        boolean legacy = !root.has("format");
        if (legacy) {
            // Older Artemis exports were a raw SharedPreferences JSON object.
            preferences = root;
        } else {
            if (!FORMAT.equals(root.optString("format")) || root.optInt("version", -1) != VERSION) {
                throw new JSONException("Unsupported TouchKit layout format");
            }
            preferences = root.optJSONObject("preferences");
            if (preferences == null) {
                throw new JSONException("Missing layout preferences");
            }
            name = normalizeName(root.optString("name"), name);
        }

        // Fully validate before creating a layout, so malformed files leave no empty preset.
        Map<String, Object> decoded = new HashMap<>();
        for (java.util.Iterator<String> keys = preferences.keys(); keys.hasNext();) {
            String key = keys.next();
            Object value = legacy ? decodeLegacyValue(preferences.get(key))
                    : decodeValue(preferences.getJSONObject(key));
            decoded.put(key, value);
        }

        String layoutId = TouchKitLayoutNames.add(context, name);
        SharedPreferences.Editor editor = context.getSharedPreferences(
                layoutId, Context.MODE_PRIVATE).edit().clear();
        for (Map.Entry<String, Object> entry : decoded.entrySet()) {
            putPreference(editor, entry.getKey(), entry.getValue());
        }
        if (!editor.commit()) {
            TouchKitLayoutNames.delete(context, layoutId);
            throw new JSONException("Could not save imported layout");
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(TouchKitLayoutNames.DEFAULT_LAYOUT_PREF, layoutId)
                .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, layoutId)
                .apply();
        return new ImportResult(layoutId, name);
    }

    private static JSONObject encodeValue(Object value) throws JSONException {
        JSONObject encoded = new JSONObject();
        if (value instanceof String) {
            encoded.put("type", "string").put("value", value);
        } else if (value instanceof Integer) {
            encoded.put("type", "int").put("value", value);
        } else if (value instanceof Long) {
            encoded.put("type", "long").put("value", value);
        } else if (value instanceof Float) {
            encoded.put("type", "float").put("value", value);
        } else if (value instanceof Boolean) {
            encoded.put("type", "boolean").put("value", value);
        } else if (value instanceof Set) {
            JSONArray values = new JSONArray();
            for (Object item : (Set<?>) value) {
                if (!(item instanceof String)) {
                    throw new JSONException("Unsupported preference set");
                }
                values.put(item);
            }
            encoded.put("type", "string_set").put("value", values);
        } else {
            throw new JSONException("Unsupported preference type");
        }
        return encoded;
    }

    private static Object decodeValue(JSONObject encoded) throws JSONException {
        String type = encoded.getString("type");
        switch (type) {
            case "string": return encoded.getString("value");
            case "int": return encoded.getInt("value");
            case "long": return encoded.getLong("value");
            case "float": return (float) encoded.getDouble("value");
            case "boolean": return encoded.getBoolean("value");
            case "string_set":
                JSONArray array = encoded.getJSONArray("value");
                Set<String> values = new HashSet<>();
                for (int i = 0; i < array.length(); i++) {
                    values.add(array.getString(i));
                }
                return values;
            default: throw new JSONException("Unsupported preference type: " + type);
        }
    }

    private static Object decodeLegacyValue(Object value) throws JSONException {
        if (value instanceof String || value instanceof Integer || value instanceof Long
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Double) {
            return ((Double) value).floatValue();
        }
        throw new JSONException("Unsupported legacy preference type");
    }

    @SuppressWarnings("unchecked")
    private static void putPreference(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else editor.putStringSet(key, (Set<String>) value);
    }

    private static String getDisplayName(Context context, String layoutId) {
        String[] values = TouchKitLayoutNames.getValues(context);
        String[] names = TouchKitLayoutNames.getNames(context);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(layoutId)) return names[i];
        }
        return layoutId;
    }

    private static String normalizeName(String candidate, String fallback) {
        String value = candidate == null ? "" : candidate.trim();
        if (value.isEmpty()) value = fallback == null ? "Imported layout" : fallback.trim();
        if (value.isEmpty()) value = "Imported layout";
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    public static final class ImportResult {
        public final String layoutId;
        public final String displayName;

        private ImportResult(String layoutId, String displayName) {
            this.layoutId = layoutId;
            this.displayName = displayName;
        }
    }
}
