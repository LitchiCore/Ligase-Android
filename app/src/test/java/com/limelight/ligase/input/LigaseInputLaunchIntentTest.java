package com.limelight.ligase.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Intent;

import com.limelight.Game;
import com.limelight.computers.ComputerManagerService;
import com.limelight.ligase.InputDeviceMode;
import com.limelight.ligase.LigasePreferences;
import com.limelight.ligase.input.LigaseTouchOverlayMode;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.utils.ServerHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {com.limelight.shadows.ShadowMoonBridge.class})
public class LigaseInputLaunchIntentTest {
    @Test
    public void touchLaunchNeverCarriesLegacyLayoutOrTouchKitRuntime() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LigasePreferences.setInputDeviceMode(activity, InputDeviceMode.TOUCH);
        LigasePreferences.setTouchOverlayMode(activity,
                LigaseTouchOverlayMode.CLOUD_CONTROLS);
        ComputerManagerService.ComputerManagerBinder binder =
                mock(ComputerManagerService.ComputerManagerBinder.class);
        when(binder.getUniqueId()).thenReturn("android-client-id");

        Intent intent = ServerHelper.createStartIntent(
                activity,
                new NvApp("Game", "app-uuid", 42, false),
                computer(),
                binder,
                true,
                1920,
                1080,
                true,
                true);

        assertEquals("touch", intent.getStringExtra(Game.EXTRA_LIGASE_INPUT_MODE));
        assertFalse(intent.getBooleanExtra(Game.EXTRA_LIGASE_VIRTUAL_GAMEPAD, true));
    }

    @Test
    public void physicalModeStillCarriesExplicitMode() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LigasePreferences.setInputDeviceMode(activity, InputDeviceMode.KEYBOARD_MOUSE);
        ComputerManagerService.ComputerManagerBinder binder =
                mock(ComputerManagerService.ComputerManagerBinder.class);
        when(binder.getUniqueId()).thenReturn("android-client-id");

        Intent intent = ServerHelper.createStartIntent(
                activity,
                new NvApp("Game", "app-uuid", 42, false),
                computer(),
                binder,
                true);

        assertEquals("keyboard_mouse",
                intent.getStringExtra(Game.EXTRA_LIGASE_INPUT_MODE));
        assertFalse(intent.getBooleanExtra(Game.EXTRA_LIGASE_VIRTUAL_GAMEPAD, true));
    }

    @Test
    public void typedLaunchDecisionOverridesLegacyGlobalForCanonicalGamePlan() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LigasePreferences.setInputDeviceMode(activity, InputDeviceMode.GAMEPAD);
        ComputerManagerService.ComputerManagerBinder binder =
                mock(ComputerManagerService.ComputerManagerBinder.class);
        when(binder.getUniqueId()).thenReturn("android-client-id");
        LigaseInputLaunchDecision decision = new LigaseInputLaunchDecision(
                InputDeviceMode.TOUCH.getStoredValue(),
                false,
                LigaseCloudTouchMode.TRACKPAD);

        Intent intent = ServerHelper.createLigaseStartIntent(
                activity, new NvApp("Game", "app-uuid", 42, false), computer(), binder,
                true, 1920, 1080, 60f, true, decision);

        assertEquals("touch", intent.getStringExtra(Game.EXTRA_LIGASE_INPUT_MODE));
        assertEquals("trackpad", intent.getStringExtra(Game.EXTRA_LIGASE_CLOUD_TOUCH_MODE));
        assertFalse(intent.getBooleanExtra(Game.EXTRA_LIGASE_VIRTUAL_GAMEPAD, true));
        assertEquals(60f, intent.getFloatExtra(Game.EXTRA_LIGASE_FPS, 0f), 0f);
    }

    @Test
    public void legacyStackedLayersMigrateToSingleKeyboardChoice() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("ligase_product_preferences", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("touch_virtual_gamepad", true)
                .putBoolean("touch_touchkit_keyboard", true)
                .apply();

        assertEquals(
                LigaseTouchOverlayMode.CLOUD_CONTROLS,
                LigasePreferences.getTouchOverlayMode(activity));
        LigasePreferences.setTouchOverlayMode(
                activity,
                LigaseTouchOverlayMode.VIRTUAL_GAMEPAD);
        assertEquals(
                LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
                LigasePreferences.getTouchOverlayMode(activity));
    }

    private static ComputerDetails computer() {
        ComputerDetails computer = new ComputerDetails();
        computer.uuid = "host-uuid";
        computer.name = "Host";
        computer.activeAddress = new ComputerDetails.AddressTuple("192.0.2.1", 48989);
        computer.httpsPort = 48984;
        computer.state = ComputerDetails.State.ONLINE;
        return computer;
    }
}
