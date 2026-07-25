package com.limelight.ligase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3EditorActivityViewModel;
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorLaunchMode;
import com.limelight.ligase.feature.input.layout.v3.ui.blackeditor.LayoutV3BlackEditorActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(
        sdk = {33},
        shadows = {
                com.limelight.shadows.ShadowMoonBridge.class,
                com.limelight.shadows.ShadowGameManager.class
        })
public class LigaseActivityTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("ligase_product_preferences", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void firstLaunchRemainsInsideComposeActivity() {
        LigaseActivity activity = Robolectric.buildActivity(LigaseActivity.class).setup().get();

        assertFalse(activity.isFinishing());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertFalse(LigasePreferences.hasInputDeviceMode(activity));
    }

    @Test
    public void laterLaunchDoesNotStartLegacyPcView() {
        LigasePreferences.setInputDeviceMode(context, InputDeviceMode.GAMEPAD);

        LigaseActivity activity = Robolectric.buildActivity(LigaseActivity.class).setup().get();

        assertFalse(activity.isFinishing());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void homeRequiresTwoBackPressesToExit() {
        LigasePreferences.setInputDeviceMode(context, InputDeviceMode.TOUCH);
        LigaseActivity activity = Robolectric.buildActivity(LigaseActivity.class).setup().get();

        activity.getOnBackPressedDispatcher().onBackPressed();
        assertFalse(activity.isFinishing());

        activity.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void newV3EditorLaunchUsesTypedActivityIntent() throws Exception {
        LigasePreferences.setInputDeviceMode(context, InputDeviceMode.TOUCH);
        LigaseActivity activity = Robolectric.buildActivity(LigaseActivity.class).setup().get();
        Method launch = LigaseActivity.class.getDeclaredMethod(
                "launchNewLayoutV3Editor",
                String.class);
        launch.setAccessible(true);

        launch.invoke(activity, "Local layout");
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();

        assertEquals(
                LayoutV3BlackEditorActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(
                LayoutV3EditorLaunchMode.NEW_V3.name(),
                intent.getStringExtra(LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_MODE));
        assertEquals(
                "Local layout",
                intent.getStringExtra(LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DISPLAY_NAME));
    }

    @Test
    public void addHostDialogKeepsInvalidAddressVisible() throws Exception {
        LigasePreferences.setInputDeviceMode(context, InputDeviceMode.TOUCH);
        LigaseActivity activity = Robolectric.buildActivity(LigaseActivity.class).setup().get();
        Method showAddHost = LigaseActivity.class.getDeclaredMethod("showAddHostDialog");
        showAddHost.setAccessible(true);

        showAddHost.invoke(activity);
        Dialog dialog = ShadowDialog.getLatestDialog();
        Button proceed = dialog.findViewById(android.R.id.button1);
        proceed.performClick();

        assertTrue(dialog.isShowing());
    }

    @Test
    public void addHostDialogSeparatesAddressAndLigaseDefaultPort() throws Exception {
        LigasePreferences.setInputDeviceMode(context, InputDeviceMode.TOUCH);
        LigaseActivity activity = Robolectric.buildActivity(LigaseActivity.class).setup().get();
        Method showAddHost = LigaseActivity.class.getDeclaredMethod("showAddHostDialog");
        showAddHost.setAccessible(true);

        showAddHost.invoke(activity);
        Dialog dialog = ShadowDialog.getLatestDialog();
        List<EditText> inputs = new ArrayList<>();
        collectEditTexts(dialog.getWindow().getDecorView(), inputs);

        assertEquals(2, inputs.size());
        assertEquals("", inputs.get(0).getText().toString());
        assertEquals("48989", inputs.get(1).getText().toString());
    }

    private static void collectEditTexts(View view, List<EditText> output) {
        if (view instanceof EditText) {
            output.add((EditText) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectEditTexts(group.getChildAt(index), output);
            }
        }
    }
}
