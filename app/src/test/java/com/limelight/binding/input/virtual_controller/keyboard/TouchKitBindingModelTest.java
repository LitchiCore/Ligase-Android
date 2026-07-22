package com.limelight.binding.input.virtual_controller.keyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.preference.PreferenceManager;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TouchKitBindingModelTest {
    @Test
    public void circularControl_doesNotClaimTransparentCorners() {
        HitRegionElement circle = new HitRegionElement(
                RuntimeEnvironment.getApplication(), true);
        circle.layout(0, 0, 200, 200);
        assertTrue(circle.isPointInsideInteractiveRegion(100, 100));
        assertFalse(circle.isPointInsideInteractiveRegion(5, 5));

        HitRegionElement rectangle = new HitRegionElement(
                RuntimeEnvironment.getApplication(), false);
        rectangle.layout(0, 0, 200, 200);
        assertTrue(rectangle.isPointInsideInteractiveRegion(5, 5));
    }

    @Test
    public void newlyAddedControl_inheritsGlobalAppearance() {
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
                .edit()
                .putInt("seekbar_touchkit_overlay_opacity", 37)
                .putInt("touchkit_foreground_opacity", 64)
                .commit();
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(
                null, "new_global_appearance", 1,
                RuntimeEnvironment.getApplication());

        KeyBoardController.applyGlobalAppearance(
                RuntimeEnvironment.getApplication(), button);

        assertEquals(37, button.getGlobalOpacityForTest());
        assertEquals(64, button.getGlobalForegroundOpacityForTest());
    }

    @Test
    public void softKeyboardDescriptor_createsDedicatedFunctionControl() throws Exception {
        JSONObject descriptor = new JSONObject()
                .put("type", 8)
                .put("elementId", "touchkit_soft_keyboard_test");
        assertTrue(KeyBoardControllerConfigurationLoader.createElementFromDescriptor(
                descriptor, null, RuntimeEnvironment.getApplication(), null)
                instanceof TouchKitSoftKeyboardButton);
    }

    @Test
    public void layoutMigration_anchorsControlsToLeftCenterAndRight() throws Exception {
        JSONObject left = new JSONObject()
                .put("LEFT", 100).put("TOP", 100)
                .put("WIDTH", 100).put("HEIGHT", 100);
        JSONObject center = new JSONObject()
                .put("LEFT", 1150).put("TOP", 100)
                .put("WIDTH", 100).put("HEIGHT", 100);
        JSONObject right = new JSONObject()
                .put("LEFT", 2200).put("TOP", 100)
                .put("WIDTH", 100).put("HEIGHT", 100);

        KeyBoardControllerConfigurationLoader.scaleConfiguration(
                left, 2400, 1080, 1920, 1200);
        KeyBoardControllerConfigurationLoader.scaleConfiguration(
                center, 2400, 1080, 1920, 1200);
        KeyBoardControllerConfigurationLoader.scaleConfiguration(
                right, 2400, 1080, 1920, 1200);

        assertEquals(111, left.getInt("LEFT"));
        assertEquals(905, center.getInt("LEFT"));
        assertEquals(1698, right.getInt("LEFT"));
        assertEquals(111, right.getInt("WIDTH"));
        assertEquals(111, right.getInt("TOP"));
    }

    @Test
    public void comboParser_acceptsModifiersAndLetters() {
        assertArrayEquals(new int[] {
                        KeyEvent.KEYCODE_CTRL_LEFT,
                        KeyEvent.KEYCODE_C
                },
                TouchKitKeyBindingParser.parseCombo("CTRL+C"));
        assertEquals("LeftAlt+1", TouchKitKeyBindingParser.normalizeCombo("ALT+1"));
        assertEquals("Alt+1", TouchKitKeyBindingParser.displayCombo("LeftAlt+1", false));
        assertEquals("L Alt+1", TouchKitKeyBindingParser.displayCombo("LeftAlt+1", true));
        assertEquals("Alt+1",
                TouchKitKeyBindingParser.displayCombo("LeftAlt+Num1", false));
        assertEquals("L Alt+Num1",
                TouchKitKeyBindingParser.displayCombo("LeftAlt+Num1", true));
        assertEquals("RightCtrl+1",
                TouchKitKeyBindingParser.normalizeCombo("RightCtrl+1"));
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME,
                TouchKitKeyBindingParser.parseKeyCode("Home"));
        assertEquals(KeyEvent.KEYCODE_MOVE_END,
                TouchKitKeyBindingParser.parseKeyCode("End"));
        assertEquals(KeyEvent.KEYCODE_PLUS,
                TouchKitKeyBindingParser.parseKeyCode("Plus"));
        assertEquals(KeyEvent.KEYCODE_MINUS,
                TouchKitKeyBindingParser.parseKeyCode("-"));
        assertEquals(KeyEvent.KEYCODE_EQUALS,
                TouchKitKeyBindingParser.parseKeyCode("="));
        assertEquals(KeyEvent.KEYCODE_GRAVE,
                TouchKitKeyBindingParser.parseKeyCode("`"));
        assertEquals(KeyEvent.KEYCODE_BACKSLASH,
                TouchKitKeyBindingParser.parseKeyCode("\\"));
        assertEquals(KeyEvent.KEYCODE_CAPS_LOCK,
                TouchKitKeyBindingParser.parseKeyCode("CapsLock"));
        assertEquals(KeyEvent.KEYCODE_SYSRQ,
                TouchKitKeyBindingParser.parseKeyCode("PrintScreen"));
        assertEquals(KeyEvent.KEYCODE_NUMPAD_ADD,
                TouchKitKeyBindingParser.parseKeyCode("NumPlus"));
        assertEquals("Plus", TouchKitKeyBindingParser.normalizeCombo("Plus"));
        assertEquals("+", TouchKitKeyBindingParser.displayCombo("Plus", false));
        assertEquals("NumPlus", TouchKitKeyBindingParser.normalizeCombo("NumPlus"));
        assertEquals("Num+", TouchKitKeyBindingParser.displayCombo("NumPlus", true));
    }

    @Test
    public void comboParser_distinguishesNumberRowFromNumpad() {
        assertEquals(KeyEvent.KEYCODE_1, TouchKitKeyBindingParser.parseKeyCode("1"));
        assertEquals(KeyEvent.KEYCODE_NUMPAD_1,
                TouchKitKeyBindingParser.parseKeyCode("Num1"));
        assertArrayEquals(new int[] {
                        KeyEvent.KEYCODE_ALT_LEFT,
                        KeyEvent.KEYCODE_NUMPAD_1
                }, TouchKitKeyBindingParser.parseCombo("ALT+NUM1"));
    }

    @Test
    public void bindingLine_distinguishesEqualsKeyFromDescriptionSeparator() {
        TouchKitKeyBindingParser.BindingLine equals =
                TouchKitKeyBindingParser.splitBindingLine("==补充成员");
        assertEquals("=", equals.keySpec);
        assertEquals("补充成员", equals.description);

        TouchKitKeyBindingParser.BindingLine shiftedEquals =
                TouchKitKeyBindingParser.splitBindingLine("Shift+==放大");
        assertEquals("Shift+=", shiftedEquals.keySpec);
        assertEquals("放大", shiftedEquals.description);

        TouchKitKeyBindingParser.BindingLine equalsOnly =
                TouchKitKeyBindingParser.splitBindingLine("=");
        assertEquals("=", equalsOnly.keySpec);
        assertEquals("", equalsOnly.description);
    }

    @Test(expected = IllegalArgumentException.class)
    public void comboParser_rejectsUnknownKeys() {
        TouchKitKeyBindingParser.parseCombo("CTRL+NOT_A_REAL_KEY");
    }

    @Test
    public void comboDisplay_hidesOnlyPhysicalSideNames() {
        TouchKitComboButton button = new TouchKitComboButton(
                null, RuntimeEnvironment.getApplication(), "combo_display");
        assertTrue(button.setComboSpec("RightAlt+Num1"));
        assertEquals("Alt+1", button.getText());
        button.setShowPhysicalKeyNames(true);
        assertEquals("R Alt+Num1", button.getText());
        assertEquals("RightAlt+Num1", button.getComboSpec());
    }

    @Test
    public void keyDisplay_usesFriendlyNamesInBothPhysicalModes() {
        assertEquals("Esc", TouchKitKeyBindingParser.displayKeyCodeName(
                KeyEvent.KEYCODE_ESCAPE, false));
        assertEquals("Esc", TouchKitKeyBindingParser.displayKeyCodeName(
                KeyEvent.KEYCODE_ESCAPE, true));
        assertEquals("Delete", TouchKitKeyBindingParser.displayKeyCodeName(
                KeyEvent.KEYCODE_FORWARD_DEL, false));
        assertEquals("Home", TouchKitKeyBindingParser.displayKeyCodeName(
                KeyEvent.KEYCODE_MOVE_HOME, false));
        assertEquals("End", TouchKitKeyBindingParser.displayKeyCodeName(
                KeyEvent.KEYCODE_MOVE_END, false));
    }

    @Test
    public void timedHoldDuration_isAdjustableAndClamped() {
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(
                null, "timed_hold", 1, RuntimeEnvironment.getApplication());
        button.setTimedHoldDurationMs(1000);
        assertEquals(1000, button.getTimedHoldDurationMs());
        button.setTimedHoldDurationMs(50);
        assertEquals(100, button.getTimedHoldDurationMs());
        button.setTimedHoldDurationMs(6000);
        assertEquals(5000, button.getTimedHoldDurationMs());
    }

    @Test
    public void timedHold_secondTapCancelsWithoutRestarting() {
        KeyBoardController controller = mock(KeyBoardController.class);
        when(controller.getHandler()).thenReturn(new Handler(Looper.getMainLooper()));
        KeyBoardDigitalButton button = new KeyBoardDigitalButton(
                controller, "timed_hold_cancel", 1,
                RuntimeEnvironment.getApplication());
        button.setTriggerMode(KeyBoardDigitalButton.TriggerMode.TIMED_HOLD);
        button.setTimedHoldDurationMs(500);
        int[] callbacks = new int[2];
        button.addDigitalButtonListener(new KeyBoardDigitalButton.DigitalButtonListener() {
            @Override public void onClick() { callbacks[0]++; }
            @Override public void onLongClick() { }
            @Override public void onRelease() { callbacks[1]++; }
        });

        MotionEvent firstDown = MotionEvent.obtain(0, 0,
                MotionEvent.ACTION_DOWN, 10, 10, 0);
        MotionEvent firstUp = MotionEvent.obtain(0, 10,
                MotionEvent.ACTION_UP, 10, 10, 0);
        MotionEvent cancelDown = MotionEvent.obtain(0, 20,
                MotionEvent.ACTION_DOWN, 10, 10, 0);
        button.onElementTouchEvent(firstDown);
        button.onElementTouchEvent(firstUp);
        button.onElementTouchEvent(cancelDown);

        assertArrayEquals(new int[] {1, 1}, callbacks);
        assertFalse(button.isPressed());
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS);
        assertArrayEquals(new int[] {1, 1}, callbacks);

        firstDown.recycle();
        firstUp.recycle();
        cancelDown.recycle();
    }

    @Test
    public void stickMapping_updatesOnlySpecifiedDirections() {
        int[] defaults = {
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_D,
                KeyEvent.KEYCODE_SHIFT_LEFT
        };
        int[] parsed = TouchKitStickBindingSupport.parse("上=UP\n右=RIGHT", defaults);
        assertArrayEquals(new int[] {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_DPAD_RIGHT
        }, parsed);
        assertNull(TouchKitStickBindingSupport.parse("中心=SPACE", defaults));
        assertFalse(TouchKitStickBindingSupport.format(defaults).contains("中心="));
        assertEquals("上=W\n下=S\n左=A\n右=D",
                TouchKitStickBindingSupport.removeLegacyCenterBinding(
                        "上=W\n下=S\n左=A\n右=D\n中心=LeftShift"));
    }

    @Test
    public void radialMenu_hasNameAndValidatedSegments() {
        TouchKitRadialMenuButton radial = new TouchKitRadialMenuButton(
                null, RuntimeEnvironment.getApplication(), "radial_test");
        assertEquals("", radial.getRadialName());
        assertEquals("", radial.getActionSpec());
        assertEquals(0, radial.getActionCount());
        radial.setRadialName("战斗");
        assertTrue(radial.setActionSpec("CTRL+C=复制\nCTRL+V=粘贴\nM=地图\nF1"));
        assertEquals("战斗", radial.getRadialName());
        assertEquals(4, radial.getActionCount());

        assertFalse(radial.setActionSpec("错误行"));
        assertEquals(4, radial.getActionCount());
        assertFalse(radial.setActionSpec("复制=CTRL+C"));
        assertEquals(4, radial.getActionCount());
        assertTrue(radial.setActionSpec(""));
        assertEquals(0, radial.getActionCount());

        assertTrue(radial.setActionSpec("==补充成员\nShift+==放大"));
        assertEquals("==补充成员\nLeftShift+==放大", radial.getActionSpec());
    }

    @Test
    public void dynamicWheel_canBeRegisteredAndReallyDeleted() throws Exception {
        String layoutId = "dynamic_wheel_test";
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
                .edit()
                .putString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, layoutId)
                .commit();
        RuntimeEnvironment.getApplication().getSharedPreferences(layoutId, 0)
                .edit().clear().commit();

        String dynamicId = KeyBoardControllerConfigurationLoader
                .createDynamicElementId("touchkit_radial");
        JSONObject descriptor = new JSONObject()
                .put("type", 5)
                .put("name", "Key wheel");
        KeyBoardControllerConfigurationLoader.recordDynamicElement(
                RuntimeEnvironment.getApplication(), descriptor, dynamicId);
        assertTrue(KeyBoardControllerConfigurationLoader.containsDynamicElement(
                RuntimeEnvironment.getApplication(), dynamicId));

        KeyBoardControllerConfigurationLoader.deleteElement(
                RuntimeEnvironment.getApplication(), dynamicId);
        assertFalse(KeyBoardControllerConfigurationLoader.containsDynamicElement(
                RuntimeEnvironment.getApplication(), dynamicId));
    }

    private static final class HitRegionElement extends keyBoardVirtualControllerElement {
        private final boolean circular;

        HitRegionElement(Context context, boolean circular) {
            super(null, context, "hit_test");
            this.circular = circular;
        }

        @Override protected void onElementDraw(Canvas canvas) { }
        @Override protected boolean isGrayBackgroundCircular() { return circular; }
        @Override public boolean onElementTouchEvent(MotionEvent event) { return true; }
    }
}
