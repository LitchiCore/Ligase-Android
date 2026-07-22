package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

/** Opens Artemis' existing system soft-keyboard bridge. */
public final class TouchKitSoftKeyboardButton extends keyBoardVirtualControllerElement {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF keyboardBounds = new RectF();

    public TouchKitSoftKeyboardButton(KeyBoardController controller, Context context,
                                      String elementId) {
        super(controller, context, elementId);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        float stroke = Math.max(2f, getDefaultStrokeWidth());
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - stroke;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(virtualController.getControllerMode() ==
                KeyBoardController.ControllerMode.Active
                ? getGrayOutlineColor() : getDefaultColor());
        canvas.drawCircle(centerX, centerY, radius, paint);

        float insetX = getWidth() * 0.23f;
        float insetY = getHeight() * 0.29f;
        keyboardBounds.set(insetX, insetY, getWidth() - insetX, getHeight() - insetY);
        paint.setColor(isPressed()
                ? applyForegroundOpacity(pressedColor)
                : applyForegroundOpacity(Color.WHITE));
        paint.setStrokeWidth(Math.max(2f, stroke * 0.8f));
        canvas.drawRoundRect(keyboardBounds, stroke * 1.4f, stroke * 1.4f, paint);

        paint.setStyle(Paint.Style.FILL);
        float keyRadius = Math.max(1.5f, stroke * 0.42f);
        float startX = keyboardBounds.left + keyboardBounds.width() * 0.18f;
        float stepX = keyboardBounds.width() * 0.21f;
        float rowOneY = keyboardBounds.top + keyboardBounds.height() * 0.32f;
        float rowTwoY = keyboardBounds.top + keyboardBounds.height() * 0.58f;
        for (int row = 0; row < 2; row++) {
            float y = row == 0 ? rowOneY : rowTwoY;
            for (int column = 0; column < 4; column++) {
                canvas.drawCircle(startX + column * stepX, y, keyRadius, paint);
            }
        }
        RectF space = new RectF(
                keyboardBounds.left + keyboardBounds.width() * 0.28f,
                keyboardBounds.top + keyboardBounds.height() * 0.77f,
                keyboardBounds.right - keyboardBounds.width() * 0.28f,
                keyboardBounds.top + keyboardBounds.height() * 0.84f);
        canvas.drawRoundRect(space, keyRadius, keyRadius, paint);
    }

    @Override
    protected boolean shouldDrawGrayBackground() {
        return true;
    }

    @Override
    protected boolean isGrayBackgroundCircular() {
        return true;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                boolean activate = isPressed();
                setPressed(false);
                invalidate();
                if (activate) {
                    virtualController.openSoftKeyboard();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
