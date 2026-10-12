package com.jamster.probe;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.util.ArrayList;
import java.util.List;

public class AdvancedMemoryProfiler {
    private final ThreadMXBean threadBean;
    private final List<Long> gcPausesMs = new ArrayList<>();
    private long lastAllocatedBytes = 0;
    private long lastCheckTime = System.nanoTime();
    private double allocationRateMbPerSec = 0.0;

    public AdvancedMemoryProfiler() {
        this.threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (threadBean.isThreadAllocatedMemorySupported()) {
            threadBean.setThreadAllocatedMemoryEnabled(true);
        }

        // Subscribe to GC events to get exact pause durations
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            NotificationEmitter emitter = (NotificationEmitter) bean;
            emitter.addNotificationListener((notification, handback) -> {
                if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                    CompositeData cd = (CompositeData) notification.getUserData();
                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                    synchronized (gcPausesMs) {
                        gcPausesMs.add(info.getGcInfo().getDuration());
                    }
                }
            }, null, null);
        }
    }

    public void tick() {
        if (!threadBean.isThreadAllocatedMemoryEnabled()) return;
        
        long now = System.nanoTime();
        long currentAllocated = threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        
        if (lastAllocatedBytes > 0) {
            long diffBytes = currentAllocated - lastAllocatedBytes;
            double elapsedSecs = (now - lastCheckTime) / 1_000_000_000.0;
            this.allocationRateMbPerSec = (diffBytes / (1024.0 * 1024.0)) / elapsedSecs;
        }
        
        this.lastAllocatedBytes = currentAllocated;
        this.lastCheckTime = now;
    }

    public double getAllocationRate() { return allocationRateMbPerSec; }
    
    public List<Long> drainGcPauses() {
        synchronized (gcPausesMs) {
            List<Long> copy = new ArrayList<>(gcPausesMs);
            gcPausesMs.clear();
            return copy;
        }
    }
}