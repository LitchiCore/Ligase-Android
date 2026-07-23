package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.limelight.R;

import org.json.JSONException;
import org.json.JSONObject;

/** A lockable gyroscope-to-relative-mouse control for fine aiming. */
public final class TouchKitGyroMouseButton extends keyBoardVirtualControllerElement
        implements SensorEventListener {
    private static final float BASE_PIXELS_PER_RADIAN = 500f;
    private static final float FILTER_ALPHA = 0.35f;
    private static final float MAX_EVENT_SECONDS = 0.05f;
    private static final int MAX_EVENT_DELTA = 96;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SensorManager sensorManager;
    private final Sensor gyroscope;

    private boolean active;
    private int sensitivityPercent = 100;
    private float deadzoneDegreesPerSecond = 1.5f;
    private boolean invertHorizontal;
    private boolean invertVertical;

    private long lastTimestamp;
    private float filteredHorizontal;
    private float filteredVertical;
    private float remainderX;
    private float remainderY;

    public TouchKitGyroMouseButton(KeyBoardController controller, Context context,
                                   String elementId) {
        super(controller, context, elementId);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gyroscope = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public boolean isGyroscopeAvailable() {
        return gyroscope != null;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean enabled) {
        boolean shouldEnable = enabled && gyroscope != null && isShown()
                && virtualController.getControllerMode() ==
                KeyBoardController.ControllerMode.Active;
        if (active == shouldEnable) return;

        active = shouldEnable;
        resetMotionState();
        if (active) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        } else if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        invalidate();
    }

    public int getSensitivityPercent() {
        return sensitivityPercent;
    }

    public void setSensitivityPercent(int sensitivityPercent) {
        this.sensitivityPercent = Math.max(10, Math.min(300, sensitivityPercent));
    }

    public float getDeadzoneDegreesPerSecond() {
        return deadzoneDegreesPerSecond;
    }

    public void setDeadzoneDegreesPerSecond(float deadzoneDegreesPerSecond) {
        this.deadzoneDegreesPerSecond = Math.max(0f,
                Math.min(5f, deadzoneDegreesPerSecond));
    }

    public boolean isInvertHorizontal() {
        return invertHorizontal;
    }

    public void setInvertHorizontal(boolean invertHorizontal) {
        this.invertHorizontal = invertHorizontal;
    }

    public boolean isInvertVertical() {
        return invertVertical;
    }

    public void setInvertVertical(boolean invertVertical) {
        this.invertVertical = invertVertical;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        float stroke = Math.max(2f, getDefaultStrokeWidth());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.max(0f, Math.min(getWidth(), getHeight()) / 2f - stroke);
        int color = applyForegroundOpacity(Color.WHITE);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(virtualController.getControllerMode() ==
                KeyBoardController.ControllerMode.Active
                ? (active ? applyForegroundOpacity(Color.WHITE) : getGrayOutlineColor())
                : getDefaultColor());
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setColor(color);
        paint.setStrokeWidth(Math.max(2f, stroke * 0.8f));
        float targetRadius = radius * 0.36f;
        canvas.drawCircle(cx, cy - radius * 0.10f, targetRadius, paint);
        canvas.drawLine(cx - targetRadius * 1.35f, cy - radius * 0.10f,
                cx - targetRadius * 0.55f, cy - radius * 0.10f, paint);
        canvas.drawLine(cx + targetRadius * 0.55f, cy - radius * 0.10f,
                cx + targetRadius * 1.35f, cy - radius * 0.10f, paint);
        canvas.drawLine(cx, cy - radius * 0.10f - targetRadius * 1.35f,
                cx, cy - radius * 0.10f - targetRadius * 0.55f, paint);
        canvas.drawLine(cx, cy - radius * 0.10f + targetRadius * 0.55f,
                cx, cy - radius * 0.10f + targetRadius * 1.35f, paint);

        paint.setStyle(Paint.Style.FILL);
        if (active) {
            canvas.drawCircle(cx, cy - radius * 0.10f,
                    Math.max(2f, targetRadius * 0.18f), paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(10f, radius * 0.27f));
        canvas.drawText(getContext().getString(active
                        ? R.string.touchkit_gyro_status_on
                        : R.string.touchkit_gyro_status_off),
                cx, cy + radius * 0.72f, paint);
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
                if (activate) {
                    if (gyroscope == null) {
                        Toast.makeText(getContext(), R.string.touchkit_gyro_unavailable,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        setActive(!active);
                        virtualController.vibrate(MotionEvent.ACTION_DOWN);
                        Toast.makeText(getContext(), active
                                        ? R.string.touchkit_gyro_enabled
                                        : R.string.touchkit_gyro_disabled,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!active || event.sensor.getType() != Sensor.TYPE_GYROSCOPE) return;
        if (lastTimestamp == 0L) {
            lastTimestamp = event.timestamp;
            return;
        }

        float seconds = Math.min(MAX_EVENT_SECONDS,
                Math.max(0f, (event.timestamp - lastTimestamp) / 1_000_000_000f));
        lastTimestamp = event.timestamp;
        if (seconds <= 0f) return;

        float[] mapped = mapRatesForRotation(event.values[0], event.values[1],
                getDisplayRotation());
        filteredHorizontal += FILTER_ALPHA * (mapped[0] - filteredHorizontal);
        filteredVertical += FILTER_ALPHA * (mapped[1] - filteredVertical);

        float deadzone = (float) Math.toRadians(deadzoneDegreesPerSecond);
        float horizontal = removeDeadzone(filteredHorizontal, deadzone);
        float vertical = removeDeadzone(filteredVertical, deadzone);
        float scale = BASE_PIXELS_PER_RADIAN * sensitivityPercent / 100f * seconds;
        remainderX += horizontal * scale * (invertHorizontal ? -1f : 1f);
        remainderY += vertical * scale * (invertVertical ? -1f : 1f);

        int dx = clampEventDelta((int) remainderX);
        int dy = clampEventDelta((int) remainderY);
        if (dx == 0 && dy == 0) return;
        remainderX = Math.abs(dx) == MAX_EVENT_DELTA ? 0f : remainderX - dx;
        remainderY = Math.abs(dy) == MAX_EVENT_DELTA ? 0f : remainderY - dy;
        virtualController.sendMouseMove(dx, dy);
    }

    static float[] mapRatesForRotation(float x, float y, int rotation) {
        // Mouse X follows rotation around the screen's vertical axis. Mouse Y follows
        // rotation around the screen's horizontal axis. Android sensor axes remain in
        // the device's natural orientation, so remap them for the current display.
        switch (rotation) {
            case Surface.ROTATION_90:
                return new float[]{-x, y};
            case Surface.ROTATION_180:
                return new float[]{-y, -x};
            case Surface.ROTATION_270:
                return new float[]{x, -y};
            case Surface.ROTATION_0:
            default:
                return new float[]{y, x};
        }
    }

    private int getDisplayRotation() {
        WindowManager windowManager = (WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        return windowManager == null ? Surface.ROTATION_0
                : windowManager.getDefaultDisplay().getRotation();
    }

    private static float removeDeadzone(float value, float deadzone) {
        float magnitude = Math.abs(value);
        if (magnitude <= deadzone) return 0f;
        return Math.copySign(magnitude - deadzone, value);
    }

    private static int clampEventDelta(int value) {
        return Math.max(-MAX_EVENT_DELTA, Math.min(MAX_EVENT_DELTA, value));
    }

    private void resetMotionState() {
        lastTimestamp = 0L;
        filteredHorizontal = 0f;
        filteredVertical = 0f;
        remainderX = 0f;
        remainderY = 0f;
    }

    @Override
    protected void onVisibilityChanged(android.view.View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) setActive(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        setActive(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = super.getConfiguration();
        configuration.put("GYRO_SENSITIVITY", sensitivityPercent);
        configuration.put("GYRO_DEADZONE", deadzoneDegreesPerSecond);
        configuration.put("GYRO_INVERT_X", invertHorizontal);
        configuration.put("GYRO_INVERT_Y", invertVertical);
        return configuration;
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        setActive(false);
        super.loadConfiguration(configuration);
        setSensitivityPercent(configuration.optInt("GYRO_SENSITIVITY", 100));
        setDeadzoneDegreesPerSecond((float) configuration.optDouble("GYRO_DEADZONE", 1.5));
        setInvertHorizontal(configuration.optBoolean("GYRO_INVERT_X", false));
        setInvertVertical(configuration.optBoolean("GYRO_INVERT_Y", false));
    }
}
