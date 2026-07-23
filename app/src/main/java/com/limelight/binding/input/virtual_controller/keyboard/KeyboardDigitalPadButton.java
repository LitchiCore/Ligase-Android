package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

public class KeyboardDigitalPadButton extends keyBoardVirtualControllerElement{

    private String value;

    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
    List<DigitalPadListener> listeners = new ArrayList<>();

    private static final int DPAD_MARGIN = 5;

    private final Paint paint = new Paint();

    protected KeyboardDigitalPadButton(KeyBoardController controller, Context context, String elementId) {
        super(controller, context, elementId);
    }

    public void addDigitalPadListener(DigitalPadListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getCorrectWidth(), 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
            // draw no direction rect
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(getDefaultColor());
            canvas.drawRect(
                    getPercent(getWidth(), 36), getPercent(getHeight(), 36),
                    getPercent(getWidth(), 63), getPercent(getHeight(), 63),
                    paint
            );
        }

        // draw left rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                paint
        );


        // draw up rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_UP) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN,
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                paint
        );

        // draw right rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 66),
                paint
        );

        // draw down rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 ? pressedColor : getDefaultColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight() - (paint.getStrokeWidth()+DPAD_MARGIN),
                paint
        );

        // draw left up line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_UP) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN,
                paint
        );

        // draw up right line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_UP) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 66), paint.getStrokeWidth()+DPAD_MARGIN,
                getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 33),
                paint
        );

        // draw right down line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getWidth()-paint.getStrokeWidth(), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN),
                paint
        );

        // draw down left line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0
                ) ? pressedColor : getDefaultColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 33), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN),
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 66),
                paint
        );
    }

    @Override
    protected boolean shouldDrawGrayBackground() {
        return true;
    }

    private void updateDirection(int newDirection) {
        int changedDirections = changedDirections(direction, newDirection);
        if (changedDirections == 0) {
            return;
        }
        direction = newDirection;
        _DBG("direction: " + direction + " changed: " + changedDirections);

        // notify listeners
        for (DigitalPadListener listener : listeners) {
            listener.onDirectionChange(direction, changedDirections);
        }
    }

    static int changedDirections(int previousDirection, int newDirection) {
        return previousDirection ^ newDirection;
    }

    static int directionForPosition(float x, float y, int width, int height) {
        int newDirection = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
        if (x < width * 0.33f) {
            newDirection |= DIGITAL_PAD_DIRECTION_LEFT;
        }
        if (x > width * 0.66f) {
            newDirection |= DIGITAL_PAD_DIRECTION_RIGHT;
        }
        if (y > height * 0.66f) {
            newDirection |= DIGITAL_PAD_DIRECTION_DOWN;
        }
        if (y < height * 0.33f) {
            newDirection |= DIGITAL_PAD_DIRECTION_UP;
        }
        return newDirection;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                updateDirection(directionForPosition(event.getX(), event.getY(),
                        getWidth(), getHeight()));
                invalidate();

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                updateDirection(DIGITAL_PAD_DIRECTION_NO_DIRECTION);
                invalidate();

                return true;
            }
            default: {
            }
        }

        return true;
    }

    public interface DigitalPadListener {
        void onDirectionChange(int direction, int changedDirections);
    }
}
