package com.limelight.ligase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.preference.PreferenceManager;

import com.limelight.ligase.library.HostSortMode;
import com.limelight.ligase.library.LibraryLayoutMode;
import com.limelight.ligase.input.LigaseInputCategory;

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
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove("list_languages")
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
    public void stableDeviceAndGlobalLayoutSelectionsPersist() {
        String stableDevice = "gamepad|1118|654|usb-1234";

        LigasePreferences.setSelectedInputDevice(
                context,
                LigaseInputCategory.GAMEPAD,
                stableDevice);
        LigasePreferences.setGlobalTouchLayoutId(context, "OSC_Keyboard_2");

        assertEquals(stableDevice, LigasePreferences.getSelectedInputDevice(
                context,
                LigaseInputCategory.GAMEPAD));
        assertEquals("OSC_Keyboard_2",
                LigasePreferences.getGlobalTouchLayoutId(context));
        assertEquals(null, LigasePreferences.getSelectedInputDevice(
                context,
                LigaseInputCategory.KEYBOARD));
    }

    @Test
    public void themeDefaultsToSystemAndPersistsDarkMode() {
        assertEquals(LigaseThemeMode.SYSTEM, LigasePreferences.getThemeMode(context));

        LigasePreferences.setThemeMode(context, LigaseThemeMode.DARK);

        assertEquals(LigaseThemeMode.DARK, LigasePreferences.getThemeMode(context));
    }

    @Test
    public void libraryDisplaySortIsLocalAndHostScoped() {
        assertEquals(HostSortMode.NAME_ASCENDING,
                LigasePreferences.getLibrarySortMode(context, "host-a"));

        LigasePreferences.setLibrarySortMode(
                context,
                "host-a",
                HostSortMode.LAST_PLAYED_NEWEST);

        assertEquals(HostSortMode.LAST_PLAYED_NEWEST,
                LigasePreferences.getLibrarySortMode(context, "host-a"));
        assertEquals(HostSortMode.NAME_ASCENDING,
                LigasePreferences.getLibrarySortMode(context, "host-b"));
        assertEquals("lastPlayedNewest",
                context.getSharedPreferences(
                                "ligase_product_preferences",
                                Context.MODE_PRIVATE)
                        .getString("library_sort:host-a", null));
    }

    @Test
    public void libraryLayoutDefaultsToListAndPersistsPosterChoice() {
        assertEquals(LibraryLayoutMode.LIST,
                LigasePreferences.getLibraryLayoutMode(context));

        LigasePreferences.setLibraryLayoutMode(context, LibraryLayoutMode.POSTER);

        assertEquals(LibraryLayoutMode.POSTER,
                LigasePreferences.getLibraryLayoutMode(context));
        assertEquals("poster",
                context.getSharedPreferences(
                                "ligase_product_preferences",
                                Context.MODE_PRIVATE)
                        .getString("library_layout", null));
    }

    @Test
    public void languageDefaultsToSystemAndUsesExistingGlobalPreference() {
        assertEquals(LigaseLanguageMode.SYSTEM,
                LigasePreferences.getLanguageMode(context));

        LigasePreferences.setLanguageMode(
                context,
                LigaseLanguageMode.SIMPLIFIED_CHINESE);

        assertEquals(LigaseLanguageMode.SIMPLIFIED_CHINESE,
                LigasePreferences.getLanguageMode(context));
        assertEquals("zh-CN",
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString("list_languages", null));
    }
}
