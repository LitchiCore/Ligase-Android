package com.limelight.ligase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.widget.Button;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.ligase.library.LigaseResolutionDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

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
    public void resolutionEditorPositiveButtonInvokesSaveCallback() throws Exception {
        LigasePreferences.setInputDeviceMode(context, InputDeviceMode.TOUCH);
        LigaseActivity activity = Robolectric.buildActivity(LigaseActivity.class).setup().get();
        AtomicReference<LigaseResolutionDto> saved = new AtomicReference<>();
        Function1<LigaseResolutionDto, Unit> callback = value -> {
            saved.set(value);
            return Unit.INSTANCE;
        };
        Method showEditor = LigaseActivity.class.getDeclaredMethod(
                "showResolutionEditor",
                String.class,
                LigaseResolutionDto.class,
                boolean.class,
                boolean.class,
                Function1.class);
        showEditor.setAccessible(true);

        showEditor.invoke(
                activity,
                "Resolution",
                new LigaseResolutionDto(1600, 900),
                false,
                false,
                callback);
        Dialog dialog = ShadowDialog.getLatestDialog();
        Button save = dialog.findViewById(android.R.id.button1);
        save.performClick();

        assertEquals(new LigaseResolutionDto(1600, 900), saved.get());
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
}
