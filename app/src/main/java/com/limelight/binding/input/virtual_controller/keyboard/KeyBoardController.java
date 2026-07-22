/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.limelight.Game;
import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.TouchKitLayoutNames;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.NvConnection;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyBoardController {

    private static final String TOUCHKIT_FOREGROUND_OPACITY_PREF =
            "touchkit_foreground_opacity";
    private static final String TOUCHKIT_BACKGROUND_OPACITY_PREF =
            "seekbar_touchkit_overlay_opacity";

    public enum ControllerMode {
        Active,
        LayoutEditor,
        EditProperties,
        MoveButtons,
        ResizeButtons,
        DisableEnableButtons
    }

    public boolean shown = false;

    private static final boolean _PRINT_DEBUG_INFORMATION = false;

    private final NvConnection conn;
    private final Context context;
    private final Handler handler;
    private final boolean inputDispatchEnabled;

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;

    private Map<Integer, Runnable> keyEventRunnableMap = new HashMap<>();
    private final Map<Integer, Integer> activeKeyboardKeyCounts = new HashMap<>();

    private Button buttonConfigure = null;
    private Button buttonClearAll = null;
    private Button buttonAddKeys = null;

    private Vibrator vibrator;
    private List<keyBoardVirtualControllerElement> elements = new ArrayList<>();
    private final Map<Integer, Integer> activeControlPointerCounts = new HashMap<>();

    public KeyBoardController(final NvConnection conn, FrameLayout layout, final Context context) {
        this(conn, layout, context, true);
    }

    public KeyBoardController(final NvConnection conn, FrameLayout layout, final Context context,
                              boolean inputDispatchEnabled) {
        this.conn = conn;
        this.frame_layout = layout;
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.inputDispatchEnabled = inputDispatchEnabled;

        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // Configure button
        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.5f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_keyboard_setting);

        // Add long click listener for moving the configure button
        buttonConfigure.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(context, context.getString(R.string.keyboard_configure_movable), Toast.LENGTH_SHORT).show();
                buttonConfigure.setTag("movable");
                vibrator.vibrate(100); // Give haptic feedback
                return true;
            }
        });

        // Add touch listener for moving
        buttonConfigure.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private float lastTouchX, lastTouchY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if ("movable".equals(view.getTag())) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            dX = view.getX() - event.getRawX();
                            dY = view.getY() - event.getRawY();
                            lastTouchX = event.getRawX();
                            lastTouchY = event.getRawY();
                            isMoving = false;
                            break;

                        case MotionEvent.ACTION_MOVE:
                            float newX = event.getRawX() + dX;
                            float newY = event.getRawY() + dY;

                            // Check if actually moving (to differentiate from simple touch)
                            if (Math.abs(event.getRawX() - lastTouchX) > 5 ||
                                Math.abs(event.getRawY() - lastTouchY) > 5) {
                                isMoving = true;
                            }

                            if (isMoving) {
                                // Keep button within screen bounds
                                newX = Math.max(0, Math.min(newX, frame_layout.getWidth() - view.getWidth()));
                                newY = Math.max(0, Math.min(newY, frame_layout.getHeight() - view.getHeight()));

                                view.setX(newX);
                                view.setY(newY);
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                            view.setTag(null);
                            if (!isMoving) {
                                // If not moving, trigger the click event
                                view.performClick();
                            }
                            break;
                    }
                    return true;
                }
                return false;
            }
        });

        buttonConfigure.setOnClickListener(v -> showConfigurationMenu());

        // Clear All button
        buttonClearAll = new Button(context);
        buttonClearAll.setBackgroundColor(Color.DKGRAY);
        buttonClearAll.setText(context.getString(R.string.keyboard_clear_all));
        buttonClearAll.setAlpha(0.7f);
        buttonClearAll.setVisibility(View.GONE);
        buttonClearAll.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.keyboard_clear_all_confirm_title));
            builder.setMessage(context.getString(R.string.keyboard_clear_all_confirm_message));
            builder.setPositiveButton(context.getString(R.string.yes), (dialog, which) -> {
                for (keyBoardVirtualControllerElement element :
                        new ArrayList<>(elements)) deleteElementFromLayout(element);
                vibrate(KeyEvent.ACTION_DOWN);
            });
            builder.setNegativeButton(context.getString(R.string.no), null);
            builder.show();
        });

        // Add Keys button
        buttonAddKeys = new Button(context);
        buttonAddKeys.setBackgroundColor(Color.DKGRAY);
        buttonAddKeys.setText(context.getString(R.string.keyboard_add_keys));
        buttonAddKeys.setAlpha(0.7f);
        buttonAddKeys.setVisibility(View.GONE);
        buttonAddKeys.setOnClickListener(v -> showKeySelectionDialog());

        refreshLayout();
    }

    Handler getHandler() {
        return handler;
    }

    void beginControlPointer(int pointerId) {
        synchronized (activeControlPointerCounts) {
            activeControlPointerCounts.put(pointerId,
                    activeControlPointerCounts.getOrDefault(pointerId, 0) + 1);
        }
    }

    void endControlPointer(int pointerId) {
        // Defer removal until the current MotionEvent has finished dispatching to
        // sibling views. Game must still be able to exclude this pointer on UP.
        handler.post(() -> {
            synchronized (activeControlPointerCounts) {
                int count = activeControlPointerCounts.getOrDefault(pointerId, 0);
                if (count <= 1) {
                    activeControlPointerCounts.remove(pointerId);
                } else {
                    activeControlPointerCounts.put(pointerId, count - 1);
                }
            }
        });
    }

    public boolean isControlPointerActive(int pointerId) {
        synchronized (activeControlPointerCounts) {
            return activeControlPointerCounts.containsKey(pointerId);
        }
    }

    public boolean hasActiveControlPointers() {
        synchronized (activeControlPointerCounts) {
            return !activeControlPointerCounts.isEmpty();
        }
    }

    private void showConfigurationMenu() {
        String[] items = {
                context.getString(R.string.touchkit_editor_properties),
                context.getString(R.string.keyboard_add_keys),
                context.getString(R.string.touchkit_switch_layout),
                context.getString(R.string.touchkit_editor_more)
        };

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.touchkit_editor_title_with_layout,
                        getCurrentLayoutName()))
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            setControllerMode(ControllerMode.EditProperties);
                            Toast.makeText(context,
                                    R.string.touchkit_editor_select_control_hint,
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            showKeySelectionDialog();
                            break;
                        case 2:
                            showLayoutSelectionDialog();
                            break;
                        default:
                            showMoreEditorActions();
                            break;
                    }
                })
                .setPositiveButton(R.string.touchkit_editor_save_exit, (dialog, which) ->
                        finishEditing())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String getCurrentLayoutId() {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
    }

    private String getCurrentLayoutName() {
        String current = getCurrentLayoutId();
        String[] values = TouchKitLayoutNames.getValues(context);
        String[] names = TouchKitLayoutNames.getNames(context);
        for (int i = 0; i < values.length && i < names.length; i++) {
            if (values[i].equals(current)) {
                return names[i];
            }
        }
        return current;
    }

    private void showRenameCurrentLayoutDialog() {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setText(getCurrentLayoutName());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_rename_layout_prompt)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(context, R.string.profile_manager_name_cannot_be_blank,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    TouchKitLayoutNames.rename(context, getCurrentLayoutId(), name);
                    Toast.makeText(context, context.getString(
                            R.string.touchkit_layout_renamed, name), Toast.LENGTH_SHORT).show();
                    showConfigurationMenu();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMoreEditorActions() {
        String[] items = {
                context.getString(R.string.touchkit_rename_current_layout),
                context.getString(R.string.touchkit_global_background_opacity),
                context.getString(R.string.touchkit_global_foreground_opacity),
                context.getString(R.string.keyboard_clear_all)
        };
        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_editor_more)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showRenameCurrentLayoutDialog();
                    else if (which == 1) showGlobalBackgroundOpacityDialog();
                    else if (which == 2) showGlobalForegroundOpacityDialog();
                    else buttonClearAll.performClick();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showGlobalBackgroundOpacityDialog() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int original = preferences.getInt(TOUCHKIT_BACKGROUND_OPACITY_PREF, 100);
        LinearLayout container = createEditorContainer();
        TextView value = new TextView(context);
        container.addView(value);
        SeekBar opacity = new SeekBar(context);
        opacity.setMax(90);
        opacity.setProgress(Math.max(0, Math.min(90, original - 10)));
        Runnable update = () -> {
            int current = opacity.getProgress() + 10;
            value.setText(context.getString(
                    R.string.touchkit_global_background_opacity_value, current));
            setOpacity(current);
        };
        update.run();
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                    boolean fromUser) {
                update.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        container.addView(opacity);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_global_background_opacity)
                .setView(container)
                .setPositiveButton(R.string.save, (ignored, which) ->
                        preferences.edit().putInt(TOUCHKIT_BACKGROUND_OPACITY_PREF,
                                opacity.getProgress() + 10).apply())
                .setNegativeButton(R.string.cancel, (ignored, which) -> setOpacity(original))
                .create();
        dialog.setOnCancelListener(ignored -> setOpacity(original));
        dialog.show();
    }

    private void showGlobalForegroundOpacityDialog() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int original = preferences.getInt(TOUCHKIT_FOREGROUND_OPACITY_PREF, 100);
        LinearLayout container = createEditorContainer();
        TextView value = new TextView(context);
        container.addView(value);
        SeekBar opacity = new SeekBar(context);
        opacity.setMax(90);
        opacity.setProgress(Math.max(0, Math.min(90, original - 10)));
        Runnable update = () -> {
            int current = opacity.getProgress() + 10;
            value.setText(context.getString(
                    R.string.touchkit_global_foreground_opacity_value, current));
            setGlobalForegroundOpacity(current);
        };
        update.run();
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                    boolean fromUser) {
                update.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        container.addView(opacity);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_global_foreground_opacity)
                .setView(container)
                .setPositiveButton(R.string.save, (ignored, which) ->
                        preferences.edit().putInt(TOUCHKIT_FOREGROUND_OPACITY_PREF,
                                opacity.getProgress() + 10).apply())
                .setNegativeButton(R.string.cancel, (ignored, which) ->
                        setGlobalForegroundOpacity(original))
                .create();
        dialog.setOnCancelListener(ignored -> setGlobalForegroundOpacity(original));
        dialog.show();
    }

    private void finishEditing() {
        currentMode = inputDispatchEnabled
                ? ControllerMode.Active : ControllerMode.LayoutEditor;
        showControlButtons(false);
        showEnabledElements();
        KeyBoardControllerConfigurationLoader.saveProfile(this, context);
        Toast.makeText(context, R.string.configuration_mode_exiting,
                Toast.LENGTH_SHORT).show();
        invalidateElements();
        // In a live stream this button only leaves editor mode. The standalone
        // settings editor has no active input mode to return to, so close that
        // activity and reveal the existing settings screen underneath it.
        if (!inputDispatchEnabled && context instanceof Activity) {
            ((Activity) context).setResult(Activity.RESULT_OK);
            ((Activity) context).finish();
        }
    }

    private void showLayoutSelectionDialog() {
        String[] names = TouchKitLayoutNames.getNames(context);
        String[] values = TouchKitLayoutNames.getValues(context);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String current = preferences.getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected = i;
                break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_switch_layout)
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    preferences.edit().putString(
                            KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                            values[which]).apply();
                    currentMode = inputDispatchEnabled
                            ? ControllerMode.Active : ControllerMode.LayoutEditor;
                    refreshLayout();
                    show();
                    if (!inputDispatchEnabled) {
                        enterLayoutEditorMode();
                    }
                    Toast.makeText(context, context.getString(
                            R.string.touchkit_layout_switched, names[which]),
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setControllerMode(ControllerMode mode) {
        if (mode != ControllerMode.Active) {
            for (keyBoardVirtualControllerElement element : elements) {
                if (element instanceof TouchKitGyroMouseButton) {
                    ((TouchKitGyroMouseButton) element).setActive(false);
                }
            }
        }
        currentMode = mode;
        if (mode == ControllerMode.DisableEnableButtons) {
            showElements();
            showControlButtons(false);
        } else {
            showEnabledElements();
            showControlButtons(false);
        }
        invalidateElements();
    }

    /** Enables drag-to-move and tap-to-edit without dispatching input. */
    public void enterLayoutEditorMode() {
        setControllerMode(ControllerMode.LayoutEditor);
    }

    private void invalidateElements() {
        buttonConfigure.invalidate();
        for (keyBoardVirtualControllerElement element : elements) {
            element.invalidate();
        }
    }

    void showElementEditor(keyBoardVirtualControllerElement element) {
        if (element instanceof TouchKitSoftKeyboardButton) {
            showSoftKeyboardButtonEditor((TouchKitSoftKeyboardButton) element);
        } else if (element instanceof TouchKitGyroMouseButton) {
            showGyroMouseButtonEditor((TouchKitGyroMouseButton) element);
        } else if (element instanceof KeyBoardDigitalButton) {
            showDigitalButtonEditor((KeyBoardDigitalButton) element);
        } else if (element instanceof TouchKitRadialMenuButton) {
            showRadialMenuEditor((TouchKitRadialMenuButton) element);
        } else if (element instanceof TouchKitConfigurableStick) {
            showStickEditor(element, (TouchKitConfigurableStick) element);
        } else {
            showBasicElementEditor(element);
        }
    }

    private LinearLayout createEditorContainer() {
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding / 2, padding, 0);
        return container;
    }

    private View createScrollableEditorView(LinearLayout container) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.addView(container, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private final class AppearanceControls {
        private final SeekBar size;
        private final SeekBar opacity;
        private final int minSize;

        AppearanceControls(LinearLayout container, keyBoardVirtualControllerElement element) {
            float density = context.getResources().getDisplayMetrics().density;
            minSize = (int) (24 * density);
            int availableWidth = frame_layout.getWidth() > 0
                    ? frame_layout.getWidth() : context.getResources().getDisplayMetrics().widthPixels;
            int maxSize = Math.max(minSize, Math.min(availableWidth / 2, (int) (320 * density)));

            TextView sizeValue = new TextView(context);
            container.addView(sizeValue);
            size = new SeekBar(context);
            size.setMax(maxSize - minSize);
            size.setProgress(Math.max(0, Math.min(size.getMax(),
                    Math.max(element.getWidth(), element.getHeight()) - minSize)));
            Runnable updateSize = () -> sizeValue.setText(context.getString(
                    R.string.touchkit_editor_size_value,
                    Math.round((minSize + size.getProgress()) / density)));
            updateSize.run();
            size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateSize.run();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            container.addView(size);

            TextView opacityValue = new TextView(context);
            container.addView(opacityValue);
            opacity = new SeekBar(context);
            opacity.setMax(100);
            opacity.setProgress(element.getBackgroundOpacity());
            Runnable updateOpacity = () -> opacityValue.setText(context.getString(
                    R.string.touchkit_editor_opacity_value, opacity.getProgress()));
            updateOpacity.run();
            opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateOpacity.run();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            container.addView(opacity);
        }

        void apply(keyBoardVirtualControllerElement element) {
            int px = minSize + size.getProgress();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
            params.width = px;
            params.height = px;
            element.setBackgroundOpacity(opacity.getProgress());
            element.requestLayout();
        }
    }

    private void showBasicElementEditor(keyBoardVirtualControllerElement element) {
        LinearLayout container = createEditorContainer();
        AppearanceControls appearance = new AppearanceControls(container, element);
        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_editor_button_title)
                .setView(createScrollableEditorView(container))
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    appearance.apply(element);
                    KeyBoardControllerConfigurationLoader.saveProfile(this, context);
                })
                .setNeutralButton(R.string.touchkit_delete_control,
                        (dialog, which) -> deleteElementFromLayout(element))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showGyroMouseButtonEditor(TouchKitGyroMouseButton button) {
        LinearLayout container = createEditorContainer();
        AppearanceControls appearance = new AppearanceControls(container, button);

        TextView sensitivityValue = new TextView(context);
        container.addView(sensitivityValue);
        SeekBar sensitivity = new SeekBar(context);
        sensitivity.setMax(290);
        sensitivity.setProgress(button.getSensitivityPercent() - 10);
        Runnable updateSensitivity = () -> sensitivityValue.setText(context.getString(
                R.string.touchkit_gyro_sensitivity_value, 10 + sensitivity.getProgress()));
        updateSensitivity.run();
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                    boolean fromUser) {
                updateSensitivity.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        container.addView(sensitivity);

        TextView deadzoneValue = new TextView(context);
        container.addView(deadzoneValue);
        SeekBar deadzone = new SeekBar(context);
        deadzone.setMax(50);
        deadzone.setProgress(Math.round(button.getDeadzoneDegreesPerSecond() * 10f));
        Runnable updateDeadzone = () -> deadzoneValue.setText(context.getString(
                R.string.touchkit_gyro_deadzone_value, deadzone.getProgress() / 10f));
        updateDeadzone.run();
        deadzone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                    boolean fromUser) {
                updateDeadzone.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        container.addView(deadzone);

        CheckBox invertHorizontal = new CheckBox(context);
        invertHorizontal.setText(R.string.touchkit_gyro_invert_horizontal);
        invertHorizontal.setChecked(button.isInvertHorizontal());
        container.addView(invertHorizontal);

        CheckBox invertVertical = new CheckBox(context);
        invertVertical.setText(R.string.touchkit_gyro_invert_vertical);
        invertVertical.setChecked(button.isInvertVertical());
        container.addView(invertVertical);

        TextView help = new TextView(context);
        help.setText(button.isGyroscopeAvailable()
                ? R.string.touchkit_gyro_editor_help
                : R.string.touchkit_gyro_unavailable);
        container.addView(help);

        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_gyro_editor_title)
                .setView(createScrollableEditorView(container))
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    button.setActive(false);
                    button.setSensitivityPercent(10 + sensitivity.getProgress());
                    button.setDeadzoneDegreesPerSecond(deadzone.getProgress() / 10f);
                    button.setInvertHorizontal(invertHorizontal.isChecked());
                    button.setInvertVertical(invertVertical.isChecked());
                    appearance.apply(button);
                    KeyBoardControllerConfigurationLoader.saveProfile(this, context);
                })
                .setNeutralButton(R.string.touchkit_delete_control,
                        (dialog, which) -> deleteElementFromLayout(button))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSoftKeyboardButtonEditor(TouchKitSoftKeyboardButton button) {
        LinearLayout container = createEditorContainer();
        float density = context.getResources().getDisplayMetrics().density;
        int minSize = (int) (24 * density);
        int availableWidth = frame_layout.getWidth() > 0
                ? frame_layout.getWidth() : context.getResources().getDisplayMetrics().widthPixels;
        int maxSize = Math.max(minSize,
                Math.min(availableWidth / 2, (int) (220 * density)));

        TextView sizeValue = new TextView(context);
        container.addView(sizeValue);
        SeekBar size = new SeekBar(context);
        size.setMax(maxSize - minSize);
        size.setProgress(Math.max(0, Math.min(size.getMax(),
                Math.max(button.getWidth(), button.getHeight()) - minSize)));
        Runnable updateSize = () -> sizeValue.setText(context.getString(
                R.string.touchkit_editor_size_value,
                Math.round((minSize + size.getProgress()) / density)));
        updateSize.run();
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                    boolean fromUser) {
                updateSize.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        container.addView(size);

        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_soft_keyboard)
                .setView(createScrollableEditorView(container))
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    int px = minSize + size.getProgress();
                    FrameLayout.LayoutParams params =
                            (FrameLayout.LayoutParams) button.getLayoutParams();
                    params.width = px;
                    params.height = px;
                    button.requestLayout();
                    KeyBoardControllerConfigurationLoader.saveProfile(this, context);
                })
                .setNeutralButton(R.string.touchkit_delete_control,
                        (dialog, which) -> deleteElementFromLayout(button))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDigitalButtonEditor(KeyBoardDigitalButton button) {
        LinearLayout container = createEditorContainer();

        EditText bindingLine = new EditText(context);
        bindingLine.setHint(R.string.touchkit_editor_binding_line_hint);
        bindingLine.setSingleLine(true);
        String actualBinding = button instanceof TouchKitComboButton
                ? ((TouchKitComboButton) button).getComboSpec()
                : button.hasEditableKeyBinding()
                        ? TouchKitKeyBindingParser.keyCodeName(button.getEditableKeyCode())
                        : button.getText();
        bindingLine.setText(button.getDescription().isEmpty()
                ? actualBinding : actualBinding + "=" + button.getDescription());
        container.addView(bindingLine);

        TextView bindingHelp = new TextView(context);
        bindingHelp.setText(R.string.touchkit_editor_binding_line_help);
        container.addView(bindingHelp);

        CheckBox physicalKeyNames = new CheckBox(context);
        physicalKeyNames.setText(R.string.touchkit_show_physical_key_names);
        physicalKeyNames.setChecked(button.getShowPhysicalKeyNames());
        physicalKeyNames.setVisibility(button instanceof TouchKitComboButton ||
                button.hasEditableKeyBinding() ? View.VISIBLE : View.GONE);
        container.addView(physicalKeyNames);

        TextView triggerLabel = new TextView(context);
        triggerLabel.setText(R.string.touchkit_trigger_mode_label);
        container.addView(triggerLabel);

        Spinner trigger = new Spinner(context);
        String[] triggerItems = {
                context.getString(R.string.touchkit_trigger_hold),
                context.getString(R.string.touchkit_trigger_toggle),
                context.getString(R.string.touchkit_trigger_timed_hold)
        };
        trigger.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, triggerItems));
        trigger.setSelection(button.getTriggerMode() ==
                KeyBoardDigitalButton.TriggerMode.TOGGLE ? 1 :
                button.getTriggerMode() == KeyBoardDigitalButton.TriggerMode.TIMED_HOLD ? 2 : 0);
        container.addView(trigger);

        TextView timedHoldValue = new TextView(context);
        container.addView(timedHoldValue);
        SeekBar timedHoldDuration = new SeekBar(context);
        timedHoldDuration.setMax(49);
        timedHoldDuration.setProgress(button.getTimedHoldDurationMs() / 100 - 1);
        Runnable updateTimedHold = () -> timedHoldValue.setText(context.getString(
                R.string.touchkit_timed_hold_value,
                (timedHoldDuration.getProgress() + 1) / 10f));
        Runnable updateTimedHoldVisibility = () -> {
            int visibility = trigger.getSelectedItemPosition() == 2 ? View.VISIBLE : View.GONE;
            timedHoldValue.setVisibility(visibility);
            timedHoldDuration.setVisibility(visibility);
        };
        timedHoldDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                    boolean fromUser) {
                updateTimedHold.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        trigger.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                updateTimedHoldVisibility.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        updateTimedHold.run();
        updateTimedHoldVisibility.run();
        container.addView(timedHoldDuration);

        TouchKitMouseScrollButton scrollButton = button instanceof TouchKitMouseScrollButton
                ? (TouchKitMouseScrollButton) button : null;
        SeekBar scrollStep = null;
        CheckBox continuousScroll = null;
        if (scrollButton != null) {
            triggerLabel.setVisibility(View.GONE);
            trigger.setVisibility(View.GONE);
            timedHoldValue.setVisibility(View.GONE);
            timedHoldDuration.setVisibility(View.GONE);
            TextView scrollStepValue = new TextView(context);
            container.addView(scrollStepValue);
            scrollStep = new SeekBar(context);
            scrollStep.setMax(19);
            scrollStep.setProgress(scrollButton.getScrollStep() - 1);
            SeekBar finalScrollStep = scrollStep;
            Runnable updateScrollStep = () -> scrollStepValue.setText(context.getString(
                    R.string.touchkit_scroll_step_value, finalScrollStep.getProgress() + 1));
            updateScrollStep.run();
            scrollStep.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                        boolean fromUser) {
                    updateScrollStep.run();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            container.addView(scrollStep);

            continuousScroll = new CheckBox(context);
            continuousScroll.setText(R.string.touchkit_scroll_continuous);
            continuousScroll.setChecked(scrollButton.isContinuousScroll());
            container.addView(continuousScroll);
        }
        SeekBar finalScrollStepControl = scrollStep;
        CheckBox finalContinuousScroll = continuousScroll;

        TextView sizeValue = new TextView(context);
        container.addView(sizeValue);
        SeekBar size = new SeekBar(context);
        int minSize = (int) (24 * context.getResources().getDisplayMetrics().density);
        int maxSize = Math.max(minSize, Math.min(frame_layout.getWidth() / 3,
                (int) (220 * context.getResources().getDisplayMetrics().density)));
        int currentSize = Math.max(button.getWidth(), button.getHeight());
        size.setMax(maxSize - minSize);
        size.setProgress(Math.max(0, Math.min(size.getMax(), currentSize - minSize)));
        Runnable updateSizeLabel = () -> sizeValue.setText(context.getString(
                R.string.touchkit_editor_size_value,
                Math.round((minSize + size.getProgress()) /
                        context.getResources().getDisplayMetrics().density)));
        updateSizeLabel.run();
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSizeLabel.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        container.addView(size);

        TextView opacityValue = new TextView(context);
        container.addView(opacityValue);
        SeekBar opacity = new SeekBar(context);
        opacity.setMax(80);
        opacity.setProgress(Math.max(0, button.getBackgroundOpacity() - 20));
        Runnable updateOpacityLabel = () -> opacityValue.setText(context.getString(
                R.string.touchkit_editor_opacity_value, 20 + opacity.getProgress()));
        updateOpacityLabel.run();
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOpacityLabel.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        container.addView(opacity);

        Spinner shape = new Spinner(context);
        String[] shapeItems = {
                context.getString(R.string.touchkit_editor_shape_circle),
                context.getString(R.string.touchkit_editor_shape_rounded)
        };
        shape.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, shapeItems));
        shape.setSelection(button.getButtonShape() ==
                KeyBoardDigitalButton.ButtonShape.CIRCLE ? 0 : 1);
        container.addView(shape);

        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_editor_button_title)
                .setView(createScrollableEditorView(container))
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String line = bindingLine.getText().toString().trim();
                    TouchKitKeyBindingParser.BindingLine parsedLine =
                            button instanceof TouchKitComboButton ||
                                    button.hasEditableKeyBinding()
                                    ? TouchKitKeyBindingParser.splitBindingLine(line)
                                    : TouchKitKeyBindingParser.splitFixedBindingLine(
                                            line, actualBinding);
                    String label = parsedLine.keySpec;
                    String description = parsedLine.description;
                    if (!label.isEmpty()) {
                        if (button instanceof TouchKitComboButton) {
                            if (!((TouchKitComboButton) button).setComboSpec(label)) {
                                Toast.makeText(context, R.string.touchkit_editor_invalid_key,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                button.setShowPhysicalKeyNames(physicalKeyNames.isChecked());
                            }
                        } else if (!button.hasEditableKeyBinding()) {
                            button.setText(label);
                        } else if (button.setEditableKeyName(label)) {
                            button.setShowPhysicalKeyNames(physicalKeyNames.isChecked());
                        } else {
                            Toast.makeText(context, R.string.touchkit_editor_invalid_key,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    button.setDescription(description);
                    button.setButtonShape(shape.getSelectedItemPosition() == 0
                            ? KeyBoardDigitalButton.ButtonShape.CIRCLE
                            : KeyBoardDigitalButton.ButtonShape.ROUNDED_RECT);
                    int triggerPosition = trigger.getSelectedItemPosition();
                    button.setTriggerMode(triggerPosition == 1
                            ? KeyBoardDigitalButton.TriggerMode.TOGGLE
                            : triggerPosition == 2
                                    ? KeyBoardDigitalButton.TriggerMode.TIMED_HOLD
                                    : KeyBoardDigitalButton.TriggerMode.HOLD);
                    button.setTimedHoldDurationMs(
                            (timedHoldDuration.getProgress() + 1) * 100);
                    if (scrollButton != null) {
                        scrollButton.setScrollStep(finalScrollStepControl.getProgress() + 1);
                        scrollButton.setContinuousScroll(finalContinuousScroll.isChecked());
                        scrollButton.setTriggerMode(KeyBoardDigitalButton.TriggerMode.HOLD);
                    }
                    button.setBackgroundOpacity(20 + opacity.getProgress());
                    int px = minSize + size.getProgress();
                    FrameLayout.LayoutParams params =
                            (FrameLayout.LayoutParams) button.getLayoutParams();
                    params.width = px;
                    params.height = px;
                    button.requestLayout();
                    KeyBoardControllerConfigurationLoader.saveProfile(this, context);
                })
                .setNeutralButton(R.string.touchkit_delete_control,
                        (dialog, which) -> deleteElementFromLayout(button))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRadialMenuEditor(TouchKitRadialMenuButton radialMenu) {
        LinearLayout container = createEditorContainer();
        AppearanceControls appearance = new AppearanceControls(container, radialMenu);

        EditText name = new EditText(context);
        name.setHint(R.string.touchkit_radial_name);
        name.setSingleLine(true);
        name.setText(radialMenu.getRadialName());
        container.addView(name);

        TextView help = new TextView(context);
        help.setText(R.string.touchkit_radial_spec_help);
        container.addView(help);

        EditText spec = new EditText(context);
        spec.setMinLines(6);
        spec.setGravity(Gravity.TOP);
        spec.setText(radialMenu.getActionSpec());
        container.addView(spec);

        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_radial_editor_title)
                .setView(createScrollableEditorView(container))
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    radialMenu.setRadialName(name.getText().toString());
                    if (!radialMenu.setActionSpec(spec.getText().toString())) {
                        Toast.makeText(context, R.string.touchkit_radial_spec_error,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    appearance.apply(radialMenu);
                    KeyBoardControllerConfigurationLoader.saveProfile(this, context);
                })
                .setNeutralButton(R.string.touchkit_delete_control,
                        (dialog, which) -> deleteElementFromLayout(radialMenu))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showStickEditor(keyBoardVirtualControllerElement element,
                                 TouchKitConfigurableStick stick) {
        LinearLayout container = createEditorContainer();
        AppearanceControls appearance = new AppearanceControls(container, element);
        TextView help = new TextView(context);
        help.setText(R.string.touchkit_stick_spec_help);
        container.addView(help);

        EditText spec = new EditText(context);
        spec.setMinLines(5);
        spec.setGravity(Gravity.TOP);
        spec.setText(stick.getBindingSpec());
        container.addView(spec);

        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_stick_editor_title)
                .setView(createScrollableEditorView(container))
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    if (!stick.setBindingSpec(spec.getText().toString())) {
                        Toast.makeText(context, R.string.touchkit_stick_spec_error,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    appearance.apply(element);
                    element.invalidate();
                    KeyBoardControllerConfigurationLoader.saveProfile(this, context);
                })
                .setNeutralButton(R.string.touchkit_delete_control,
                        (dialog, which) -> deleteElementFromLayout(element))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteElementFromLayout(keyBoardVirtualControllerElement element) {
        KeyBoardControllerConfigurationLoader.deleteElement(context, element.elementId);
        removeElement(element);
    }

    public void hide(boolean temporary) {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setVisibility(View.GONE);
        }

        buttonConfigure.setVisibility(View.GONE);
        if (!temporary) {
            shown = false;
        };
    }

    public void hide() {
        hide(false);
    }

    public void show() {
        showEnabledElements();
        buttonConfigure.setVisibility(View.VISIBLE);
        shown = true;
    }

    public void showElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            // In configuration mode, show all non-hidden elements
            if (currentMode == ControllerMode.DisableEnableButtons) {
                element.setVisibility(element.hidden ? View.GONE : View.VISIBLE);
            } else {
                element.setVisibility((element.hidden || !element.enabled) ? View.GONE : View.VISIBLE);
            }
        }
    }

    public void showEnabledElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            // In configuration mode, show all non-hidden elements
            if (currentMode == ControllerMode.DisableEnableButtons) {
                element.setVisibility(element.hidden ? View.GONE : View.VISIBLE);
            } else {
                element.setVisibility((!element.hidden && element.enabled) ? View.VISIBLE : View.GONE);
            }
        }
    }

    public void toggleVisibility() {
        if (buttonConfigure.getVisibility() == View.VISIBLE) {
            hide();
        } else {
            show();
        }
    }

    public void removeElements() {
        for (keyBoardVirtualControllerElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();
        synchronized (activeControlPointerCounts) {
            activeControlPointerCounts.clear();
        }

        frame_layout.removeView(buttonConfigure);
        frame_layout.removeView(buttonClearAll);
        frame_layout.removeView(buttonAddKeys);
    }

    public void setOpacity(int opacity) {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setOpacity(opacity);
        }
    }

    public void addElement(keyBoardVirtualControllerElement element, int x, int y, int width, int height) {
        applyGlobalAppearance(context, element);
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    static void applyGlobalAppearance(Context context,
                                      keyBoardVirtualControllerElement element) {
        SharedPreferences appearance = PreferenceManager.getDefaultSharedPreferences(context);
        element.setOpacity(appearance.getInt(TOUCHKIT_BACKGROUND_OPACITY_PREF, 100));
        element.setGlobalForegroundOpacity(
                appearance.getInt(TOUCHKIT_FOREGROUND_OPACITY_PREF, 100));
    }

    public void setGlobalForegroundOpacity(int opacity) {
        for (keyBoardVirtualControllerElement element : elements) {
            element.setGlobalForegroundOpacity(opacity);
        }
    }

    keyBoardVirtualControllerElement findElement(String elementId) {
        for (keyBoardVirtualControllerElement element : elements) {
            if (element.elementId.equals(elementId)) return element;
        }
        return null;
    }

    void removeElement(keyBoardVirtualControllerElement element) {
        elements.remove(element);
        frame_layout.removeView(element);
    }

    public List<keyBoardVirtualControllerElement> getElements() {
        return elements;
    }

    int getLayoutWidth() {
        int width = frame_layout.getWidth();
        return width > 0 ? width : context.getResources().getDisplayMetrics().widthPixels;
    }

    int getLayoutHeight() {
        int height = frame_layout.getHeight();
        return height > 0 ? height : context.getResources().getDisplayMetrics().heightPixels;
    }

    private static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
            LimeLog.info("VirtualController: " + text);
        }
    }

    public void refreshLayout() {
        removeElements();

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int buttonSize = (int) (screen.heightPixels * 0.06f);

        // Configure button at original position
        FrameLayout.LayoutParams configParams = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        configParams.leftMargin = 20 + buttonSize;
        configParams.topMargin = 15;
        frame_layout.addView(buttonConfigure, configParams);

        // Measure the widths of both buttons
        buttonClearAll.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        buttonAddKeys.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int clearAllWidth = buttonClearAll.getMeasuredWidth();
        int addKeysWidth = buttonAddKeys.getMeasuredWidth();

        // Calculate center positions
        int totalWidth = clearAllWidth + addKeysWidth + 3; // 3 pixels spacing
        int screenCenter = screen.widthPixels / 2;
        int startX = screenCenter - (totalWidth / 2);

        // Clear All button
        FrameLayout.LayoutParams clearParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        clearParams.leftMargin = startX;
        clearParams.topMargin = 15;
        frame_layout.addView(buttonClearAll, clearParams);

        // Add Keys button
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        addParams.leftMargin = startX + clearAllWidth + 3; // Position right after Clear All with 3px spacing
        addParams.topMargin = 15;
        frame_layout.addView(buttonAddKeys, addParams);

        // Apply default layout
        KeyBoardControllerConfigurationLoader.createDefaultLayout(this, context, conn);
        KeyBoardControllerConfigurationLoader.applyDeletedBaseElements(this, context);
        KeyBoardControllerConfigurationLoader.createDynamicElements(this, context, conn);
        KeyBoardControllerConfigurationLoader.loadFromPreferences(this, context);
        setGlobalForegroundOpacity(PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(TOUCHKIT_FOREGROUND_OPACITY_PREF, 100));
    }

    public ControllerMode getControllerMode() {
        return currentMode;
    }

    public void sendKeyEvent(KeyEvent keyEvent) {
        if (!inputDispatchEnabled || Game.instance == null || !Game.instance.connected) {
            return;
        }
        //1-鼠标 0-按键 2-摇杆 3-十字键
        if (keyEvent.getSource() == 1) {
            Game.instance.mouseButtonEvent(keyEvent.getKeyCode(), KeyEvent.ACTION_DOWN == keyEvent.getAction());
        } else {
            int keyCode = keyEvent.getKeyCode();
            int count = activeKeyboardKeyCounts.containsKey(keyCode)
                    ? activeKeyboardKeyCounts.get(keyCode) : 0;
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                activeKeyboardKeyCounts.put(keyCode, count + 1);
                if (count == 0) {
                    Game.instance.onKey(null, keyCode, keyEvent);
                }
            } else if (keyEvent.getAction() == KeyEvent.ACTION_UP && count > 0) {
                if (count == 1) {
                    activeKeyboardKeyCounts.remove(keyCode);
                    Game.instance.onKey(null, keyCode, keyEvent);
                } else {
                    activeKeyboardKeyCounts.put(keyCode, count - 1);
                }
            }
        }

        if (keyEvent.getSource() != 2) {
            vibrate(keyEvent.getAction());
        }
    }

    void openSoftKeyboard() {
        if (inputDispatchEnabled && Game.instance != null) {
            Game.instance.toggleKeyboard();
        }
    }

    public void sendMouseMove(int x,int y){
        if (!inputDispatchEnabled || Game.instance == null || !Game.instance.connected) {
            return;
        }
        int maximumContinuousDelta = frame_layout == null
                ? 256 : Math.max(96, Math.min(frame_layout.getWidth(), frame_layout.getHeight()) / 4);
        if (Math.abs(x) > maximumContinuousDelta || Math.abs(y) > maximumContinuousDelta) {
            return;
        }
        Game.instance.mouseMove(x,y);
    }

    public void sendMouseScroll(int clicks) {
        if (!inputDispatchEnabled || Game.instance == null || !Game.instance.connected) {
            return;
        }
        Game.instance.mouseVScroll((byte) Math.max(-127, Math.min(127, clicks)));
    }

    public void vibrate(int action) {
        if (PreferenceConfiguration.readPreferences(context).enableKeyboardVibrate && vibrator.hasVibrator()) {
            switch (action) {
                case KeyEvent.ACTION_DOWN:
                    frame_layout.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    break;
                case KeyEvent.ACTION_UP:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        frame_layout.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE);
                    } else {
                        frame_layout.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    break;
                default:
                    frame_layout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
    }

    private void showControlButtons(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        buttonClearAll.setVisibility(visibility);
        buttonAddKeys.setVisibility(visibility);
    }

    private void showKeySelectionDialog() {
        String[] categories = {
                context.getString(R.string.touchkit_key_category_keyboard),
                context.getString(R.string.touchkit_key_category_mouse),
                context.getString(R.string.touchkit_key_category_function)
        };
        new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_key_category_title)
                .setItems(categories, (dialog, which) -> showKeySelectionDialog(which))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showKeySelectionDialog(int category) {
        try {
            InputStream is = context.getAssets().open("config/keyboard.json");
            int length = is.available();
            byte[] buffer = new byte[length];
            is.read(buffer);
            is.close();
            String jsonConfig = new String(buffer, "utf8");

            JSONObject json = new JSONObject(jsonConfig);
            JSONObject data = json.getJSONObject("data");
            JSONArray keystrokeList = data.getJSONArray("keystroke");
            JSONArray mouseList = data.getJSONArray("mouse");
            JSONArray rockerList = data.getJSONArray("rocker");
            JSONArray dpadList = data.getJSONArray("dpad");

            List<JSONObject> allItemsList = new ArrayList<>();
            List<String> keyNamesList = new ArrayList<>();

            if (category == 0) {
                for (int i = 0; i < keystrokeList.length(); i++) {
                    JSONObject key = keystrokeList.getJSONObject(i);
                    key.put("type", 0);
                    allItemsList.add(key);
                    keyNamesList.add(key.getString("name"));
                }
            }

            if (category == 1) {
                for (int i = 0; i < mouseList.length(); i++) {
                    JSONObject obj = mouseList.getJSONObject(i);
                    obj.put("type", 1);
                    allItemsList.add(obj);
                    int code = obj.optInt("code");
                    int label = KeyBoardControllerConfigurationLoader
                            .getTouchKitMouseLabelResource(code);
                    if (obj.optInt("switchButton") == 0 && label != 0) {
                        keyNamesList.add(context.getString(label));
                    } else {
                        keyNamesList.add(obj.getString("name"));
                    }
                }
            }

            if (category == 2) {
                for (int i = 0; i < rockerList.length(); i++) {
                    JSONObject obj = rockerList.getJSONObject(i);
                    obj.put("type", 2);
                    allItemsList.add(obj);
                    keyNamesList.add(context.getString(R.string.touchkit_control_joystick));
                }
                for (int i = 0; i < dpadList.length(); i++) {
                    JSONObject obj = dpadList.getJSONObject(i);
                    obj.put("type", 3);
                    allItemsList.add(obj);
                    keyNamesList.add(context.getString(R.string.touchkit_control_dpad));
                }
            }

            if (category == 2) {
                JSONObject radial = new JSONObject();
                radial.put("type", 5);
                radial.put("name", context.getString(R.string.touchkit_add_radial));
                radial.put("elementId", "touchkit_radial_1");
                allItemsList.add(radial);
                keyNamesList.add(radial.getString("name"));

                JSONObject combo = new JSONObject();
                combo.put("type", 7);
                combo.put("name", context.getString(R.string.touchkit_add_combo));
                combo.put("elementId", "touchkit_combo");
                allItemsList.add(combo);
                keyNamesList.add(combo.getString("name"));

                JSONObject softKeyboard = new JSONObject();
                softKeyboard.put("type", 8);
                softKeyboard.put("name", context.getString(R.string.touchkit_soft_keyboard));
                softKeyboard.put("elementId", "touchkit_soft_keyboard");
                allItemsList.add(softKeyboard);
                keyNamesList.add(softKeyboard.getString("name"));

                JSONObject gyroMouse = new JSONObject();
                gyroMouse.put("type", 9);
                gyroMouse.put("name", context.getString(R.string.touchkit_gyro_control));
                gyroMouse.put("elementId", "touchkit_gyro_mouse");
                allItemsList.add(gyroMouse);
                keyNamesList.add(gyroMouse.getString("name"));
            }

            if (category == 1) {
                JSONObject scrollUp = new JSONObject();
                scrollUp.put("type", 6);
                scrollUp.put("name", context.getString(R.string.touchkit_mouse_scroll_up));
                scrollUp.put("elementId", "touchkit_scroll_up");
                scrollUp.put("scroll", 1);
                allItemsList.add(scrollUp);
                keyNamesList.add(scrollUp.getString("name"));

                JSONObject scrollDown = new JSONObject();
                scrollDown.put("type", 6);
                scrollDown.put("name", context.getString(R.string.touchkit_mouse_scroll_down));
                scrollDown.put("elementId", "touchkit_scroll_down");
                scrollDown.put("scroll", -1);
                allItemsList.add(scrollDown);
                keyNamesList.add(scrollDown.getString("name"));
            }

            // Load and add custom keys
            android.content.SharedPreferences preferences = context.getSharedPreferences(GameMenu.PREF_NAME, Context.MODE_PRIVATE);
            String value = preferences.getString(GameMenu.KEY_NAME, "");

            if (category == 2 && !TextUtils.isEmpty(value)) {
                try {
                    KeyConfigHelper.ShortcutFile shortcutFile = KeyConfigHelper.parseShortcutFile(value);
                    if (shortcutFile != null && shortcutFile.data != null && !shortcutFile.data.isEmpty()) {
                        List<KeyConfigHelper.Shortcut> shortcutData = shortcutFile.data;
                        for (int idx = 0; idx < shortcutData.size(); idx++) {
                            KeyConfigHelper.Shortcut sc = shortcutData.get(idx);

                            String id = (sc.id == null || sc.id.isEmpty()) ? Integer.toString(idx) : sc.id;
                            String name = sc.name;

                            JSONObject customKey = new JSONObject();
                            customKey.put("type", 4); // Custom key type
                            customKey.put("name", name);
                            customKey.put("elementId", "custom_" + id);
                            customKey.put("sticky", sc.sticky);

                            JSONArray keyCodesJson = new JSONArray();
                            for (String code : sc.keys) {
                                keyCodesJson.put(code);
                            }
                            customKey.put("keys", keyCodesJson);

                            allItemsList.add(customKey);
                            keyNamesList.add(context.getString(R.string.keyboard_key_custom, name));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(context, "Error loading custom keys: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            String[] keyNames = keyNamesList.toArray(new String[0]);
            boolean[] checkedItems = new boolean[keyNames.length];

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.keyboard_select_keys));
            builder.setMultiChoiceItems(keyNames, checkedItems, (dialog, which, isChecked) -> {
                checkedItems[which] = isChecked;
            });

            builder.setPositiveButton(context.getString(R.string.keyboard_add), (dialog, which) -> {
                DisplayMetrics screen = context.getResources().getDisplayMetrics();
                int height = screen.heightPixels;

                // Calculate button size using the same logic as createDefaultLayout
                int BUTTON_SIZE = 10;
                int w = KeyBoardControllerConfigurationLoader.screenScale(BUTTON_SIZE, height);
                int maxW = screen.widthPixels / 18;

                if (w > maxW) {
                    BUTTON_SIZE = KeyBoardControllerConfigurationLoader.screenScaleSwitch(maxW, height);
                    w = KeyBoardControllerConfigurationLoader.screenScale(BUTTON_SIZE, height);
                }

                Map<String, keyBoardVirtualControllerElement> existingElements = new HashMap<>();
                List<Rect> existingPositions = new ArrayList<>();
                for (keyBoardVirtualControllerElement element : elements) {
                    existingElements.put(element.elementId, element);
                    if (element.getVisibility() != View.GONE) {
                        // Create a set of existing element IDs for quick lookup
                        // Get current element positions to avoid overlap - include ALL elements
                        try {
                            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) element.getLayoutParams();
                            if (params != null) {
                                existingPositions.add(new Rect(
                                        params.leftMargin,
                                        params.topMargin,
                                        params.leftMargin + params.width,
                                        params.topMargin + params.height
                                ));
                            }
                        } catch (ClassCastException e) {
                            // If element doesn't have FrameLayout.LayoutParams, try to get position another way
                            existingPositions.add(new Rect(
                                    (int) element.getX(),
                                    (int) element.getY(),
                                    (int) element.getX() + element.getWidth(),
                                    (int) element.getY() + element.getHeight()
                            ));
                        }
                    }
                }

                int elementsAdded = 0;
                int duplicatesFound = 0;
                TouchKitComboButton comboToConfigure = null;
                // Add selected keys
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) {
                        try {
                            JSONObject obj = allItemsList.get(i);
                            int type = obj.optInt("type", 0);

                            // Determine elementId to check for duplicates
                            String elementId;
                            if (type == 7) {
                                elementId = findAvailableComboElementId(existingElements);
                                if (elementId == null) {
                                    elementId = "touchkit_combo_1";
                                }
                            } else if (type >= 2) { // Rocker, D-Pad, custom, radial, or scroll
                                elementId = obj.getString("elementId");
                            } else { // Keyboard or Mouse
                                int code = obj.getInt("code");
                                int switchButton = obj.optInt("switchButton", 0);
                                elementId = type == 0 ? "key_" + code : "m_" + code;
                                if (switchButton == 1) {
                                    elementId = type == 0 ? "key_s_" + code : "m_s_" + code;
                                }
                            }

                            // Calculate element size based on type
                            int elementSize = type == 5 ? w * 3 :
                                    ((type == 2 || type == 3) ? (int)(w * 2.5) : w);

                            // Find non-overlapping position
                            Point position = findNonOverlappingPosition(existingPositions, elementSize);

                            keyBoardVirtualControllerElement existing = existingElements.get(elementId);
                            if (existing != null) {
                                if (!existing.enabled || existing.hidden) {
                                    existing.enabled = true;
                                    existing.hidden = false;
                                    FrameLayout.LayoutParams params =
                                            (FrameLayout.LayoutParams) existing.getLayoutParams();
                                    params.leftMargin = position.x;
                                    params.topMargin = position.y;
                                    params.width = elementSize;
                                    params.height = elementSize;
                                    existing.setVisibility(View.VISIBLE);
                                    existing.requestLayout();
                                    existingPositions.add(new Rect(position.x, position.y,
                                            position.x + elementSize, position.y + elementSize));
                                    elementsAdded++;
                                    if (existing instanceof TouchKitComboButton) {
                                        comboToConfigure = (TouchKitComboButton) existing;
                                    }
                                    continue;
                                }
                                String dynamicId = KeyBoardControllerConfigurationLoader
                                        .createDynamicElementId(elementId);
                                JSONObject descriptor = new JSONObject(obj.toString());
                                descriptor.put("elementId", dynamicId);
                                keyBoardVirtualControllerElement duplicate =
                                        KeyBoardControllerConfigurationLoader
                                                .createElementFromDescriptor(descriptor, this,
                                                        context, conn);
                                addElement(duplicate, position.x, position.y,
                                        elementSize, elementSize);
                                KeyBoardControllerConfigurationLoader.recordDynamicElement(
                                        context, descriptor, dynamicId);
                                existingPositions.add(new Rect(position.x, position.y,
                                        position.x + elementSize, position.y + elementSize));
                                existingElements.put(dynamicId, duplicate);
                                elementsAdded++;
                                if (duplicate instanceof TouchKitComboButton) {
                                    comboToConfigure = (TouchKitComboButton) duplicate;
                                }
                                continue;
                            }

                            keyBoardVirtualControllerElement newElement = null;

                            if (type == 4) { // Custom Key
                                String name = obj.getString("name");
                                boolean sticky = obj.getBoolean("sticky");
                                JSONArray keysJson = obj.getJSONArray("keys");

                                short[] vkKeyCodes = new short[keysJson.length()];
                                for (int j = 0; j < keysJson.length(); j++) {
                                    String code = keysJson.getString(j);
                                    int keycode;
                                    if (code.startsWith("0x")) {
                                        keycode = Integer.parseInt(code.substring(2), 16);
                                    } else if (code.startsWith("VK_")) {
                                        Field field = KeyMapper.class.getDeclaredField(code);
                                        keycode = field.getInt(null);
                                    } else {
                                        throw new IllegalArgumentException("Unknown key code: " + code);
                                    }
                                    vkKeyCodes[j] = (short) keycode;
                                }

                                newElement = KeyBoardControllerConfigurationLoader.createCustomButton(
                                        elementId, vkKeyCodes, 1, name, -1, sticky, this, conn, context
                                );
                                addElement(newElement, position.x, position.y, w, w);

                            } else if (type == 2) { // Rocker (joystick)
                                int[] keys = new int[]{
                                    obj.getInt("upCode"),
                                    obj.getInt("downCode"),
                                    obj.getInt("leftCode"),
                                    obj.getInt("rightCode"),
                                    obj.getInt("middleCode")
                                };

                                newElement = KeyBoardControllerConfigurationLoader.createKeyBoardAnalogStickButton(
                                    this, elementId, context, keys);
                                addElement(newElement, position.x, position.y, elementSize, elementSize);

                            } else if (type == 3) { // D-pad
                                newElement = KeyBoardControllerConfigurationLoader.createDiaitalPadButton(
                                    elementId,
                                    obj.getInt("leftCode"),
                                    obj.getInt("rightCode"),
                                    obj.getInt("upCode"),
                                    obj.getInt("downCode"),
                                    this, context);
                                addElement(newElement, position.x, position.y, elementSize, elementSize);
                            } else if (type == 5) { // TouchKit radial menu
                                newElement = KeyBoardControllerConfigurationLoader.createRadialMenuButton(
                                        elementId, this, context);
                                addElement(newElement, position.x, position.y, elementSize, elementSize);
                            } else if (type == 6) { // Mouse wheel step
                                newElement = KeyBoardControllerConfigurationLoader.createMouseScrollButton(
                                        elementId, obj.getString("name"), obj.getInt("scroll"),
                                        this, context);
                                addElement(newElement, position.x, position.y, w, w);
                            } else if (type == 7) { // Standalone key combination
                                newElement = KeyBoardControllerConfigurationLoader.createComboButton(
                                        elementId, this, context);
                                addElement(newElement, position.x, position.y, w, w);
                                comboToConfigure = (TouchKitComboButton) newElement;

                            } else if (type == 8) { // Artemis soft keyboard
                                newElement = KeyBoardControllerConfigurationLoader
                                        .createSoftKeyboardButton(elementId, this, context);
                                addElement(newElement, position.x, position.y, w, w);

                            } else if (type == 9) { // Lockable gyroscope mouse aiming
                                newElement = KeyBoardControllerConfigurationLoader
                                        .createGyroMouseButton(elementId, this, context);
                                addElement(newElement, position.x, position.y, w, w);

                            } else {
                                String name = obj.getString("name");
                                int code = obj.getInt("code");
                                int mouseLabel = type == 1
                                        ? KeyBoardControllerConfigurationLoader
                                                .getTouchKitMouseLabelResource(code)
                                        : 0;
                                if (mouseLabel != 0) {
                                    name = context.getString(mouseLabel);
                                }

                                int effectiveCode = type == 1 && code >= 9 && code <= 11
                                        ? (code == 9 ? 3 : 1) : code;
                                int effectiveMouseLabel = type == 1
                                        ? KeyBoardControllerConfigurationLoader
                                                .getTouchKitMouseLabelResource(effectiveCode)
                                        : 0;
                                if (effectiveMouseLabel != 0) {
                                    name = context.getString(effectiveMouseLabel);
                                }
                                newElement = KeyBoardControllerConfigurationLoader.createDigitalButton(
                                        elementId, effectiveCode, type, 1, name,
                                        type == 1 ? KeyBoardControllerConfigurationLoader
                                                .getTouchKitMouseIconResource(effectiveCode) : -1,
                                        PreferenceConfiguration.readPreferences(context).stickyModifierKey &&
                                        KeyBoardControllerConfigurationLoader.isModifierKey(effectiveCode),
                                        this, context);
                                addElement(newElement, position.x, position.y, w, w);
                            }

                            // Add the new element's position to the existing positions list
                            existingPositions.add(new Rect(position.x, position.y,
                                position.x + elementSize, position.y + elementSize));

                            KeyBoardControllerConfigurationLoader.restoreDeletedBaseElement(
                                    context, elementId);

                            // Add the new elementId to the set to prevent adding it twice in the same operation

                            elementsAdded++;
                            vibrate(KeyEvent.ACTION_DOWN);

                        } catch (JSONException e) {
                            LimeLog.warning("Error adding key: " + e.getMessage());
                            e.printStackTrace();
                        } catch (Exception e) {
                            LimeLog.warning("Unexpected error adding key: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }

                // Build feedback message
                StringBuilder feedback = new StringBuilder();
                if (elementsAdded > 0) {
                    KeyBoardControllerConfigurationLoader.saveProfile(KeyBoardController.this, context);
                    setControllerMode(ControllerMode.EditProperties);
                    feedback.append(context.getString(R.string.keyboard_keys_added, elementsAdded));
                }
                if (duplicatesFound > 0) {
                    if (feedback.length() > 0) {
                        feedback.append("\n");
                    }
                    feedback.append(context.getString(R.string.keyboard_duplicates_skipped, duplicatesFound));
                }

                if (feedback.length() > 0) {
                    Toast.makeText(context, feedback.toString(), Toast.LENGTH_LONG).show();
                }
                if (comboToConfigure != null) {
                    showComboCreationDialog(comboToConfigure);
                }
            });

            builder.setNegativeButton(context.getString(R.string.cancel), null);
            builder.show();

        } catch (Exception e) {
            LimeLog.warning("Error loading keyboard configuration: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(context, context.getString(R.string.keyboard_load_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void showComboCreationDialog(TouchKitComboButton button) {
        LinearLayout container = createEditorContainer();
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.touchkit_combo_input_hint);
        container.addView(input);

        CheckBox physicalKeyNames = new CheckBox(context);
        physicalKeyNames.setText(R.string.touchkit_show_physical_key_names);
        container.addView(physicalKeyNames);

        TextView help = new TextView(context);
        help.setText(R.string.touchkit_combo_input_help);
        container.addView(help);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.touchkit_combo_input_title)
                .setView(createScrollableEditorView(container))
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel,
                        (ignored, which) -> deleteElementFromLayout(button))
                .create();
        dialog.setOnCancelListener(ignored -> deleteElementFromLayout(button));
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String line = input.getText().toString().trim();
                    int separator = line.indexOf('=');
                    String combo = (separator < 0 ? line : line.substring(0, separator)).trim();
                    String description = separator < 0
                            ? "" : line.substring(separator + 1).trim();
                    if (combo.isEmpty() || !button.setComboSpec(combo)) {
                        Toast.makeText(context, R.string.touchkit_editor_invalid_key,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    button.setShowPhysicalKeyNames(physicalKeyNames.isChecked());
                    button.setDescription(description);
                    KeyBoardControllerConfigurationLoader.saveProfile(this, context);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private Point findNonOverlappingPosition(List<Rect> existingPositions, int elementSize) {
        // 1. Try to find space next to existing elements
        Point position = findPositionNextToExisting(existingPositions, elementSize);
        if (position != null) {
            return position;
        }

        // 2. If not found, search from top-left
        return findNonOverlappingPositionFromTopLeft(existingPositions, elementSize);
    }

    private String findAvailableComboElementId(
            Map<String, keyBoardVirtualControllerElement> existingElements) {
        for (int i = 1; i <= 8; i++) {
            String id = "touchkit_combo_" + i;
            keyBoardVirtualControllerElement element = existingElements.get(id);
            if (element == null || !element.enabled || element.hidden) {
                return id;
            }
        }
        return null;
    }

    private Point findPositionNextToExisting(List<Rect> existingPositions, int elementSize) {
        int spacing = 10;

        for (Rect existingRect : existingPositions) {
            // Potential positions around the existing rectangle
            Point[] potentialPositions = {
                    new Point(existingRect.right + spacing, existingRect.top), // Right
                    new Point(existingRect.left - elementSize - spacing, existingRect.top), // Left
                    new Point(existingRect.left, existingRect.bottom + spacing), // Bottom
                    new Point(existingRect.left, existingRect.top - elementSize - spacing) // Top
            };

            for (Point p : potentialPositions) {
                if (isPositionFree(p, elementSize, existingPositions)) {
                    return p;
                }
            }
        }

        return null; // No free spot found
    }

    private boolean isPositionFree(Point pos, int elementSize, List<Rect> existingPositions) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int screenWidth = screen.widthPixels;
        int screenHeight = screen.heightPixels;
        int spacing = 10;

        Rect newRect = new Rect(pos.x, pos.y, pos.x + elementSize, pos.y + elementSize);

        // Check screen bounds, leaving a margin
        if (newRect.left < spacing || newRect.right > screenWidth - spacing || newRect.top < 100 || newRect.bottom > screenHeight - 50) {
            return false;
        }

        // Check for overlap with other elements
        for (Rect existing : existingPositions) {
            if (Rect.intersects(existing, newRect)) {
                return false;
            }
        }

        // Check against configure button area (top left corner)
        Rect configButtonArea = new Rect(0, 0, 150, 100);
        return !Rect.intersects(configButtonArea, newRect);
    }


    private Point findNonOverlappingPositionFromTopLeft(List<Rect> existingPositions, int elementSize) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int spacing = 10; // Minimum spacing between elements

        // Start from top of screen with some margin (avoid configure button area)
        int startY = 100;
        int x = spacing;
        int y = startY;

        while (isPositionFree(new Point(x, y), elementSize, existingPositions)) {
            // Move right
            x += elementSize + spacing;

            // If reached screen width, move to next row
            if (!isPositionFree(new Point(x, y), elementSize, existingPositions)) {
                x = spacing;
                y += elementSize + spacing;
            }

            // If a free spot is found, return it
            if (isPositionFree(new Point(x, y), elementSize, existingPositions)) {
                return new Point(x, y);
            }
        }


        // If no space found, place at a default location (might overlap)
        return new Point(spacing, startY);
    }

    private int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }
}
