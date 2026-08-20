package com.limelight.binding.input.touch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.os.Looper;
import android.view.View;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class TouchContextGestureTest {
    @After
    public void drainMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks();
    }

    @Test
    public void absoluteTap_emitsReliableDownThenUp() {
        NvConnection connection = mock(NvConnection.class);
        AbsoluteTouchContext context = new AbsoluteTouchContext(connection, 0, sizedView(), false);
        context.setPointerCount(1);

        context.touchDownEvent(400, 300, 1_000, true);
        context.touchUpEvent(400, 300, 1_050);

        verify(connection).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        verify(connection, never()).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, MILLISECONDS);
        InOrder order = inOrder(connection);
        order.verify(connection).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        order.verify(connection).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
    }

    @Test
    public void absoluteDragAndCancel_doNotLeavePressedButton() {
        NvConnection dragConnection = mock(NvConnection.class);
        AbsoluteTouchContext drag = new AbsoluteTouchContext(dragConnection, 0, sizedView(), false);
        drag.setPointerCount(1);
        drag.touchDownEvent(200, 200, 1_000, true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, MILLISECONDS);
        drag.touchMoveEvent(260, 260, 1_150);
        drag.touchUpEvent(300, 300, 1_300);
        InOrder order = inOrder(dragConnection);
        order.verify(dragConnection).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        order.verify(dragConnection).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);

        NvConnection cancelConnection = mock(NvConnection.class);
        AbsoluteTouchContext cancelled = new AbsoluteTouchContext(cancelConnection, 0, sizedView(), false);
        cancelled.setPointerCount(1);
        cancelled.touchDownEvent(200, 200, 2_000, true);
        cancelled.cancelTouch();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(700, MILLISECONDS);
        verifyNoInteractions(cancelConnection);
    }

    @Test
    public void relativeTapLongPressDragCancelAndMultiTouch_areDistinct() {
        PreferenceConfiguration preferences = new PreferenceConfiguration();
        preferences.touchPadSensitivity = 100;
        preferences.touchPadYSensitity = 100;

        NvConnection tapConnection = mock(NvConnection.class);
        RelativeTouchContext tap = new RelativeTouchContext(
                tapConnection, 0, 1280, 720, sizedView(), preferences);
        tap.setPointerCount(1);
        tap.touchDownEvent(100, 100, 1_000, true);
        tap.touchUpEvent(100, 100, 1_100);
        verify(tapConnection).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, MILLISECONDS);
        verify(tapConnection).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);

        NvConnection dragConnection = mock(NvConnection.class);
        RelativeTouchContext drag = new RelativeTouchContext(
                dragConnection, 0, 1280, 720, sizedView(), preferences);
        drag.setPointerCount(1);
        drag.touchDownEvent(100, 100, 2_000, true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(650, MILLISECONDS);
        drag.touchMoveEvent(140, 140, 2_700);
        drag.touchUpEvent(140, 140, 2_750);
        InOrder dragOrder = inOrder(dragConnection);
        dragOrder.verify(dragConnection).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        dragOrder.verify(dragConnection).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);

        NvConnection cancelConnection = mock(NvConnection.class);
        RelativeTouchContext cancelled = new RelativeTouchContext(
                cancelConnection, 0, 1280, 720, sizedView(), preferences);
        cancelled.setPointerCount(1);
        cancelled.touchDownEvent(100, 100, 3_000, true);
        cancelled.cancelTouch();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(700, MILLISECONDS);
        verifyNoInteractions(cancelConnection);

        NvConnection multiConnection = mock(NvConnection.class);
        RelativeTouchContext primary = new RelativeTouchContext(
                multiConnection, 0, 1280, 720, sizedView(), preferences);
        primary.setPointerCount(2);
        primary.touchDownEvent(100, 100, 4_000, true);
        primary.touchUpEvent(100, 100, 4_100);
        verifyNoInteractions(multiConnection);
    }

    @Test
    public void trackpadTapLongHoldAndCancel_doNotCrossGestureStates() {
        NvConnection tapConnection = mock(NvConnection.class);
        TrackpadContext tap = new TrackpadContext(tapConnection, 0);
        tap.setPointerCount(1);
        tap.touchDownEvent(100, 100, 1_000, true);
        tap.touchUpEvent(100, 100, 1_100);
        verify(tapConnection).sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(230, MILLISECONDS);
        verify(tapConnection).sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);

        NvConnection holdConnection = mock(NvConnection.class);
        TrackpadContext hold = new TrackpadContext(holdConnection, 0);
        hold.setPointerCount(1);
        hold.touchDownEvent(100, 100, 2_000, true);
        hold.touchUpEvent(100, 100, 2_500);
        verifyNoInteractions(holdConnection);

        NvConnection cancelConnection = mock(NvConnection.class);
        TrackpadContext cancelled = new TrackpadContext(cancelConnection, 0);
        cancelled.setPointerCount(1);
        cancelled.touchDownEvent(100, 100, 3_000, true);
        cancelled.cancelTouch();
        cancelled.touchUpEvent(100, 100, 3_050);
        verifyNoInteractions(cancelConnection);
    }

    private static View sizedView() {
        View view = mock(View.class);
        when(view.getWidth()).thenReturn(1920);
        when(view.getHeight()).thenReturn(1080);
        return view;
    }
}
