package com.jamster.probe;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

public class StartupTimings {
    private static final long appStartNanos;
    
    static {
        long jvmStartMs = ManagementFactory.getRuntimeMXBean().getStartTime();
        long currentMs = System.currentTimeMillis();
        long elapsedSinceJvmStart = currentMs - jvmStartMs;
        appStartNanos = System.nanoTime() - (elapsedSinceJvmStart * 1_000_000);
    }

    private static final Map<String, Long> timings = new LinkedHashMap<>();

    public static void recordPhase(String phaseName) {
        long elapsedMs = (System.nanoTime() - appStartNanos) / 1_000_000;
        timings.put(phaseName, elapsedMs);
    }

    public static Map<String, Long> getAllTimings() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (var entry : timings.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static boolean isComplete() {
        return timings.containsKey("game_ready");
    }

    public static void reset() {
        timings.clear();
    }
}
