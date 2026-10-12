package com.jamster.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jamster.probe.MetricsCollector;
import com.jamster.probe.MetricsLogger;
import com.jamster.probe.ProbeConfig;
import com.jamster.client.commands.Config;
import com.jamster.client.commands.Benchmark;
import com.jamster.client.benchmark.BenchmarkManager;
import com.jamster.client.benchmark.ui.BenchmarkScreen;

public class ProbeClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("probe");
    private static MetricsCollector metricsCollector;
    private static MetricsLogger metricsLogger;

    @Override
    public void onInitializeClient() {
        metricsCollector = new MetricsCollector();
        metricsLogger = new MetricsLogger();

        Config.register();
        Benchmark.setMetricsCollector(metricsCollector);
        Benchmark.register();
        BenchmarkScreen.registerKeybind();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (metricsCollector != null && metricsLogger != null) {
                metricsCollector.update();
                BenchmarkManager.tick(metricsCollector);
                metricsLogger.log(metricsCollector);
            }
        });

        HudElementRegistry.addLast(
            Identifier.parse("probe:hud_overlay"),
            ProbeClient::renderHUD
        );
    }

    private static void renderHUD(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!ProbeConfig.INSTANCE.enabled || metricsCollector == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // Record per-frame timing for 1% low FPS
        metricsCollector.recordFrame();

        int lineHeight = client.font.lineHeight + 2;
        int x = 10;
        int y = 10;

        // recording indicator
        if (BenchmarkManager.isRecording()) {
            int remainingSecs = BenchmarkManager.getRemainingSeconds();
            String recText = "* RECORDING BENCHMARK - " + remainingSecs + "s";
            graphics.text(client.font, recText, x, y, 0xFFFF5555, true);
            y += lineHeight + 5;
        }

        // FPS and MSPT metrics
        String line1 = String.format("FPS: %d (1%% Low: %d) | MSPT: %.1fms | TPS: %.1f", 
            metricsCollector.getFPS(), metricsCollector.getOnePercentLowFps(), 
            metricsCollector.getMspt(), metricsCollector.getTps());

        // Memory and GC metrics
        String line2 = String.format("Mem: %dMB / %dMB | GC: %d calls (%dms total)", 
            metricsCollector.getUsedMemory(), metricsCollector.getMaxMemory(), 
            metricsCollector.getGcCount(), metricsCollector.getGcTimeMs());

        // Entity and chunk metrics
        String line3 = String.format("World: %d Entities (%s x%d) | %d Chunks | Dirty: %d", 
            metricsCollector.getEntityCount(), metricsCollector.getTopEntityType(), metricsCollector.getTopEntityCount(),
            metricsCollector.getLoadedChunkCount(), metricsCollector.getDirtyChunkSections());
            
        // some advanced metrics
        String line4 = String.format("Render: Entity %.2fms | GUI %.2fms | Alloc: %.1f MB/s",
            metricsCollector.getEntityRenderMs(), metricsCollector.getGuiRenderMs(), metricsCollector.getAllocRateMbPerSec());

        graphics.text(client.font, line1, x, y, 0xFF55FF55, true);
        graphics.text(client.font, line2, x, y + lineHeight, 0xFFFFFF55, true);
        graphics.text(client.font, line3, x, y + (lineHeight * 2), 0xFF55FFFF, true);
        graphics.text(client.font, line4, x, y + (lineHeight * 3), 0xFFFFAA00, true);
    }
}