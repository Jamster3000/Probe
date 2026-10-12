package com.jamster.client.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jamster.probe.MetricsCollector;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.LoggerFactory;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BenchmarkManager {
    private static final Path BENCHMARK_DIR = Paths.get("logs/probe_benchmarks");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get("config/probe");
    private static final Path THRESHOLD_FILE = CONFIG_DIR.resolve("thresholds.json");

    private static BenchmarkSession activeSession = null;
    private static long targetDurationMs = 0;
    private static long recordingStartTimeMs = 0;
    private static int currentTargetDurationSec = 0;

    public record ThresholdRule(double green, double yellow, boolean higherIsBetter) {}

    private static Map<String, ThresholdRule> THRESHOLDS = new HashMap<>();

    static {
        loadThresholds();
    }

    private static void loadThresholds() {
        try {
            Files.createDirectories(CONFIG_DIR);
            
            // If the threshold file doesn't exist yet, create a default one
            if (!Files.exists(THRESHOLD_FILE)) {
                Map<String, ThresholdRule> defaults = Map.ofEntries(
                    Map.entry("Avg FPS", new ThresholdRule(60.0, 30.0, true)),
                    Map.entry("1% Low FPS", new ThresholdRule(60.0, 30.0, true)),
                    Map.entry("Avg MSPT", new ThresholdRule(40.0, 50.0, false)),
                    Map.entry("Avg TPS", new ThresholdRule(19.5, 15.0, true)),
                    Map.entry("GC Cycles", new ThresholdRule(5.0, 20.0, false)),
                    Map.entry("GC Time", new ThresholdRule(5.0, 20.0, false)),
                    Map.entry("Entity Rndr", new ThresholdRule(5.0, 15.0, false)),
                    Map.entry("GUI Rndr", new ThresholdRule(5.0, 15.0, false)),
                    Map.entry("Avg Alloc", new ThresholdRule(50.0, 150.0, false)),
                    Map.entry("Peak Alloc", new ThresholdRule(50.0, 150.0, false))
                );
                Files.writeString(THRESHOLD_FILE, GSON.toJson(defaults));
            }

            // Read the JSON config file from disk
            String jsonContent = Files.readString(THRESHOLD_FILE);
            Type type = new TypeToken<Map<String, ThresholdRule>>() {}.getType();
            Map<String, ThresholdRule> parsed = GSON.fromJson(jsonContent, type);
            
            if (parsed != null) {
                THRESHOLDS = parsed;
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").error("Failed to load thresholds.json, using empty defaults", e);
        }
    }

    public static ChatFormatting getValueColor(String label, double value) {
        ThresholdRule rule = THRESHOLDS.get(label);
        if (rule == null) return ChatFormatting.WHITE;

        if (rule.higherIsBetter()) {
            if (value >= rule.green()) return ChatFormatting.GREEN;
            if (value >= rule.yellow()) return ChatFormatting.YELLOW;
            return ChatFormatting.RED;
        } else {
            if (value <= rule.green()) return ChatFormatting.GREEN;
            if (value <= rule.yellow()) return ChatFormatting.YELLOW;
            return ChatFormatting.RED;
        }
    }

    public static String getTooltipKey(String label) {
        return switch (label) {
            case "Avg FPS" -> "probe.tooltip.fps";
            case "1% Low FPS" -> "probe.tooltip.1pct";
            case "Avg MSPT" -> "probe.tooltip.mspt";
            case "Avg TPS" -> "probe.tooltip.tps";
            case "Peak Mem" -> "probe.tooltip.memory";
            case "GC Cycles" -> "probe.tooltip.gc_cycles";
            case "GC Time" -> "probe.tooltip.gc_time";
            case "Entity Rndr" -> "probe.tooltip.entity_render";
            case "GUI Rndr" -> "probe.tooltip.gui_render";
            case "Avg Alloc", "Peak Alloc" -> "probe.tooltip.alloc_rate";
            default -> null;
        };
    }

    public static boolean isRecording() {
        return activeSession != null;
    }

    public static String getActiveName() {
        return activeSession != null ? activeSession.name : "";
    }

    public static void start(String name, int durationSeconds, MetricsCollector currentMetrics) {
        activeSession = new BenchmarkSession(name, currentMetrics);
        activeSession.startSnapshot = com.jamster.probe.WorldSnapshot.capture();
        targetDurationMs = durationSeconds > 0 ? durationSeconds * 1000L : 0;
        currentTargetDurationSec = durationSeconds;
        recordingStartTimeMs = System.currentTimeMillis();
    }

    public static int getRemainingSeconds() {
        if (!isRecording()) return 0;

        long elapsedMs = System.currentTimeMillis() - recordingStartTimeMs;
        int elapsedsec = (int) (elapsedMs / 1000);
        int remaining = currentTargetDurationSec - elapsedsec;

        return Math.max(0, remaining);
    }

    public static void tick(MetricsCollector metrics) {
        if (activeSession == null) return;
        if (metrics != null) activeSession.sample(metrics);

        if (targetDurationMs > 0 && activeSession.getElapsedTimeMs() >= targetDurationMs) {
            BenchmarkSession run = stop();
            if (run != null) {
                sendChatMessage(String.format("§aBenchmark '%s' finished after %ds (%d samples).", run.name, run.durationMs / 1000, run.samples));
            }
        }
    }

    public static BenchmarkSession stop() {
        if (activeSession == null) return null;
        activeSession.endSnapshot = com.jamster.probe.WorldSnapshot.capture();
        activeSession.finalizeRun();
        BenchmarkSession finished = activeSession;
        save(finished);
        activeSession = null;
        targetDurationMs = 0;
        return finished;
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(BENCHMARK_DIR.resolve(name + ".json"));
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").error("Failed to delete benchmark " + name, e);
            return false;
        }
    }

    public static int deleteAll() {
        int count = 0;
        File folder = BENCHMARK_DIR.toFile();
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, filename) -> filename.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) count++;
                }
            }
        }
        return count;
    }

    private static void sendChatMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    public static void save(BenchmarkSession session) {
        try {
            Files.createDirectories(BENCHMARK_DIR);
            Files.writeString(BENCHMARK_DIR.resolve(session.name + ".json"), GSON.toJson(session));
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").error("Failed to save benchmark " + session.name, e);
        }
    }

    public static BenchmarkSession load(String name) {
        try {
            Path filePath = BENCHMARK_DIR.resolve(name + ".json");
            if (Files.exists(filePath)) {
                return GSON.fromJson(Files.readString(filePath), BenchmarkSession.class);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").error("Failed to load benchmark " + name, e);
        }
        return null;
    }

    public static List<String> listSaved() {
        List<String> names = new ArrayList<>();
        File folder = BENCHMARK_DIR.toFile();
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, filename) -> filename.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    names.add(file.getName().replace(".json", ""));
                }
            }
        }
        return names;
    }

    public static List<Component> buildComparisonComponents(BenchmarkSession b1, BenchmarkSession b2) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("====== PROBE BENCHMARK COMPARISON ======").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal(" Baseline: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(b1.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            .append(Component.literal("  vs  Target: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(b2.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        lines.add(Component.empty());

        lines.add(formatCompareMetricRow("Avg FPS", b1.avgFps, b2.avgFps, true, "%.1f"));
        lines.add(formatCompareMetricRow("1% Low FPS", b1.avgOnePercentLow, b2.avgOnePercentLow, true, "%.1f"));
        lines.add(formatCompareMetricRow("Avg MSPT", b1.avgMspt, b2.avgMspt, false, "%.1fms"));
        lines.add(formatCompareMetricRow("Avg TPS", b1.avgTps, b2.avgTps, true, "%.1f"));
        lines.add(formatCompareMetricRow("Peak Mem", b1.peakMemoryMb, b2.peakMemoryMb, false, "%.0fMB"));
        lines.add(formatCompareMetricRow("GC Cycles", b1.gcCyclesInRun, b2.gcCyclesInRun, false, "%.0f"));
        lines.add(formatCompareMetricRow("GC Time", b1.gcTimeInRunMs, b2.gcTimeInRunMs, false, "%.0fms"));
        lines.add(formatCompareMetricRow("Entity Rndr", b1.avgEntityRenderMs, b2.avgEntityRenderMs, false, "%.2fms"));
        lines.add(formatCompareMetricRow("GUI Rndr", b1.avgGuiRenderMs, b2.avgGuiRenderMs, false, "%.2fms"));
        lines.add(formatCompareMetricRow("Avg Alloc", b1.avgAllocRateMbPerSec, b2.avgAllocRateMbPerSec, false, "%.1fMB/s"));
        lines.add(formatCompareMetricRow("Peak Alloc", b1.peakAllocRateMbPerSec, b2.peakAllocRateMbPerSec, false, "%.1fMB/s"));

        return lines;
    }

    public static List<Component> buildViewComponents(BenchmarkSession session) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("====== PROBE BENCHMARK REPORT ======").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal(" Benchmark: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(session.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        lines.add(Component.empty());

        lines.add(formatViewMetricRow("Avg FPS", session.avgFps, true, "%.1f"));
        lines.add(formatViewMetricRow("1% Low FPS", session.avgOnePercentLow, true, "%.1f"));
        lines.add(formatViewMetricRow("Avg MSPT", session.avgMspt, false, "%.1fms"));
        lines.add(formatViewMetricRow("Avg TPS", session.avgTps, true, "%.1f"));
        lines.add(formatViewMetricRow("Peak Mem", session.peakMemoryMb, false, "%.0fMB"));
        lines.add(formatViewMetricRow("GC Cycles", session.gcCyclesInRun, false, "%.0f"));
        lines.add(formatViewMetricRow("GC Time", session.gcTimeInRunMs, false, "%.0fms"));
        lines.add(formatViewMetricRow("Entity Rndr", session.avgEntityRenderMs, false, "%.2fms"));
        lines.add(formatViewMetricRow("GUI Rndr", session.avgGuiRenderMs, false, "%.2fms"));
        lines.add(formatViewMetricRow("Avg Alloc", session.avgAllocRateMbPerSec, false, "%.1fMB/s"));
        lines.add(formatViewMetricRow("Peak Alloc", session.peakAllocRateMbPerSec, false, "%.1fMB/s"));

        return lines;
    }

    private static Component formatViewMetricRow(String label, double value, boolean higherIsBetter, String valueFormat) {
        String valueStr = String.format(valueFormat, value);
        MutableComponent row = Component.literal(String.format(" %-11s ", label + ":")).withStyle(ChatFormatting.YELLOW);
        row.append(Component.literal(valueStr).withStyle(getValueColor(label, value)));
        return row;
    }

    private static Component formatCompareMetricRow(String label, double val1, double val2, boolean higherIsBetter, String valueFormat) {
        double diff = val2 - val1;
        double pct = val1 != 0 ? (diff / val1) * 100.0 : 0.0;

        ChatFormatting diffColor;
        if (Math.abs(diff) < 0.01) {
            diffColor = ChatFormatting.GRAY;
        } else if ((diff > 0 && higherIsBetter) || (diff < 0 && !higherIsBetter)) {
            diffColor = ChatFormatting.GREEN;
        } else {
            diffColor = ChatFormatting.RED;
        }

        String v1Str = String.format(valueFormat, val1);
        String v2Str = String.format(valueFormat, val2);
        String diffStr = String.format("%s%s (%+.1f%%)", diff > 0 ? "+" : "", String.format(valueFormat, diff), pct);

        MutableComponent row = Component.literal(String.format(" %-11s ", label + ":")).withStyle(ChatFormatting.YELLOW);
        row.append(Component.literal(String.format("%-8s", v1Str)).withStyle(getValueColor(label, val1)));
        row.append(Component.literal(" → ").withStyle(ChatFormatting.WHITE));
        row.append(Component.literal(String.format("%-8s ", v2Str)).withStyle(getValueColor(label, val2)));
        row.append(Component.literal(diffStr).withStyle(diffColor));

        return row;
    }
}