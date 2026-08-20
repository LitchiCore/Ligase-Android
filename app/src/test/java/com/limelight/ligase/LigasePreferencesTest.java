package com.limelight.ligase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.preference.PreferenceManager;

import com.limelight.ligase.feature.library.domain.HostSortMode;
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode;
import com.limelight.ligase.input.LigaseInputCategory;
import com.limelight.ligase.input.LigaseCloudTouchMode;
import com.limelight.ligase.input.LigaseInputProfile;
import com.limelight.ligase.input.LigaseTouchOverlayMode;

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
    public void stableDeviceSelectionPersistsWithoutLayoutPreference() {
        String stableDevice = "gamepad|1118|654|usb-1234";

        LigasePreferences.setSelectedInputDevice(
                context,
                LigaseInputCategory.GAMEPAD,
                stableDevice);
        assertEquals(stableDevice, LigasePreferences.getSelectedInputDevice(
                context,
                LigaseInputCategory.GAMEPAD));
        assertEquals(null, LigasePreferences.getSelectedInputDevice(
                context,
                LigaseInputCategory.KEYBOARD));
    }

    @Test
    public void canonicalGameUuidOverrideRoundTripsWithoutChangingGlobal() {
        String uuid = "67209ea3-7129-42d0-9349-52f8799d292d";
        LigasePreferences.setInputDeviceMode(context, InputDeviceMode.TOUCH);
        LigasePreferences.setTouchOverlayMode(context, LigaseTouchOverlayMode.CLOUD_CONTROLS);
        LigaseInputProfile override = new LigaseInputProfile(
                InputDeviceMode.GAMEPAD,
                LigaseTouchOverlayMode.HIDDEN,
                LigaseCloudTouchMode.TRACKPAD);

        assertTrue(LigasePreferences.setGameInputOverride(context, uuid, override));
        assertEquals(override, LigasePreferences.gameInputOverride(context, uuid));
        assertEquals(InputDeviceMode.TOUCH,
                LigasePreferences.globalInputProfile(context).getMode());
        assertFalse(LigasePreferences.setGameInputOverride(
                context, uuid.toUpperCase(), override));
        assertEquals(null, LigasePreferences.gameInputOverride(context, uuid.toUpperCase()));
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
