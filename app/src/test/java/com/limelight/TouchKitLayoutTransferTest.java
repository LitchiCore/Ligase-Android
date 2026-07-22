package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

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
public class TouchKitLayoutTransferTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("touchkit_layout_registry", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("touchkit_layout_names", Context.MODE_PRIVATE)
                .edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    @Test
    public void roundTrip_preservesTypesAndCreatesSelectedCopy() throws Exception {
        String source = TouchKitLayoutNames.getValues(context)[0];
        SharedPreferences sourcePrefs = context.getSharedPreferences(source, Context.MODE_PRIVATE);
        sourcePrefs.edit()
                .putString("button", "{\"key\":59}")
                .putInt("__touchkit_canvas_width", 2312)
                .putBoolean("enabled", true)
                .putFloat("scale", 0.75f)
                .commit();

        String exported = TouchKitLayoutTransfer.exportLayout(context, source);
        TouchKitLayoutTransfer.ImportResult result = TouchKitLayoutTransfer.importLayout(
                context, exported, "Fallback");

        assertNotEquals(source, result.layoutId);
        SharedPreferences imported = context.getSharedPreferences(result.layoutId, Context.MODE_PRIVATE);
        assertEquals("{\"key\":59}", imported.getString("button", null));
        assertEquals(2312, imported.getInt("__touchkit_canvas_width", 0));
        assertTrue(imported.getBoolean("enabled", false));
        assertEquals(0.75f, imported.getFloat("scale", 0), 0.001f);
        assertEquals(result.layoutId, PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, null));
    }

    @Test
    public void legacyRawJson_importsWithoutOverwritingCurrentLayout() throws Exception {
        String current = TouchKitLayoutNames.getValues(context)[0];
        context.getSharedPreferences(current, Context.MODE_PRIVATE).edit()
                .putString("keep", "original").commit();

        TouchKitLayoutTransfer.ImportResult result = TouchKitLayoutTransfer.importLayout(
                context, "{\"legacy\":\"value\",\"__touchkit_canvas_height\":1080}",
                "旧版预设");

        assertFalse(current.equals(result.layoutId));
        assertEquals("original", context.getSharedPreferences(current, Context.MODE_PRIVATE)
                .getString("keep", null));
        assertEquals(1080, context.getSharedPreferences(result.layoutId, Context.MODE_PRIVATE)
                .getInt("__touchkit_canvas_height", 0));
    }
}
