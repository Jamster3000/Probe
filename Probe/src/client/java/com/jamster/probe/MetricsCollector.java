package com.jamster.probe;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

public class MetricsCollector {
    private long maxMemory;
    private long totalMemory;
    private long usedMemory;
    private long freeMemory;

    private int fps;
    private int onePercentLowFps;
    private double mspt;
    private double tps;

    private long gcCount;
    private long gcTimeMs;

    private int entityCount;
    private int loadedChunkCount;

    private double entityRenderMs;
    private double guiRenderMs;
    private double allocRateMbPerSec;
    private final AdvancedMemoryProfiler memoryProfiler;

    private int dirtyChunkSections;
    private int maxDirtyChunkSections;
    private String topEntityType;
    private int topEntityCount;


    private final List<Double> frameTimeHistory = new ArrayList<>();
    private long lastFrameNanos = System.nanoTime();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    private final EntityTypeCounter entityTypeCounter = new EntityTypeCounter();
    private final ChunkRebuildTracker chunkRebuildTracker = new ChunkRebuildTracker();
    private long lastChunkUpdateTime = 0;
    private static final long CHUNK_UPDATE_INTERVAL = 1_000_000_000;
    private long lastEntityUpdateTime = 0;
    private static final long ENTITY_UPDATE_INTERVAL = 1_000_000_000;

    public MetricsCollector() {
        this.memoryProfiler = new AdvancedMemoryProfiler();
    }

    public void update() {
        Runtime runtime = Runtime.getRuntime();

        this.maxMemory = runtime.maxMemory() / (1024 * 1024);
        this.totalMemory = runtime.totalMemory() / (1024 * 1024);
        this.freeMemory = runtime.freeMemory() / (1024 * 1024);
        this.usedMemory = this.totalMemory - this.freeMemory;

        memoryProfiler.tick();
        this.allocRateMbPerSec = memoryProfiler.getAllocationRate();

        long totalGcCount = 0;
        long totalGcTime = 0;
        for (GarbageCollectorMXBean bean: gcBeans) {
            long count = bean.getCollectionCount();
            long time = bean.getCollectionTime();
            if (count > 0) totalGcCount += count;
            if (time > 0) totalGcTime += time;
        }
        this.gcCount = totalGcCount;
        this.gcTimeMs = totalGcTime;

        this.entityRenderMs = TimingStore.getAndResetEntityRenderMs();
        this.guiRenderMs = TimingStore.getAndResetGuiRenderMs();

        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            this.fps = client.getFps();

            if (client.level != null) {
                this.entityCount = client.level.getEntityCount();
                if (client.level.getChunkSource() != null) {
                    this.loadedChunkCount = client.level.getChunkSource().getLoadedChunksCount();
                }

                long now = System.nanoTime();
                if (now - lastEntityUpdateTime >= ENTITY_UPDATE_INTERVAL) {
                    try {
                        AABB largeBox = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
                        var entities = client.level.getEntities(null, largeBox);
                        entityTypeCounter.countEntities(entities);
                        this.topEntityType = entityTypeCounter.getTopEntityType();
                        this.topEntityCount = entityTypeCounter.getTopEntityCount();
                    } catch (Exception e) {
                    }
                    lastEntityUpdateTime = now;
                }
            }

            if (client.getSingleplayerServer() != null) {
                double avgTickNanos = client.getSingleplayerServer().getAverageTickTimeNanos();
                this.mspt = avgTickNanos / 1_000_000.0;
                this.tps = Math.min(20.0, 1000.0 / Math.max(this.mspt, 50.0));
            } else {
                this.mspt = 0.0;
                this.tps = 20.0;
            }

            long now = System.nanoTime();
            if (now - lastChunkUpdateTime >= CHUNK_UPDATE_INTERVAL) {
                try {
                    if (client.levelRenderer != null) {
                        chunkRebuildTracker.updateDirtySectionCount(client.levelRenderer);
                    }
                } catch (Exception e) {
                    // Silently fail
                }
                lastChunkUpdateTime = now;
            }

            this.dirtyChunkSections = chunkRebuildTracker.getDirtyChunkSectionCount();
            this.maxDirtyChunkSections = chunkRebuildTracker.getMaxDirtyChunkSectionCount();
        }
    }

    public void recordFrame() {
        long now = System.nanoTime();
        double frameTimeMs = (now - lastFrameNanos) / 1_000_000.0;
        lastFrameNanos = now;

        synchronized (frameTimeHistory) {
            frameTimeHistory.add(frameTimeMs);
            if (frameTimeHistory.size() > 100) {
                frameTimeHistory.remove(0);
            }

            if (frameTimeHistory.size() >= 10) {
                List<Double> sorted = new ArrayList<>(frameTimeHistory);
                Collections.sort(sorted);
                int p99Index = (int) Math.ceil(sorted.size() * 0.99) - 1;
                double p99FrameTime = sorted.get(Math.max(0, p99Index));
                this.onePercentLowFps = p99FrameTime > 0 ? (int) (1000.0 / p99FrameTime) : this.fps;
            } else {
                this.onePercentLowFps = this.fps;
            }
        }
    }

    public long getUsedMemory() { return usedMemory; }
    public long getMaxMemory() { return maxMemory; }
    public long getTotalMemory() { return totalMemory; }
    public long getFreeMemory() { return freeMemory; }
    public int getFPS() { return fps; }
    public int getOnePercentLowFps() { return onePercentLowFps; }
    public double getMspt() { return mspt; }
    public double getTps() { return tps; }
    public long getGcCount() { return gcCount; }
    public long getGcTimeMs() { return gcTimeMs; }
    public int getEntityCount() { return entityCount; }
    public int getLoadedChunkCount() { return loadedChunkCount; }
    public double getEntityRenderMs() { return entityRenderMs; }
    public double getGuiRenderMs() { return guiRenderMs; }
    public double getAllocRateMbPerSec() { return allocRateMbPerSec; }
    public AdvancedMemoryProfiler getMemoryProfiler() { return memoryProfiler; }
    public int getDirtyChunkSections() { return dirtyChunkSections; }
    public int getMaxDirtyChunkSections() { return maxDirtyChunkSections; }
    public String getTopEntityType() { return topEntityType; }
    public int getTopEntityCount() { return topEntityCount; }
    public Map<String, Integer> getEntityTypeBreakdown() { return entityTypeCounter.getEntityCounts(); }
}