package com.limelight;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardController;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;
import com.limelight.binding.input.virtual_controller.keyboard.keyBoardVirtualControllerElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Production TouchKit rendering with input and persistence disabled. */
public final class TouchKitLayoutPreviewActivity extends AppCompatActivity {
    private static final String EXTRA_LAYOUT_ID =
            "com.litchicore.ligase.extra.TOUCHKIT_PREVIEW_LAYOUT_ID";

    private KeyBoardController controller;

    public static Intent createIntent(Context context, String layoutId) {
        return new Intent(context, TouchKitLayoutPreviewActivity.class)
                .putExtra(EXTRA_LAYOUT_ID, layoutId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();

        String layoutId = getIntent().getStringExtra(EXTRA_LAYOUT_ID);
        if (layoutId == null || !TouchKitLayoutNames.contains(this, layoutId)) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        Context previewContext = new ReadOnlyLayoutContext(this, layoutId);
        controller = new KeyBoardController(null, root, previewContext, false);
        controller.hide(true);
        controller.showElements();
        for (keyBoardVirtualControllerElement element : controller.getElements()) {
            element.setEnabled(false);
            element.setClickable(false);
            element.setLongClickable(false);
        }

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_ligase_back);
        back.setColorFilter(Color.WHITE);
        back.setBackgroundColor(Color.argb(150, 32, 35, 44));
        back.setContentDescription(getString(R.string.ligase_back));
        back.setOnClickListener(view -> finish());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(56), dp(56));
        params.gravity = Gravity.TOP | Gravity.START;
        params.setMargins(dp(16), dp(16), 0, 0);
        root.addView(back, params);
        back.bringToFront();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        if (controller != null) controller.removeElements();
        super.onDestroy();
    }

    private static final class ReadOnlyLayoutContext extends ContextWrapper {
        private final String layoutId;
        private final String defaultPreferencesName;

        ReadOnlyLayoutContext(Context base, String layoutId) {
            super(base);
            this.layoutId = layoutId;
            defaultPreferencesName = base.getPackageName() + "_preferences";
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            SharedPreferences delegate = super.getSharedPreferences(name, mode);
            return new ReadOnlySharedPreferences(
                    delegate, defaultPreferencesName.equals(name) ? layoutId : null);
        }
    }

    private static final class ReadOnlySharedPreferences implements SharedPreferences {
        private final SharedPreferences delegate;
        @Nullable private final String selectedLayout;

        ReadOnlySharedPreferences(SharedPreferences delegate, @Nullable String selectedLayout) {
            this.delegate = delegate;
            this.selectedLayout = selectedLayout;
        }

        @Override
        public Map<String, ?> getAll() {
            Map<String, Object> values = new HashMap<>(delegate.getAll());
            if (selectedLayout != null) {
                values.put(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, selectedLayout);
            }
            return Collections.unmodifiableMap(values);
        }

        @Override
        public String getString(String key, @Nullable String defaultValue) {
            if (selectedLayout != null
                    && KeyBoardControllerConfigurationLoader.OSC_PREFERENCE.equals(key)) {
                return selectedLayout;
            }
            return delegate.getString(key, defaultValue);
        }

        @Override
        public Set<String> getStringSet(String key, @Nullable Set<String> defaultValues) {
            Set<String> values = delegate.getStringSet(key, defaultValues);
            return values == null ? null : Collections.unmodifiableSet(values);
        }

        @Override public int getInt(String key, int value) { return delegate.getInt(key, value); }
        @Override public long getLong(String key, long value) { return delegate.getLong(key, value); }
        @Override public float getFloat(String key, float value) { return delegate.getFloat(key, value); }
        @Override public boolean getBoolean(String key, boolean value) {
            return delegate.getBoolean(key, value);
        }
        @Override public boolean contains(String key) {
            return selectedLayout != null
                    && KeyBoardControllerConfigurationLoader.OSC_PREFERENCE.equals(key)
                    || delegate.contains(key);
        }
        @Override public Editor edit() { return NoOpEditor.INSTANCE; }
        @Override public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            delegate.registerOnSharedPreferenceChangeListener(listener);
        }
        @Override public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            delegate.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private static final class NoOpEditor implements SharedPreferences.Editor {
        static final NoOpEditor INSTANCE = new NoOpEditor();

        @Override public SharedPreferences.Editor putString(
                String key, @Nullable String value) { return this; }
        @Override public SharedPreferences.Editor putStringSet(
                String key, @Nullable Set<String> values) {
            return this;
        }
        @Override public SharedPreferences.Editor putInt(String key, int value) { return this; }
        @Override public SharedPreferences.Editor putLong(String key, long value) { return this; }
        @Override public SharedPreferences.Editor putFloat(String key, float value) { return this; }
        @Override public SharedPreferences.Editor putBoolean(String key, boolean value) {
            return this;
        }
        @Override public SharedPreferences.Editor remove(String key) { return this; }
        @Override public SharedPreferences.Editor clear() { return this; }
        @Override public boolean commit() { return true; }
        @Override public void apply() { }
    }
}
