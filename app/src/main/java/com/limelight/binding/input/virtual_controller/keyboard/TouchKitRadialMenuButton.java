package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.limelight.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TouchKitRadialMenuButton extends keyBoardVirtualControllerElement {
    private static final long KEY_DOWN_INTERVAL_MS = 25;
    private static final long MIN_KEY_HOLD_MS = 50;
    private static final long KEY_UP_INTERVAL_MS = 10;

    private static class Action {
        final String keyLabel;
        final String description;
        final int[] keys;

        Action(String keyLabel, String description, int[] keys) {
            this.keyLabel = keyLabel;
            this.description = description;
            this.keys = keys;
        }

        String displayLabel() {
            return description.isEmpty() ? keyLabel : description;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF wheelRect = new RectF();
    private final List<Action> actions = new ArrayList<>();
    private String radialName = "";
    private String actionSpec = "";
    private int selectedIndex = -1;
    private boolean expanded;

    public TouchKitRadialMenuButton(KeyBoardController controller, Context context,
                                    String elementId) {
        super(controller, context, elementId);
    }

    public String getRadialName() {
        return radialName;
    }

    public void setRadialName(String radialName) {
        this.radialName = radialName == null ? "" : radialName.trim();
        invalidate();
    }

    public String getActionSpec() {
        return actionSpec;
    }

    int getActionCount() {
        return actions.size();
    }

    public boolean setActionSpec(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            actions.clear();
            actionSpec = "";
            invalidate();
            return true;
        }
        List<Action> parsed = new ArrayList<>();
        StringBuilder normalized = new StringBuilder();
        try {
            for (String rawLine : spec.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (parsed.size() >= 8) {
                    return false;
                }
                TouchKitKeyBindingParser.BindingLine bindingLine =
                        TouchKitKeyBindingParser.splitBindingLine(line);
                String combo = bindingLine.keySpec;
                String description = bindingLine.description;

                int[] keys = TouchKitKeyBindingParser.parseCombo(combo);
                String normalizedCombo = TouchKitKeyBindingParser.normalizeCombo(combo);
                parsed.add(new Action(TouchKitKeyBindingParser.displayCombo(
                        normalizedCombo, false), description, keys));
                if (normalized.length() > 0) {
                    normalized.append('\n');
                }
                normalized.append(normalizedCombo);
                if (!description.isEmpty()) {
                    normalized.append('=').append(description);
                }
            }
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (parsed.isEmpty()) {
            return false;
        }
        actions.clear();
        actions.addAll(parsed);
        actionSpec = normalized.toString();
        invalidate();
        return true;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        float centerRadius = Math.min(getWidth(), getHeight()) * 0.19f;
        if (!expanded) {
            int alpha = getScaledOpacityAlpha(getBackgroundOpacity());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((alpha << 24) | 0x00666666);
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, centerRadius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setColor(virtualController.getControllerMode() ==
                    KeyBoardController.ControllerMode.Active ? getGrayOutlineColor() : getDefaultColor());
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, centerRadius, paint);
            drawCenterName(canvas);
            return;
        }

        float inset = getDefaultStrokeWidth();
        wheelRect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float sweep = 360f / actions.size();

        for (int i = 0; i < actions.size(); i++) {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setColor(applyGlobalOpacity(
                    i == selectedIndex ? pressedColor : 0x99666666));
            canvas.drawArc(wheelRect, -90f + i * sweep, sweep, true, paint);

            double angle = Math.toRadians(-90f + (i + 0.5f) * sweep);
            float radius = Math.min(getWidth(), getHeight()) * 0.34f;
            float x = getWidth() / 2f + (float) Math.cos(angle) * radius;
            float y = getHeight() / 2f + (float) Math.sin(angle) * radius;
            paint.setColor(applyForegroundOpacity(Color.WHITE));
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            paint.setFakeBoldText(false);
            paint.setTextAlign(Paint.Align.CENTER);
            float diameter = Math.min(getWidth(), getHeight());
            float maxTextSize = Math.max(11, diameter * 0.072f);
            float minTextSize = Math.max(8, diameter * 0.035f);
            float maxTextWidth = getSegmentTextWidth(diameter, radius, sweep, angle);
            String label = fitLabel(actions.get(i).displayLabel(), maxTextSize,
                    minTextSize, maxTextWidth);
            canvas.drawText(label, x,
                    y - (paint.ascent() + paint.descent()) / 2f, paint);
        }

        paint.setColor(applyGlobalOpacity(0xDD444444));
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, centerRadius, paint);
        drawCenterName(canvas);
    }

    private void drawCenterName(Canvas canvas) {
        paint.setColor(applyForegroundOpacity(Color.WHITE));
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.CENTER);
        float diameter = Math.min(getWidth(), getHeight());
        String label = fitLabel(radialName, Math.max(13, diameter * 0.11f),
                Math.max(8, diameter * 0.04f), centerRadiusForText() * 1.65f);
        canvas.drawText(label, getWidth() / 2f,
                getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private float centerRadiusForText() {
        return Math.min(getWidth(), getHeight()) * 0.19f;
    }

    private float getSegmentTextWidth(float diameter, float labelRadius,
                                      float sweep, double angle) {
        if (actions.size() == 1) return diameter * 0.72f;

        float sectorWidth = 2f * labelRadius *
                (float) Math.sin(Math.toRadians(sweep / 2f)) * 0.72f;
        float outerRadius = diameter * 0.5f - getDefaultStrokeWidth();
        float xOffset = Math.abs((float) Math.cos(angle) * labelRadius);
        float yOffset = Math.abs((float) Math.sin(angle) * labelRadius);
        float circleHalfWidth = (float) Math.sqrt(Math.max(0,
                outerRadius * outerRadius - yOffset * yOffset)) - xOffset;
        float circleWidth = Math.max(diameter * 0.08f, circleHalfWidth * 2f * 0.85f);
        return Math.max(diameter * 0.08f, Math.min(sectorWidth, circleWidth));
    }

    private String fitLabel(String label, float maxTextSize, float minTextSize,
                            float maxTextWidth) {
        paint.setTextSize(maxTextSize);
        float measured = paint.measureText(label);
        if (measured > maxTextWidth && measured > 0) {
            paint.setTextSize(Math.max(minTextSize,
                    maxTextSize * maxTextWidth / measured));
        }
        if (paint.measureText(label) <= maxTextWidth) return label;
        final String ellipsis = "…";
        if (paint.measureText(ellipsis) > maxTextWidth) return "";
        int end = label.length();
        while (end > 0 && paint.measureText(label, 0, end) +
                paint.measureText(ellipsis) > maxTextWidth) {
            end--;
        }
        return label.substring(0, end) + ellipsis;
    }

    @Override
    protected boolean shouldDrawGrayBackground() {
        return expanded;
    }

    @Override
    protected boolean isGrayBackgroundCircular() {
        return true;
    }

    @Override
    protected boolean isPointInsideInteractiveRegion(float x, float y) {
        float radius = Math.min(getWidth(), getHeight()) * (expanded ? 0.5f : 0.22f);
        float dx = x - getWidth() / 2f;
        float dy = y - getHeight() / 2f;
        return dx * dx + dy * dy <= radius * radius;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (actions.isEmpty()) {
                    return true;
                }
                float dx = event.getX() - getWidth() / 2f;
                float dy = event.getY() - getHeight() / 2f;
                float radius = (float) Math.sqrt(dx * dx + dy * dy);
                if (radius > Math.min(getWidth(), getHeight()) * 0.22f) {
                    return false;
                }
                expanded = true;
                selectedIndex = -1;
                setPressed(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                selectedIndex = findSegment(event.getX(), event.getY());
                setPressed(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                int action = selectedIndex;
                selectedIndex = -1;
                expanded = false;
                setPressed(false);
                invalidate();
                if (action >= 0 && action < actions.size()) {
                    sendAction(actions.get(action));
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                selectedIndex = -1;
                expanded = false;
                setPressed(false);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private int findSegment(float x, float y) {
        float dx = x - getWidth() / 2f;
        float dy = y - getHeight() / 2f;
        float radius = (float) Math.sqrt(dx * dx + dy * dy);
        if (radius < Math.min(getWidth(), getHeight()) * 0.2f) {
            return -1;
        }
        double degrees = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
        if (degrees < 0) {
            degrees += 360.0;
        }
        return Math.min(actions.size() - 1,
                (int) (degrees / (360.0 / actions.size())));
    }

    private void sendAction(Action action) {
        for (int i = 0; i < action.keys.length; i++) {
            final int key = action.keys[i];
            virtualController.getHandler().postDelayed(
                    () -> sendKey(key, KeyEvent.ACTION_DOWN),
                    i * KEY_DOWN_INTERVAL_MS);
        }
        for (int i = action.keys.length - 1; i >= 0; i--) {
            final int key = action.keys[i];
            long delay = Math.max(0, action.keys.length - 1) * KEY_DOWN_INTERVAL_MS +
                    MIN_KEY_HOLD_MS + (action.keys.length - 1L - i) * KEY_UP_INTERVAL_MS;
            virtualController.getHandler().postDelayed(
                    () -> sendKey(key, KeyEvent.ACTION_UP), delay);
        }
        virtualController.vibrate(KeyEvent.ACTION_DOWN);
    }

    private void sendKey(int key, int action) {
        KeyEvent event = new KeyEvent(action, key);
        event.setSource(0);
        virtualController.sendKeyEvent(event);
    }

    @Override
    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = super.getConfiguration();
        configuration.put("RADIAL_NAME", radialName);
        configuration.put("RADIAL_SPEC", actionSpec);
        return configuration;
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        setRadialName(configuration.optString("RADIAL_NAME", radialName));
        setActionSpec(configuration.optString("RADIAL_SPEC", actionSpec));
    }
}
