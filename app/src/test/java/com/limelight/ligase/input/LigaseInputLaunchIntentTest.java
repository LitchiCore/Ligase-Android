package com.limelight.ligase.input;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Intent;

import com.limelight.Game;
import com.limelight.computers.ComputerManagerService;
import com.limelight.ligase.InputDeviceMode;
import com.limelight.ligase.LigasePreferences;
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
    public void touchLaunchCarriesModeAndStableGlobalLayoutId() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LigasePreferences.setInputDeviceMode(activity, InputDeviceMode.TOUCH);
        LigasePreferences.setGlobalTouchLayoutId(activity, "OSC_Keyboard_2");
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
        assertEquals("OSC_Keyboard_2",
                intent.getStringExtra(Game.EXTRA_LIGASE_TOUCH_LAYOUT_ID));
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
