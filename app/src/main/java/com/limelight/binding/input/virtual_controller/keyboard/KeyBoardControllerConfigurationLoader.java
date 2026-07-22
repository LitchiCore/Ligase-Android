/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import static com.limelight.binding.input.KeyboardTranslator.getModifier;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeyBoardControllerConfigurationLoader {
    private static final String LAYOUT_FORMAT_VERSION_KEY = "__touchkit_layout_format_version";
    private static final String LAYOUT_CANVAS_WIDTH_KEY = "__touchkit_canvas_width";
    private static final String LAYOUT_CANVAS_HEIGHT_KEY = "__touchkit_canvas_height";
    private static final int LAYOUT_FORMAT_VERSION = 2;
    private static final String DYNAMIC_ELEMENTS_KEY = "__touchkit_dynamic_elements";
    private static final String DELETED_BASE_ELEMENTS_KEY = "__touchkit_deleted_base_elements";
    private static final String DYNAMIC_ID_MARKER = "__instance_";
    public static final String OSC_PREFERENCE = "keyboard_axi_list";
    public static final String OSC_PREFERENCE_VALUE = "OSC_Keyboard";

    private static final Set<Integer> MODIFIER_KEY_CODES = new HashSet<>();

    static {
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_ALT_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_ALT_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_CTRL_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_CTRL_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_SHIFT_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_SHIFT_RIGHT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_META_LEFT);
        MODIFIER_KEY_CODES.add(KeyEvent.KEYCODE_META_RIGHT);
    }

    public static boolean isModifierKey(int keyCode) {
        return MODIFIER_KEY_CODES.contains(keyCode);
    }

    // The default controls are specified using a grid of 128*72 cells at 16:9
    public static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    public static int screenScaleSwitch(int result, int height) {
        return result * 72 / height;
    }

    public static KeyboardDigitalPadButton createDiaitalPadButton(String elementId, int keyCodeLeft, int keyCodeRight, int keyCodeUp, int keyCodeDown, final KeyBoardController controller, final Context context) {
        KeyboardDigitalPadButton button = new KeyboardDigitalPadButton(controller, context, elementId);
        button.addDigitalPadListener(new KeyboardDigitalPadButton.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeLeft);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeLeft);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeRight);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeRight);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeUp);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeUp);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
                if ((direction & KeyboardDigitalPadButton.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCodeDown);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                } else {
                    KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCodeDown);
                    event.setSource(3);
                    controller.sendKeyEvent(event);
                }
            }
        });
        return button;
    }


    public static KeyBoardAnalogStickButton createKeyBoardAnalogStickButton(final KeyBoardController controller, String elementId, final Context context, int[] keylist) {

        KeyBoardAnalogStickButton analogStick = new KeyBoardAnalogStickButton(controller, elementId, context, keylist);
        analogStick.setListener(new KeyBoardAnalogStickButton.KeyBoardAnalogStickListener() {
            @Override
            public void onkeyEvent(int code, boolean isPress) {
                KeyEvent keyEvent = new KeyEvent(isPress ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code);
                keyEvent.setSource(2);
                controller.sendKeyEvent(keyEvent);
            }
        });

        return analogStick;

    }

    private static KeyBoardAnalogStickButtonFree createKeyBoardAnalogStickButton2(final KeyBoardController controller, String elementId, final Context context, int[] keylist) {

        KeyBoardAnalogStickButtonFree analogStick = new KeyBoardAnalogStickButtonFree(controller, elementId, context, keylist);
        analogStick.setListener(new KeyBoardAnalogStickButtonFree.KeyBoardAnalogStickListener() {
            @Override
            public void onkeyEvent(int code, boolean isPress) {
                KeyEvent keyEvent = new KeyEvent(isPress ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code);
                keyEvent.setSource(2);
                controller.sendKeyEvent(keyEvent);
            }
        });

        return analogStick;

    }

    public static KeyBoardDigitalButton createDigitalButton(
            final String elementId,
            final int keyShort,
            final int type,
            final int layer,
            final String text,
            final int icon,
            final boolean sticky,
            final KeyBoardController controller,
            final Context context) {
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);
        if (elementId.startsWith("key_")) {
            button.setEditableKeyCode(keyShort);
        }

        if (elementId.startsWith("m_s_") || elementId.startsWith("key_s_")) {
            button.setEnableSwitchDown(true);
        }

        // TouchKit exposes locking explicitly through TriggerMode.TOGGLE. Do not retain
        // Artemis's legacy "hold a modifier to make it sticky" behavior, because a normal
        // long press on Shift/Ctrl/Alt must always release when the finger is lifted.
        button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                int keyCode = button.getEditableKeyCode(keyShort);
                KeyEvent keyEvent = createKeyboardButtonEvent(KeyEvent.ACTION_DOWN, keyCode);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);
            }

            @Override
            public void onLongClick() { }

            @Override
            public void onRelease() {
                int keyCode = button.getEditableKeyCode(keyShort);
                KeyEvent keyEvent = createKeyboardButtonEvent(KeyEvent.ACTION_UP, keyCode);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);
            }
        });

        return button;
    }

    private static KeyEvent createKeyboardButtonEvent(int action, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_PLUS) {
            return new KeyEvent(0, 0, action, KeyEvent.KEYCODE_EQUALS, 0,
                    KeyEvent.META_SHIFT_ON);
        }
        return new KeyEvent(action, keyCode);
    }

    public static KeyBoardDigitalButton createCustomButton(
            String elementId,
            final short[] keys,
            final int layer,
            final String text,
            final int icon,
            final boolean sticky,
            final KeyBoardController controller,
            final NvConnection conn,
            final Context context
    ) {
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);

        if (sticky) {
            button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
                @Override
                public void onClick() {
                    if (button.isSticky()) {
                        button.setSticky(false);
                        return;
                    }

                    final byte[] modifier = {(byte) 0};

                    for (short key : keys) {
                        conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

                        modifier[0] |= getModifier(key);
                    }
                }

                @Override
                public void onLongClick() {
                    button.setSticky(true);
                    controller.vibrate(-1);
                }

                @Override
                public void onRelease() {
                    if (button.isSticky()) {
                        return;
                    }

                    final byte[] modifier = {(byte) 0};

                    for (int i = keys.length - 1; i >= 0; i--) {
                        modifier[0] &= (byte) ~getModifier(keys[i]);
                        conn.sendKeyboardInput(keys[i], KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
                    }
                }
            });
        } else {
            button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
                private final byte[] modifier = new byte[1];

                @Override
                public void onClick() {
                    controller.vibrate(KeyEvent.ACTION_DOWN);
                    // Press keys down
                    modifier[0] = 0;
                    for (short key : keys) {
                        conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);
                        modifier[0] |= getModifier(key);
                    }
                }

                @Override
                public void onLongClick() {
                    // Nothing to do for non-sticky buttons
                }

                @Override
                public void onRelease() {
                    controller.vibrate(KeyEvent.ACTION_UP);
                    // Release keys
                    for (int i = keys.length - 1; i >= 0; i--) {
                        short key = keys[i];
                        modifier[0] &= (byte) ~getModifier(key);
                        conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
                    }
                }
            });
        }
        return button;
    }


    public static KeyBoardTouchPadButton createDigitalTouchButton(
            final String elementId,
            final int keyShort,
            final int type,
            final int layer,
            final String text,
            final int icon,
            final KeyBoardController controller,
            final Context context) {
        KeyBoardTouchPadButton button = new KeyBoardTouchPadButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.addDigitalButtonListener(new KeyBoardTouchPadButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                int code = keyShort == 9 ? 3 : 1;
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, code);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onMove(int x, int y) {
                controller.sendMouseMove(x, y);
            }

            @Override
            public void onRelease() {
                int code = keyShort == 9 ? 3 : 1;
                KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, code);
                keyEvent.setSource(type);
                controller.sendKeyEvent(keyEvent);

            }
        });

        return button;
    }

    public static KeyBoardDigitalButton createMouseScrollButton(
            String elementId,
            String text,
            int clicks,
            KeyBoardController controller,
            Context context) {
        TouchKitMouseScrollButton button = new TouchKitMouseScrollButton(
                controller, elementId, clicks, context);
        button.setText(text);
        return button;
    }

    static int getTouchKitMouseLabelResource(int code) {
        switch (code) {
            case 1: return R.string.touchkit_mouse_left;
            case 2: return R.string.touchkit_mouse_middle;
            case 3: return R.string.touchkit_mouse_right;
            case 4: return R.string.touchkit_mouse_side_4;
            case 5: return R.string.touchkit_mouse_side_5;
            default: return 0;
        }
    }

    static int getTouchKitMouseIconResource(int code) {
        switch (code) {
            case 1: return R.drawable.touchkit_mouse_left;
            case 2: return R.drawable.touchkit_mouse_middle;
            case 3: return R.drawable.touchkit_mouse_right;
            case 4:
            case 5: return R.drawable.touchkit_mouse_side;
            default: return -1;
        }
    }

    public static TouchKitRadialMenuButton createRadialMenuButton(
            String elementId,
            KeyBoardController controller,
            Context context) {
        TouchKitRadialMenuButton radial =
                new TouchKitRadialMenuButton(controller, context, elementId);
        return radial;
    }

    public static TouchKitComboButton createComboButton(
            String elementId, KeyBoardController controller, Context context) {
        return new TouchKitComboButton(controller, context, elementId);
    }

    public static TouchKitSoftKeyboardButton createSoftKeyboardButton(
            String elementId, KeyBoardController controller, Context context) {
        return new TouchKitSoftKeyboardButton(controller, context, elementId);
    }

    public static void createDefaultLayout(final KeyBoardController controller, final Context context, final NvConnection conn) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        int height = screen.heightPixels;

        int rightDisplacement = screen.widthPixels - screen.heightPixels * 16 / 9;

        int BUTTON_SIZE = 10;

        int w = screenScale(BUTTON_SIZE, height);

        int maxW = screen.widthPixels / 18;

        if (w > maxW) {
            BUTTON_SIZE = screenScaleSwitch(maxW, height);
            w = screenScale(BUTTON_SIZE, height);
        }

        String result = "";
        try {
            InputStream is = context.getAssets().open("config/keyboard.json");
            int lenght = is.available();
            byte[] buffer = new byte[lenght];
            is.read(buffer);
            result = new String(buffer, "utf8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (TextUtils.isEmpty(result)) {
            return;
        }
        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject jsonObject1 = jsonObject.getJSONObject("data");

            JSONArray keystrokeList = jsonObject1.getJSONArray("keystroke");
            JSONArray dpadList = jsonObject1.getJSONArray("dpad");
            JSONArray rockerList = jsonObject1.getJSONArray("rocker");
            JSONArray mouseList = jsonObject1.getJSONArray("mouse");

            //十字键
            for (int i = 0; i < dpadList.length(); i++) {
                JSONObject obj = dpadList.getJSONObject(i);
                String code = obj.optString("elementId");
                int keyCodeLeft = obj.optInt("leftCode");
                int keyCodeRight = obj.optInt("rightCode");
                int keyCodeUp = obj.optInt("upCode");
                int keyCodeDown = obj.optInt("downCode");
                controller.addElement(createDiaitalPadButton(code, keyCodeLeft, keyCodeRight, keyCodeUp, keyCodeDown, controller, context),
                        screenScale(92, height) + rightDisplacement,
                        screenScale(41, height),
                        (int) (w * 2.5), (int) (w * 2.5)
                );
            }
            //摇杆
            for (int i = 0; i < rockerList.length(); i++) {
                JSONObject obj = rockerList.getJSONObject(i);
                String code = obj.optString("elementId");
                int keyCodeLeft = obj.optInt("leftCode");
                int keyCodeRight = obj.optInt("rightCode");
                int keyCodeUp = obj.optInt("upCode");
                int keyCodeDown = obj.optInt("downCode");
                int keyCodeMiddle = obj.optInt("middleCode");
                int[] keys = new int[]{keyCodeUp, keyCodeDown, keyCodeLeft, keyCodeRight, keyCodeMiddle};

                if (config.enableNewAnalogStick) {
                    controller.addElement(createKeyBoardAnalogStickButton2(controller, code, context, keys),
                            screenScale(4, height),
                            screenScale(41, height),
                            (int) (w * 2.5), (int) (w * 2.5)
                    );
                } else {
                    controller.addElement(createKeyBoardAnalogStickButton(controller, code, context, keys),
                            screenScale(4, height),
                            screenScale(41, height),
                            (int) (w * 2.5), (int) (w * 2.5)
                    );
                }
            }

            //鼠标按键
            for (int i = 0; i < mouseList.length(); i++) {
                JSONObject obj = mouseList.getJSONObject(i);
                obj.put("type", 1);
                keystrokeList.put(obj);
            }

            double buttonSum = 14.0;

            int i;

            //普通按键
            for (i = 0; i < keystrokeList.length(); i++) {
                JSONObject obj = keystrokeList.getJSONObject(i);

                String name = obj.optString("name");

                int type = obj.optInt("type");

                int code = obj.optInt("code");

                int mouseLabel = type == 1 ? getTouchKitMouseLabelResource(code) : 0;
                if (mouseLabel != 0) {
                    name = context.getString(mouseLabel);
                }

                int switchButton = obj.optInt("switchButton");

                String elementId = type == 0 ? "key_" + code : "m_" + code;

                if (switchButton == 1) {
                    elementId = type == 0 ? "key_s_" + code : "m_s_" + code;
                }

                int lastIndex = (int) (i / buttonSum);

                int x = screenScale(1 + (int) (i % buttonSum) * BUTTON_SIZE, height);

                int y = screenScale(BUTTON_SIZE + lastIndex * BUTTON_SIZE, height);

                {
                    // Legacy m_9/m_10/m_11 controls were miniature touchpads. They
                    // used ambiguous multi-pointer coordinates and could fling the
                    // cursor. Keep their click meaning but load normal mouse buttons.
                    int effectiveCode = type == 1 && code >= 9 && code <= 11
                            ? (code == 9 ? 3 : 1) : code;
                    int effectiveLabel = type == 1 ? getTouchKitMouseLabelResource(effectiveCode) : 0;
                    if (effectiveLabel != 0) {
                        name = context.getString(effectiveLabel);
                    }
                    int icon = type == 1 ? getTouchKitMouseIconResource(effectiveCode) : -1;
                    KeyBoardDigitalButton button = createDigitalButton(elementId, effectiveCode, type, 1,
                            name, icon, config.stickyModifierKey && isModifierKey(effectiveCode),
                            controller, context);
                    controller.addElement(button,
                            x, y,
                            w, w
                    );
                    // Side buttons are optional. Keep newly introduced X1/X2 controls out
                    // of existing layouts until the user explicitly adds them.
                    if ((type == 1 && (code == 4 || code == 5)) ||
                            (type == 0 && code >= android.view.KeyEvent.KEYCODE_NUMPAD_0 &&
                                    code <= android.view.KeyEvent.KEYCODE_NUMPAD_9)) {
                        button.enabled = false;
                        button.setVisibility(android.view.View.GONE);
                    }
                }
                LimeLog.info("x:" + x + ",y:" + y + ",W&H:" + w + "," + screenScale(BUTTON_SIZE, height));
            }

            // Custom keys
            SharedPreferences preferences = context.getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE);
            String value = preferences.getString(GameMenu.KEY_NAME, "");

            if (!TextUtils.isEmpty(value)) {
                try {
                    KeyConfigHelper.ShortcutFile shortcutFile =
                            KeyConfigHelper.parseShortcutFile(value);

                    if (shortcutFile != null &&
                            shortcutFile.data != null &&
                            !shortcutFile.data.isEmpty()) {

                        List<KeyConfigHelper.Shortcut> data = shortcutFile.data;
                        for (int idx = 0; idx < data.size(); idx++) {
                            KeyConfigHelper.Shortcut sc = data.get(idx);

                            String id = (sc.id == null || sc.id.isEmpty())
                                    ? Integer.toString(idx) : sc.id;
                            String name = sc.name;                 // may be null

                            List<String> keys = sc.keys;
                            short[] vkKeyCodes = new short[keys.size()];

                            for (int j = 0; j < keys.size(); j++) {
                                String code = keys.get(j);
                                int keycode;

                                if (code.startsWith("0x")) {              // literal hex
                                    keycode = Integer.parseInt(code.substring(2), 16);

                                } else if (code.startsWith("VK_")) {      // symbolic VK_*
                                    Field field = KeyMapper.class.getDeclaredField(code);
                                    keycode = field.getInt(null);

                                } else {
                                    throw new IllegalArgumentException("Unknown key code: " + code);
                                }
                                vkKeyCodes[j] = (short) keycode;
                            }

                            boolean sticky = sc.sticky;

                            int lastIndex = (int) ((idx + i) / buttonSum);

                            int x = screenScale((int) (1 + ((idx + i) % buttonSum) * BUTTON_SIZE), height);
                            int y = screenScale(BUTTON_SIZE + lastIndex * BUTTON_SIZE, height);

                            controller.addElement(
                                    createCustomButton("custom_" + id, vkKeyCodes, 1,
                                            name, -1, sticky, controller, conn, context),
                                    x, y,
                                    w, w
                            );
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(context, context.getString(R.string.wrong_import_format), Toast.LENGTH_SHORT).show();
                }
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        // Optional TouchKit controls are part of the model so old layouts can load
        // their saved configuration. They stay disabled until selected from Add Keys.
        TouchKitRadialMenuButton radial = createRadialMenuButton(
                "touchkit_radial_1", controller, context);
        int radialSize = w * 3;
        controller.addElement(radial,
                Math.max(0, screen.widthPixels / 2 - radialSize / 2),
                Math.max(0, height - radialSize - w), radialSize, radialSize);
        radial.enabled = false;
        radial.setVisibility(android.view.View.GONE);

        TouchKitSoftKeyboardButton softKeyboard = createSoftKeyboardButton(
                "touchkit_soft_keyboard", controller, context);
        controller.addElement(softKeyboard, w * 2, w, w, w);
        softKeyboard.enabled = false;
        softKeyboard.setVisibility(android.view.View.GONE);

        // Pre-create reusable combo slots so their position and binding survive
        // layout reloads. They become visible only when explicitly added.
        for (int comboIndex = 1; comboIndex <= 8; comboIndex++) {
            TouchKitComboButton combo = createComboButton(
                    "touchkit_combo_" + comboIndex, controller, context);
            controller.addElement(combo, w * comboIndex, height - w * 2, w, w);
            combo.enabled = false;
            combo.setVisibility(android.view.View.GONE);
        }

        KeyBoardDigitalButton scrollUp = createMouseScrollButton(
                "touchkit_scroll_up", context.getString(R.string.touchkit_mouse_scroll_up_short),
                1, controller, context);
        controller.addElement(scrollUp, w, height - w * 2, w, w);
        scrollUp.enabled = false;
        scrollUp.setVisibility(android.view.View.GONE);

        KeyBoardDigitalButton scrollDown = createMouseScrollButton(
                "touchkit_scroll_down", context.getString(R.string.touchkit_mouse_scroll_down_short),
                -1, controller, context);
        controller.addElement(scrollDown, w * 2, height - w * 2, w, w);
        scrollDown.enabled = false;
        scrollDown.setVisibility(android.view.View.GONE);

        controller.setOpacity(config.touchkitOverlayOpacity);
    }

    public static void saveProfile(final KeyBoardController controller,
                                   final Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);

        SharedPreferences.Editor prefEditor = context.getSharedPreferences(name, Activity.MODE_PRIVATE).edit();

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            String prefKey = "" + element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefEditor.putInt(LAYOUT_FORMAT_VERSION_KEY, LAYOUT_FORMAT_VERSION);
        prefEditor.putInt(LAYOUT_CANVAS_WIDTH_KEY, controller.getLayoutWidth());
        prefEditor.putInt(LAYOUT_CANVAS_HEIGHT_KEY, controller.getLayoutHeight());
        prefEditor.apply();
    }

    static String createDynamicElementId(String baseId) {
        return baseId + DYNAMIC_ID_MARKER + System.nanoTime();
    }

    static boolean isDynamicElementId(String elementId) {
        return elementId.contains(DYNAMIC_ID_MARKER);
    }

    static void recordDynamicElement(Context context, JSONObject source, String elementId) {
        SharedPreferences preferences = getLayoutPreferences(context);
        JSONArray descriptors = readArray(preferences.getString(DYNAMIC_ELEMENTS_KEY, "[]"));
        try {
            JSONObject descriptor = new JSONObject(source.toString());
            descriptor.put("elementId", elementId);
            descriptors.put(descriptor);
            preferences.edit().putString(DYNAMIC_ELEMENTS_KEY, descriptors.toString()).apply();
        } catch (JSONException ignored) { }
    }

    static boolean containsDynamicElement(Context context, String elementId) {
        JSONArray descriptors = readArray(getLayoutPreferences(context)
                .getString(DYNAMIC_ELEMENTS_KEY, "[]"));
        for (int i = 0; i < descriptors.length(); i++) {
            JSONObject descriptor = descriptors.optJSONObject(i);
            if (descriptor != null && elementId.equals(
                    descriptor.optString("elementId"))) return true;
        }
        return false;
    }

    static void restoreDeletedBaseElement(Context context, String elementId) {
        SharedPreferences preferences = getLayoutPreferences(context);
        JSONArray deleted = readArray(preferences.getString(DELETED_BASE_ELEMENTS_KEY, "[]"));
        JSONArray retained = new JSONArray();
        for (int i = 0; i < deleted.length(); i++) {
            String id = deleted.optString(i);
            if (!elementId.equals(id)) retained.put(id);
        }
        preferences.edit().putString(DELETED_BASE_ELEMENTS_KEY, retained.toString()).apply();
    }

    static void deleteElement(Context context, String elementId) {
        SharedPreferences preferences = getLayoutPreferences(context);
        SharedPreferences.Editor editor = preferences.edit().remove(elementId);
        if (isDynamicElementId(elementId)) {
            JSONArray descriptors = readArray(preferences.getString(DYNAMIC_ELEMENTS_KEY, "[]"));
            JSONArray retained = new JSONArray();
            for (int i = 0; i < descriptors.length(); i++) {
                JSONObject descriptor = descriptors.optJSONObject(i);
                if (descriptor != null && !elementId.equals(
                        descriptor.optString("elementId"))) retained.put(descriptor);
            }
            editor.putString(DYNAMIC_ELEMENTS_KEY, retained.toString());
        } else {
            JSONArray deleted = readArray(preferences.getString(DELETED_BASE_ELEMENTS_KEY, "[]"));
            boolean present = false;
            for (int i = 0; i < deleted.length(); i++) {
                if (elementId.equals(deleted.optString(i))) present = true;
            }
            if (!present) deleted.put(elementId);
            editor.putString(DELETED_BASE_ELEMENTS_KEY, deleted.toString());
        }
        editor.apply();
    }

    static void applyDeletedBaseElements(KeyBoardController controller, Context context) {
        JSONArray deleted = readArray(getLayoutPreferences(context)
                .getString(DELETED_BASE_ELEMENTS_KEY, "[]"));
        for (int i = 0; i < deleted.length(); i++) {
            keyBoardVirtualControllerElement element = controller.findElement(deleted.optString(i));
            if (element != null) controller.removeElement(element);
        }
    }

    static void createDynamicElements(KeyBoardController controller, Context context,
                                      NvConnection conn) {
        JSONArray descriptors = readArray(getLayoutPreferences(context)
                .getString(DYNAMIC_ELEMENTS_KEY, "[]"));
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int w = screenScale(10, screen.heightPixels);
        int maxW = screen.widthPixels / 18;
        if (w > maxW) w = maxW;
        for (int i = 0; i < descriptors.length(); i++) {
            JSONObject descriptor = descriptors.optJSONObject(i);
            if (descriptor == null) continue;
            try {
                keyBoardVirtualControllerElement element = createElementFromDescriptor(
                        descriptor, controller, context, conn);
                if (element == null || controller.findElement(element.elementId) != null) continue;
                int type = descriptor.optInt("type", 0);
                int size = type == 5 ? w * 3
                        : (type == 2 || type == 3) ? (int) (w * 2.5f) : w;
                controller.addElement(element, 10, 100, size, size);
            } catch (Exception e) {
                LimeLog.warning("Unable to restore TouchKit element: " + e.getMessage());
            }
        }
    }

    static keyBoardVirtualControllerElement createElementFromDescriptor(
            JSONObject obj, KeyBoardController controller, Context context,
            NvConnection conn) throws Exception {
        int type = obj.optInt("type", 0);
        String elementId = obj.getString("elementId");
        if (type == 2) {
            int[] keys = {obj.getInt("upCode"), obj.getInt("downCode"),
                    obj.getInt("leftCode"), obj.getInt("rightCode"),
                    obj.getInt("middleCode")};
            return createKeyBoardAnalogStickButton(controller, elementId, context, keys);
        }
        if (type == 3) {
            return createDiaitalPadButton(elementId, obj.getInt("leftCode"),
                    obj.getInt("rightCode"), obj.getInt("upCode"),
                    obj.getInt("downCode"), controller, context);
        }
        if (type == 5) return createRadialMenuButton(elementId, controller, context);
        if (type == 6) return createMouseScrollButton(elementId, obj.getString("name"),
                obj.getInt("scroll"), controller, context);
        if (type == 7) return createComboButton(elementId, controller, context);
        if (type == 8) return createSoftKeyboardButton(elementId, controller, context);
        if (type == 4) {
            JSONArray keysJson = obj.getJSONArray("keys");
            short[] keys = new short[keysJson.length()];
            for (int i = 0; i < keysJson.length(); i++) {
                String code = keysJson.getString(i);
                int keyCode;
                if (code.startsWith("0x")) keyCode = Integer.parseInt(code.substring(2), 16);
                else {
                    Field field = KeyMapper.class.getDeclaredField(code);
                    keyCode = field.getInt(null);
                }
                keys[i] = (short) keyCode;
            }
            return createCustomButton(elementId, keys, 1, obj.getString("name"), -1,
                    obj.optBoolean("sticky"), controller, conn, context);
        }
        String name = obj.getString("name");
        int code = obj.getInt("code");
        int mouseLabel = type == 1 ? getTouchKitMouseLabelResource(code) : 0;
        if (mouseLabel != 0) name = context.getString(mouseLabel);
        return createDigitalButton(elementId, code, type, 1, name,
                type == 1 ? getTouchKitMouseIconResource(code) : -1,
                false, controller, context);
    }

    private static SharedPreferences getLayoutPreferences(Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);
        return context.getSharedPreferences(name, Activity.MODE_PRIVATE);
    }

    private static JSONArray readArray(String encoded) {
        try { return new JSONArray(encoded == null ? "[]" : encoded); }
        catch (JSONException e) { return new JSONArray(); }
    }

    public static void loadFromPreferences(final KeyBoardController controller, final Context context) {
        String name = PreferenceManager.getDefaultSharedPreferences(context).getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE);

        SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);
        int sourceWidth = pref.getInt(LAYOUT_CANVAS_WIDTH_KEY, 0);
        int sourceHeight = pref.getInt(LAYOUT_CANVAS_HEIGHT_KEY, 0);
        int targetWidth = controller.getLayoutWidth();
        int targetHeight = controller.getLayoutHeight();

        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            String prefKey = "" + element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    JSONObject configuration = new JSONObject(jsonConfig);
                    if (sourceWidth > 0 && sourceHeight > 0
                            && targetWidth > 0 && targetHeight > 0
                            && (sourceWidth != targetWidth || sourceHeight != targetHeight)) {
                        scaleConfiguration(configuration, sourceWidth, sourceHeight,
                                targetWidth, targetHeight);
                    }
                    element.loadConfiguration(configuration);
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }

    /**
     * Scales a layout using screen-height units. Controls on the left stay attached to
     * the left edge, controls on the right stay attached to the right edge, and controls
     * around the middle stay attached to the screen centre. This avoids horizontal drift
     * when a layout moves between displays with different aspect ratios.
     */
    static JSONObject scaleConfiguration(JSONObject configuration,
                                          int sourceWidth, int sourceHeight,
                                          int targetWidth, int targetHeight)
            throws JSONException {
        int left = configuration.getInt("LEFT");
        int top = configuration.getInt("TOP");
        int width = configuration.getInt("WIDTH");
        int height = configuration.getInt("HEIGHT");

        float scale = (float) targetHeight / (float) sourceHeight;
        int scaledWidth = Math.max(20, Math.round(width * scale));
        int scaledHeight = Math.max(20, Math.round(height * scale));
        float sourceCenterX = left + width / 2f;
        int scaledLeft;

        if (sourceCenterX < sourceWidth * 0.45f) {
            scaledLeft = Math.round(left * scale);
        } else if (sourceCenterX > sourceWidth * 0.55f) {
            int rightMargin = sourceWidth - left - width;
            scaledLeft = targetWidth - Math.round(rightMargin * scale) - scaledWidth;
        } else {
            float offsetFromCenter = sourceCenterX - sourceWidth / 2f;
            scaledLeft = Math.round(targetWidth / 2f + offsetFromCenter * scale
                    - scaledWidth / 2f);
        }

        int scaledTop = Math.round(top * scale);
        scaledLeft = Math.max(0, Math.min(scaledLeft, targetWidth - scaledWidth));
        scaledTop = Math.max(0, Math.min(scaledTop, targetHeight - scaledHeight));

        configuration.put("LEFT", scaledLeft);
        configuration.put("TOP", scaledTop);
        configuration.put("WIDTH", Math.min(scaledWidth, targetWidth));
        configuration.put("HEIGHT", Math.min(scaledHeight, targetHeight));
        return configuration;
    }
}
