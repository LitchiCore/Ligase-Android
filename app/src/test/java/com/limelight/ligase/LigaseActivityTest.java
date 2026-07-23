package com.limelight.ligase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
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
}
