package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;

import com.limelight.R;

import org.json.JSONException;
import org.json.JSONObject;

final class TouchKitMouseScrollButton extends KeyBoardDigitalButton {
    private final int direction;
    private int scrollStep = 1;
    private boolean continuousScroll = true;
    private final Runnable repeatScroll = new Runnable() {
        @Override
        public void run() {
            sendStep();
            virtualController.getHandler().postDelayed(this,
                    Math.max(70, scrollStep * 8L + 10L));
        }
    };

    TouchKitMouseScrollButton(KeyBoardController controller, String elementId,
                              int direction, Context context) {
        super(controller, elementId, 1, context);
        this.direction = direction >= 0 ? 1 : -1;
        setIcon(this.direction > 0 ? R.drawable.touchkit_mouse_scroll_up
                : R.drawable.touchkit_mouse_scroll_down);
        setTriggerMode(TriggerMode.HOLD);
        addDigitalButtonListener(new DigitalButtonListener() {
            @Override public void onClick() { sendStep(); }

            @Override public void onLongClick() {
                if (!continuousScroll) return;
                virtualController.getHandler().removeCallbacks(repeatScroll);
                virtualController.getHandler().post(repeatScroll);
            }

            @Override public void onRelease() {
                virtualController.getHandler().removeCallbacks(repeatScroll);
            }
        });
    }

    private void sendStep() {
        // Some hosts normalize a single high-resolution wheel packet regardless of its
        // magnitude. Emit distinct standard wheel notches so every configured step is kept.
        for (int i = 0; i < scrollStep; i++) {
            virtualController.getHandler().postDelayed(
                    () -> virtualController.sendMouseScroll(direction), i * 8L);
        }
    }

    int getScrollStep() {
        return scrollStep;
    }

    void setScrollStep(int scrollStep) {
        this.scrollStep = Math.max(1, Math.min(20, scrollStep));
    }

    boolean isContinuousScroll() {
        return continuousScroll;
    }

    void setContinuousScroll(boolean continuousScroll) {
        this.continuousScroll = continuousScroll;
        if (!continuousScroll) {
            virtualController.getHandler().removeCallbacks(repeatScroll);
        }
    }

    @Override
    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = super.getConfiguration();
        configuration.put("SCROLL_STEP", scrollStep);
        configuration.put("CONTINUOUS_SCROLL", continuousScroll);
        return configuration;
    }

    @Override
    public void loadConfiguration(JSONObject configuration) throws JSONException {
        super.loadConfiguration(configuration);
        setScrollStep(configuration.optInt("SCROLL_STEP", 1));
        setContinuousScroll(configuration.optBoolean("CONTINUOUS_SCROLL", true));
        setTriggerMode(TriggerMode.HOLD);
    }
}
