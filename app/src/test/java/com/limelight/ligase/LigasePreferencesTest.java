package com.limelight.ligase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LigasePreferencesTest {
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
    public void inputDeviceRequiresExplicitFirstChoiceAndPersists() {
        assertFalse(LigasePreferences.hasInputDeviceMode(context));

        LigasePreferences.setInputDeviceMode(context, InputDeviceMode.KEYBOARD_MOUSE);

        assertTrue(LigasePreferences.hasInputDeviceMode(context));
        assertEquals(InputDeviceMode.KEYBOARD_MOUSE,
                LigasePreferences.getInputDeviceMode(context));
    }

    @Test
    public void themeDefaultsToSystemAndPersistsDarkMode() {
        assertEquals(LigaseThemeMode.SYSTEM, LigasePreferences.getThemeMode(context));

        LigasePreferences.setThemeMode(context, LigaseThemeMode.DARK);

        assertEquals(LigaseThemeMode.DARK, LigasePreferences.getThemeMode(context));
    }
}
