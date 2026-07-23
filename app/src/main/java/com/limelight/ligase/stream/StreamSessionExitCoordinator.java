package com.limelight.ligase.stream;

import java.util.concurrent.atomic.AtomicBoolean;

public final class StreamSessionExitCoordinator {
    public interface Callbacks {
        void disconnect();
        void endSession();
    }

    private final AtomicBoolean exitRequested = new AtomicBoolean();
    private final Callbacks callbacks;

    public StreamSessionExitCoordinator(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public boolean disconnect() {
        if (!exitRequested.compareAndSet(false, true)) {
            return false;
        }
        callbacks.disconnect();
        return true;
    }

    public boolean endSession() {
        if (!exitRequested.compareAndSet(false, true)) {
            return false;
        }
        callbacks.endSession();
        return true;
    }

    public boolean isExitRequested() {
        return exitRequested.get();
    }
}
