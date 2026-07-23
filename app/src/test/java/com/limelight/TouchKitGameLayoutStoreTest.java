package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TouchKitGameLayoutStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("touchkit_game_layouts", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("touchkit_layout_registry", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("touchkit_layout_names", Context.MODE_PRIVATE)
                .edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    @Test
    public void rememberedLayout_isScopedByHostAndGame() {
        String rememberedLayout = TouchKitLayoutNames.add(context, "Game layout");
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                        rememberedLayout).commit();
        TouchKitGameLayoutStore.rememberCurrent(context, "pc-a", "game-a", 10);

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                        "OSC_Keyboard").commit();
        TouchKitGameLayoutStore.applyRemembered(context, "pc-a", "game-a", 10);

        assertEquals(rememberedLayout, PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, null));
    }

    @Test
    public void differentGame_usesConfiguredDefaultLayout() {
        String configuredDefault = TouchKitLayoutNames.add(context, "Default layout");
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(TouchKitLayoutNames.DEFAULT_LAYOUT_PREF, configuredDefault)
                .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE,
                        "OSC_Keyboard").commit();
        TouchKitGameLayoutStore.applyRemembered(context, "pc-a", "game-b", 11);

        assertEquals(configuredDefault, PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, null));
    }

    @Test
    public void layouts_canBeAddedAndDeletedWithoutLeavingGameReference() {
        int initialCount = TouchKitLayoutNames.getValues(context).length;
        String added = TouchKitLayoutNames.add(context, "战争雷霆");
        assertEquals(initialCount + 1, TouchKitLayoutNames.getValues(context).length);
        assertTrue(TouchKitLayoutNames.contains(context, added));

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(TouchKitLayoutNames.DEFAULT_LAYOUT_PREF, added)
                .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, added)
                .commit();
        TouchKitGameLayoutStore.rememberCurrent(context, "pc-a", "war-thunder", 20);

        assertTrue(TouchKitLayoutNames.delete(context, added));
        assertFalse(TouchKitLayoutNames.contains(context, added));
        assertEquals(initialCount, TouchKitLayoutNames.getValues(context).length);
        TouchKitGameLayoutStore.applyRemembered(context, "pc-a", "war-thunder", 20);
        assertFalse(added.equals(PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, null)));
    }
}
