package com.limelight.ligase.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class StreamSessionExitCoordinatorTest {
    @Test
    public void repeatedEndSessionIsIdempotent() {
        AtomicInteger disconnects = new AtomicInteger();
        AtomicInteger endings = new AtomicInteger();
        StreamSessionExitCoordinator coordinator = coordinator(disconnects, endings);

        assertTrue(coordinator.endSession());
        assertFalse(coordinator.endSession());
        assertFalse(coordinator.disconnect());
        assertEquals(0, disconnects.get());
        assertEquals(1, endings.get());
    }

    @Test
    public void disconnectDoesNotEndHostSession() {
        AtomicInteger disconnects = new AtomicInteger();
        AtomicInteger endings = new AtomicInteger();
        StreamSessionExitCoordinator coordinator = coordinator(disconnects, endings);

        assertTrue(coordinator.disconnect());
        assertEquals(1, disconnects.get());
        assertEquals(0, endings.get());
        assertTrue(coordinator.isExitRequested());
    }

    private StreamSessionExitCoordinator coordinator(
            AtomicInteger disconnects,
            AtomicInteger endings) {
        return new StreamSessionExitCoordinator(new StreamSessionExitCoordinator.Callbacks() {
            @Override
            public void disconnect() {
                disconnects.incrementAndGet();
            }

            @Override
            public void endSession() {
                endings.incrementAndGet();
            }
        });
    }
}
