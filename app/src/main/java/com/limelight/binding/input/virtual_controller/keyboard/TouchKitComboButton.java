package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONException;
import org.json.JSONObject;

/** A standalone on-screen button that presses and releases a key combination. */
public class TouchKitComboButton extends KeyBoardDigitalButton {
    private static final long KEY_DOWN_INTERVAL_MS = 25;
    private static final long MIN_COMBO_HOLD_MS = 35;
    private static final long KEY_UP_INTERVAL_MS = 10;

    private String comboSpec = "ALT+1";
    private int[] comboKeys = TouchKitKeyBindingParser.parseCombo(comboSpec);
    private long pressSequenceEndTime;
    private int pressGeneration;

    public TouchKitComboButton(KeyBoardController controller, Context context, String elementId) {
        super(controller, elementId, 1, context);
        setText(comboSpec);
        addDigitalButtonListener(new DigitalButtonListener() {
            @Override public void onClick() {
                final int generation = ++pressGeneration;
                final int[] keys = comboKeys.clone();
                long now = SystemClock.uptimeMillis();
                pressSequenceEndTime = now + Math.max(0, keys.length - 1) *
                        KEY_DOWN_INTERVAL_MS;
                for (int i = 0; i < keys.length; i++) {
                    final int key = keys[i];
                    virtualController.getHandler().postDelayed(() -> {
                        if (generation != pressGeneration) return;
                        sendKey(key, KeyEvent.ACTION_DOWN);
                    }, i * KEY_DOWN_INTERVAL_MS);
                }
            }

            @Override public void onLongClick() { }

            @Override public void onRelease() {
                final int generation = pressGeneration;
                final int[] keys = comboKeys.clone();
                long waitForPresses = Math.max(0,
                        pressSequenceEndTime - SystemClock.uptimeMillis());
                long releaseStart = waitForPresses + MIN_COMBO_HOLD_MS;
                for (int i = keys.length - 1; i >= 0; i--) {
                    final int key = keys[i];
                    long delay = releaseStart + (keys.length - 1L - i) *
                            KEY_UP_INTERVAL_MS;
                    virtualController.getHandler().postDelayed(() -> {
                        if (generation != pressGeneration) return;
                        sendKey(key, KeyEvent.ACTION_UP);
                    }, delay);
                }
            }
        });
    }

    private void sendKey(int key, int action) {
        KeyEvent event = new KeyEvent(action, key);
        event.setSource(0);
        virtualController.sendKeyEvent(event);
    }

    public String getComboSpec() {
        return comboSpec;
    }

    public boolean setComboSpec(String spec) {
        try {
            comboSpec = TouchKitKeyBindingParser.normalizeCombo(spec);
            comboKeys = TouchKitKeyBindingParser.parseCombo(comboSpec);
            updateDisplayText();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override public void setShowPhysicalKeyNames(boolean showPhysicalKeyNames) {
        super.setShowPhysicalKeyNames(showPhysicalKeyNames);
        updateDisplayText();
    }

    private void updateDisplayText() {
        setText(TouchKitKeyBindingParser.displayCombo(
                comboSpec, getShowPhysicalKeyNames()));
    }

    @Override public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = super.getConfiguration();
        configuration.put("COMBO_SPEC", comboSpec);
        return configuration;
    }

    @Override public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        setComboSpec(configuration.optString("COMBO_SPEC", comboSpec));
    }
}
