package com.jamster.probe;

import net.minecraft.client.renderer.LevelRenderer;

import com.jamster.probe.mixin.LevelRendererAccessor;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkRebuildTracker {
    private final AtomicInteger dirtyChunkSections = new AtomicInteger(0);
    private int maxDirtyChunkSections = 0;

    public void updateDirtySectionCount(LevelRenderer renderer) {
        if (renderer == null) return;

        int count = 0;
        try {
            LevelRendererAccessor accessor = (LevelRendererAccessor) (Object) renderer;
            var visibleSections = accessor.getVisibleSections();
            if (visibleSections != null) {
                count = visibleSections.size();
            }
        } catch (Exception e) {
            // Silently ignore
        }

        dirtyChunkSections.set(count);
        if (count > maxDirtyChunkSections) {
            maxDirtyChunkSections = count;
        }
    }

    public int getDirtyChunkSectionCount() {
        return dirtyChunkSections.get();
    }

    public int getMaxDirtyChunkSectionCount() {
        return maxDirtyChunkSections;
    }

    public void resetMaxCount() {
        maxDirtyChunkSections = 0;
    }
}