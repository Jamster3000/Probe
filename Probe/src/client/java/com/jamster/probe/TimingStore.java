package com.jamster.probe;

import java.util.concurrent.atomic.AtomicLong;

public class TimingStore {
    private static final AtomicLong entityRenderNanos = new AtomicLong(0);
    private static final AtomicLong guiRenderNanos = new AtomicLong(0);

    public static void addEntityRenderNanos(long nanos) {
        entityRenderNanos.addAndGet(nanos);
    }

    public static void addGuiRenderNanos(long nanos) {
        guiRenderNanos.addAndGet(nanos);
    }

    public static double getAndResetEntityRenderMs() {
        return entityRenderNanos.getAndSet(0) / 1_000_000.0;
    }

    public static double getAndResetGuiRenderMs() {
        return guiRenderNanos.getAndSet(0) / 1_000_000.0;
    }
}