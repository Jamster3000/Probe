package com.jamster.client.benchmark;

import com.jamster.probe.MetricsCollector;
import com.jamster.probe.WorldSnapshot;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkSession {
    public String name;
    public String timestamp;
    public long durationMs;
    public long samples;

    public double avgFps;
    public int minFps = Integer.MAX_VALUE;
    public int maxFps = Integer.MIN_VALUE;
    public double avgOnePercentLow;
    public double avgMspt;
    public double avgTps;
    public long peakMemoryMb;

    public double avgEntityRenderMs;
    public double avgGuiRenderMs;

    public double avgAllocRateMbPerSec;
    public double peakAllocRateMbPerSec;
    public List<Long> exactGcPausesMs = new ArrayList<>();
    
    public long initialGcCount;
    public long initialGcTimeMs;
    public long gcCyclesInRun;
    public long gcTimeInRunMs;

    public double avgEntities;
    public double avgChunks;

    public WorldSnapshot startSnapshot;
    public WorldSnapshot endSnapshot;

    private transient long startTime;
    private transient double totalFps;
    private transient double total1PercentLow;
    private transient double totalMspt;
    private transient double totalTps;
    private transient double totalEntities;
    private transient double totalChunks;
    
    private transient double totalEntityRenderMs;
    private transient double totalGuiRenderMs;
    private transient double totalAllocRate;

    public BenchmarkSession() {}

    public BenchmarkSession(String name, MetricsCollector initial) {
        this.name = name;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.startTime = System.currentTimeMillis();
        
        if (initial != null) {
            this.initialGcCount = initial.getGcCount();
            this.initialGcTimeMs = initial.getGcTimeMs();
        }
    }

    public long getStartTime() {
        return startTime;
    }

    public long getElapsedTimeMs() {
        return System.currentTimeMillis() - startTime;
    }

    public void sample(MetricsCollector metrics) {
        if (metrics == null) return;
        samples++;

        int fps = metrics.getFPS();
        totalFps += fps;
        minFps = Math.min(minFps, fps);
        maxFps = Math.max(maxFps, fps);

        total1PercentLow += metrics.getOnePercentLowFps();
        totalMspt += metrics.getMspt();
        totalTps += metrics.getTps();
        peakMemoryMb = Math.max(peakMemoryMb, metrics.getUsedMemory());
        totalEntities += metrics.getEntityCount();
        totalChunks += metrics.getLoadedChunkCount();

        // Rendering Samples
        totalEntityRenderMs += metrics.getEntityRenderMs();
        totalGuiRenderMs += metrics.getGuiRenderMs();

        // Memory Samples
        double allocRate = metrics.getAllocRateMbPerSec();
        totalAllocRate += allocRate;
        peakAllocRateMbPerSec = Math.max(peakAllocRateMbPerSec, allocRate);
        exactGcPausesMs.addAll(metrics.getMemoryProfiler().drainGcPauses());

        gcCyclesInRun = metrics.getGcCount() - initialGcCount;
        gcTimeInRunMs = metrics.getGcTimeMs() - initialGcTimeMs;
    }

    public void finalizeRun() {
        this.durationMs = System.currentTimeMillis() - startTime;
        if (samples > 0) {
            avgFps = totalFps / samples;
            avgOnePercentLow = total1PercentLow / samples;
            avgMspt = totalMspt / samples;
            avgTps = totalTps / samples;
            avgEntities = totalEntities / samples;
            avgChunks = totalChunks / samples;
            
            avgEntityRenderMs = totalEntityRenderMs / samples;
            avgGuiRenderMs = totalGuiRenderMs / samples;
            avgAllocRateMbPerSec = totalAllocRate / samples;
        }
        if (minFps == Integer.MAX_VALUE) minFps = 0;
        if (maxFps == Integer.MIN_VALUE) maxFps = 0;
    }
}