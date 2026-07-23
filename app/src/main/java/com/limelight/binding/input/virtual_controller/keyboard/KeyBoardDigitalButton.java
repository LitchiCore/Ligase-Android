/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class KeyBoardDigitalButton extends keyBoardVirtualControllerElement {

    public enum TriggerMode {
        HOLD,
        TAP,
        TOGGLE,
        LONG_PRESS,
        TIMED_HOLD
    }

    public enum ButtonShape {
        CIRCLE,
        ROUNDED_RECT
    }

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private String description = "";
    private int editableKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private boolean showPhysicalKeyNames;
    private int icon = -1;
    private long timerLongClickTimeout = 300;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private int layer;
    private KeyBoardDigitalButton movingButton = null;
    private boolean sticky = false;
    private TriggerMode triggerMode = TriggerMode.HOLD;
    private ButtonShape buttonShape = ButtonShape.CIRCLE;
    private boolean toggleLatched;
    private boolean longPressTriggered;
    private boolean timedHoldActive;
    private int timedHoldDurationMs = 500;
    private boolean touchkitPointerDragEnabled;
    private int heldPointerId = -1;
    private float lastPointerRawX;
    private float lastPointerRawY;
    private float pointerScaleX = 1f;
    private float pointerScaleY = 1f;
    private final Runnable triggerLongPressRunnable = () -> {
        if (triggerMode == TriggerMode.LONG_PRESS && isPressed()) {
            longPressTriggered = true;
            notifyPress();
            virtualController.vibrate(KeyEvent.ACTION_DOWN);
        }
    };
    private final Runnable timedHoldReleaseRunnable = () -> {
        if (!timedHoldActive) return;
        timedHoldActive = false;
        onReleaseCallback();
        setPressed(false);
        virtualController.vibrate(KeyEvent.ACTION_UP);
        invalidate();
    };

    boolean inRange(float x, float y) {
        return isPointInsideInteractiveRegion(x - getX(), y - getY());
    }

    public boolean checkMovement(float x, float y, KeyBoardDigitalButton movingButton) {
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof KeyBoardDigitalButton) {
                ((KeyBoardDigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public KeyBoardDigitalButton(KeyBoardController controller, String elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public String getText() {
        return text;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
        invalidate();
    }

    public String getDescription() {
        return description;
    }

    public void setEditableKeyCode(int keyCode) {
        editableKeyCode = keyCode;
    }

    public int getEditableKeyCode() {
        return editableKeyCode;
    }

    public int getEditableKeyCode(int fallback) {
        return hasEditableKeyBinding() ? editableKeyCode : fallback;
    }

    public boolean hasEditableKeyBinding() {
        return editableKeyCode != KeyEvent.KEYCODE_UNKNOWN;
    }

    public boolean setEditableKeyName(String keyName) {
        if (!hasEditableKeyBinding() || keyName == null) {
            return false;
        }
        try {
            editableKeyCode = TouchKitKeyBindingParser.parseKeyCode(keyName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void setShowPhysicalKeyNames(boolean showPhysicalKeyNames) {
        this.showPhysicalKeyNames = showPhysicalKeyNames;
        if (hasEditableKeyBinding()) {
            setText(TouchKitKeyBindingParser.displayKeyCodeName(
                    editableKeyCode, showPhysicalKeyNames));
        }
    }

    public boolean getShowPhysicalKeyNames() {
        return showPhysicalKeyNames;
    }

    public void setTriggerMode(TriggerMode triggerMode) {
        virtualController.getHandler().removeCallbacks(timedHoldReleaseRunnable);
        if (timedHoldActive) {
            timedHoldActive = false;
            onReleaseCallback();
        }
        this.triggerMode = triggerMode == null ? TriggerMode.HOLD : triggerMode;
        sticky = false;
        toggleLatched = false;
        longPressTriggered = false;
        setPressed(false);
        invalidate();
    }

    public TriggerMode getTriggerMode() {
        return triggerMode;
    }

    public int getTimedHoldDurationMs() {
        return timedHoldDurationMs;
    }

    public void setTimedHoldDurationMs(int durationMs) {
        timedHoldDurationMs = Math.max(100, Math.min(5000, durationMs));
    }

    public void setButtonShape(ButtonShape buttonShape) {
        this.buttonShape = buttonShape == null ? ButtonShape.CIRCLE : buttonShape;
        invalidate();
    }

    public ButtonShape getButtonShape() {
        return buttonShape;
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public boolean isSticky() {
        return this.sticky;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        boolean showDescription = shouldShowDescriptionLayer();
        int controlSize = Math.min(getWidth(), getHeight());
        float controlLeft = (getWidth() - controlSize) / 2f;
        float controlTop = (getHeight() - controlSize) / 2f;

        paint.setTextSize(getPercent(controlSize, 25));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        boolean shouldSetPressed = isPressed() || isSticky();

        int outlineColor = virtualController.getControllerMode() ==
                KeyBoardController.ControllerMode.Active ? getGrayOutlineColor() : getDefaultColor();
        paint.setColor(shouldSetPressed ? applyGlobalOpacity(pressedColor) : outlineColor);

        paint.setStyle(shouldSetPressed ? Paint.Style.FILL_AND_STROKE: Paint.Style.STROKE);

        rect.left = controlLeft + paint.getStrokeWidth();
        rect.top = controlTop + paint.getStrokeWidth();
        rect.right = controlLeft + controlSize - paint.getStrokeWidth();
        rect.bottom = controlTop + controlSize - paint.getStrokeWidth();

        if (buttonShape == ButtonShape.ROUNDED_RECT) {
            float radius = getPercent(controlSize, 22);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }else{
            canvas.drawOval(rect, paint);
        }

        if (showDescription) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStrokeWidth(Math.max(1f, getDefaultStrokeWidth() / 2f));

            float dividerY = rect.top + rect.height() * 0.64f;
            float dividerInset;
            if (buttonShape == ButtonShape.CIRCLE) {
                float radius = rect.width() / 2f;
                float offsetY = dividerY - rect.centerY();
                dividerInset = radius - (float) Math.sqrt(Math.max(0,
                        radius * radius - offsetY * offsetY));
            } else {
                dividerInset = rect.width() * 0.07f;
            }
            paint.setColor(outlineColor);
            canvas.drawLine(rect.left + dividerInset, dividerY,
                    rect.right - dividerInset, dividerY, paint);

            paint.setColor(applyForegroundOpacity(Color.WHITE));
            if (icon != -1) {
                Drawable d = getResources().getDrawable(icon).mutate();
                d.setTint(applyForegroundOpacity(Color.WHITE));
                float iconSize = Math.min(rect.width() * 0.52f,
                        (dividerY - rect.top) * 0.84f);
                float iconCenterY = rect.top + (dividerY - rect.top) * 0.58f;
                d.setBounds(Math.round(rect.centerX() - iconSize / 2f),
                        Math.round(iconCenterY - iconSize / 2f),
                        Math.round(rect.centerX() + iconSize / 2f),
                        Math.round(iconCenterY + iconSize / 2f));
                d.draw(canvas);
            } else {
                drawFittedText(canvas, text, rect.centerX(),
                        rect.top + (dividerY - rect.top) * 0.60f,
                        rect.width() * 0.78f, Math.max(13, controlSize * 0.28f));
            }
            drawFittedText(canvas, description, rect.centerX(),
                    dividerY + (rect.bottom - dividerY) * 0.45f,
                    rect.width() * 0.68f, Math.max(10, controlSize * 0.13f));
        } else if (icon != -1) {
            Drawable d = getResources().getDrawable(icon).mutate();
            d.setTint(applyForegroundOpacity(Color.WHITE));
            float iconSize = rect.width() * 0.70f;
            d.setBounds(Math.round(rect.centerX() - iconSize / 2f),
                    Math.round(rect.centerY() - iconSize / 2f),
                    Math.round(rect.centerX() + iconSize / 2f),
                    Math.round(rect.centerY() + iconSize / 2f));
            d.draw(canvas);
        } else if (icon == -1) {
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(getDefaultStrokeWidth() / 2f);
            paint.setColor(applyForegroundOpacity(Color.WHITE));
            drawFittedText(canvas, text, rect.centerX(), rect.centerY(),
                    rect.width() * 0.72f, Math.max(13, controlSize * 0.25f));
        }
    }

    @Override
    protected boolean shouldDrawGrayBackground() {
        return true;
    }

    @Override
    protected boolean isGrayBackgroundCircular() {
        return buttonShape == ButtonShape.CIRCLE;
    }

    @Override
    protected void configureGrayBackgroundBounds(RectF bounds, float inset) {
        int controlSize = Math.min(getWidth(), getHeight());
        float left = (getWidth() - controlSize) / 2f;
        float top = (getHeight() - controlSize) / 2f;
        bounds.set(left + inset, top + inset,
                left + controlSize - inset, top + controlSize - inset);
    }

    private boolean shouldShowDescriptionLayer() {
        return PreferenceConfiguration.readPreferences(getContext())
                .touchkitShowButtonDescriptions &&
                !text.isEmpty() && !description.isEmpty();
    }

    private void drawFittedText(Canvas canvas, String value, float centerX, float centerY,
                                float maxWidth, float maxTextSize) {
        if (value == null || value.isEmpty()) {
            return;
        }
        paint.setTextSize(maxTextSize);
        float minTextSize = Math.max(9f, maxTextSize * 0.55f);
        while (paint.getTextSize() > minTextSize && paint.measureText(value) > maxWidth) {
            paint.setTextSize(paint.getTextSize() - 1f);
        }
        float baseline = centerY - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(value, centerX, baseline, paint);
    }

    private void onClickCallback() {
        _DBG("clicked");
        notifyPress();

        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void notifyPress() {
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().removeCallbacks(triggerLongPressRunnable);
    }

    private boolean switchDown;

    private boolean enableSwitchDown;

    public void setEnableSwitchDown(boolean enableSwitchDown) {
        this.enableSwitchDown = enableSwitchDown;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingButton = null;
                longPressTriggered = false;
                heldPointerId = event.getPointerId(event.getActionIndex());
                int heldPointerIndex = event.findPointerIndex(heldPointerId);
                lastPointerRawX = event.getX(heldPointerIndex);
                lastPointerRawY = event.getY(heldPointerIndex);
                touchkitPointerDragEnabled = false;
                if (triggerMode == TriggerMode.HOLD ||
                        triggerMode == TriggerMode.LONG_PRESS) {
                    PreferenceConfiguration currentPrefs =
                            PreferenceConfiguration.readPreferences(getContext());
                    touchkitPointerDragEnabled = currentPrefs.touchkitCloudGamingMode;
                    pointerScaleX = currentPrefs.trackpadSensitivityX / 100f;
                    pointerScaleY = currentPrefs.trackpadSensitivityY / 100f;
                }

                if (triggerMode == TriggerMode.TOGGLE) {
                    toggleLatched = !toggleLatched;
                    setPressed(toggleLatched);
                    if (toggleLatched) {
                        notifyPress();
                        virtualController.vibrate(KeyEvent.ACTION_DOWN);
                    } else {
                        onReleaseCallback();
                        virtualController.vibrate(KeyEvent.ACTION_UP);
                    }
                } else if (triggerMode == TriggerMode.TIMED_HOLD) {
                    virtualController.getHandler().removeCallbacks(timedHoldReleaseRunnable);
                    if (timedHoldActive) {
                        timedHoldActive = false;
                        setPressed(false);
                        onReleaseCallback();
                        virtualController.vibrate(KeyEvent.ACTION_UP);
                    } else {
                        timedHoldActive = true;
                        setPressed(true);
                        notifyPress();
                        virtualController.vibrate(KeyEvent.ACTION_DOWN);
                        virtualController.getHandler().postDelayed(
                                timedHoldReleaseRunnable, timedHoldDurationMs);
                    }
                } else {
                    setPressed(true);
                    if (triggerMode == TriggerMode.HOLD) {
                        onClickCallback();
                    } else if (triggerMode == TriggerMode.LONG_PRESS) {
                        virtualController.getHandler().postDelayed(
                                triggerLongPressRunnable, timerLongClickTimeout);
                    }
                }

                invalidate();
                if(enableSwitchDown && triggerMode == TriggerMode.HOLD){
                    switchDown=!switchDown;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (triggerMode == TriggerMode.HOLD) {
                    if (touchkitPointerDragEnabled && isPressed()) {
                        sendHeldPointerMovement(event);
                    } else {
                        checkMovementForAllButtons(x, y);
                    }
                } else if (triggerMode == TriggerMode.LONG_PRESS &&
                        longPressTriggered && touchkitPointerDragEnabled) {
                    sendHeldPointerMovement(event);
                }

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (triggerMode == TriggerMode.TOGGLE) {
                    return true;
                }
                if (triggerMode == TriggerMode.TIMED_HOLD) {
                    touchkitPointerDragEnabled = false;
                    return true;
                }
                if(enableSwitchDown&&switchDown){
                    return true;
                }

                if (triggerMode == TriggerMode.TAP && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    notifyPress();
                    onReleaseCallback();
                    virtualController.vibrate(KeyEvent.ACTION_DOWN);
                    virtualController.vibrate(KeyEvent.ACTION_UP);
                } else if (triggerMode == TriggerMode.LONG_PRESS) {
                    virtualController.getHandler().removeCallbacks(triggerLongPressRunnable);
                    if (longPressTriggered) {
                        onReleaseCallback();
                    }
                } else {
                    onReleaseCallback();
                }
                setPressed(false);
                touchkitPointerDragEnabled = false;
                heldPointerId = -1;

                if (triggerMode == TriggerMode.HOLD) {
                    checkMovementForAllButtons(x, y);
                }

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }

    private void sendHeldPointerMovement(MotionEvent event) {
        int pointerIndex = event.findPointerIndex(heldPointerId);
        if (pointerIndex < 0) {
            return;
        }
        float rawX = event.getX(pointerIndex);
        float rawY = event.getY(pointerIndex);
        int deltaX = Math.round((rawX - lastPointerRawX) * pointerScaleX);
        int deltaY = Math.round((rawY - lastPointerRawY) * pointerScaleY);
        lastPointerRawX = rawX;
        lastPointerRawY = rawY;
        if (deltaX != 0 || deltaY != 0) {
            virtualController.sendMouseMove(deltaX, deltaY);
        }
    }

    @Override
    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = super.getConfiguration();
        configuration.put("LABEL", text);
        configuration.put("DESCRIPTION", description);
        configuration.put("BUTTON_SHAPE", buttonShape.name());
        configuration.put("TRIGGER_MODE", triggerMode.name());
        configuration.put("TIMED_HOLD_DURATION_MS", timedHoldDurationMs);
        if (hasEditableKeyBinding()) {
            configuration.put("EDITABLE_KEY_CODE", editableKeyCode);
        }
        configuration.put("SHOW_PHYSICAL_KEY_NAMES", showPhysicalKeyNames);
        return configuration;
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        text = configuration.optString("LABEL", text);
        description = configuration.optString("DESCRIPTION", "");
        editableKeyCode = configuration.optInt("EDITABLE_KEY_CODE", editableKeyCode);
        showPhysicalKeyNames = configuration.optBoolean("SHOW_PHYSICAL_KEY_NAMES", false);
        setTimedHoldDurationMs(configuration.optInt("TIMED_HOLD_DURATION_MS", 500));
        try {
            buttonShape = ButtonShape.valueOf(
                    configuration.optString("BUTTON_SHAPE", ButtonShape.CIRCLE.name()));
        } catch (IllegalArgumentException ignored) {
            buttonShape = ButtonShape.CIRCLE;
        }
        try {
            triggerMode = TriggerMode.valueOf(
                    configuration.optString("TRIGGER_MODE", TriggerMode.HOLD.name()));
        } catch (IllegalArgumentException ignored) {
            triggerMode = TriggerMode.HOLD;
        }
        if (triggerMode == TriggerMode.TAP || triggerMode == TriggerMode.LONG_PRESS) {
            triggerMode = TriggerMode.HOLD;
        }
        if (hasEditableKeyBinding()) {
            text = TouchKitKeyBindingParser.displayKeyCodeName(
                    editableKeyCode, showPhysicalKeyNames);
        }
        invalidate();
    }
}
