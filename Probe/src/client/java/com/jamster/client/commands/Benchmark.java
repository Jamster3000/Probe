package com.jamster.client.commands;

import com.jamster.client.benchmark.BenchmarkManager;
import com.jamster.client.benchmark.BenchmarkSession;
import com.jamster.probe.MetricsCollector;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

public class Benchmark {

    private static MetricsCollector activeMetrics;

    public static void setMetricsCollector(MetricsCollector collector) {
        activeMetrics = collector;
    }

    public static MetricsCollector getActiveMetrics() {
        return activeMetrics;
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                literal("probe")
                    .then(literal("benchmark")
                        .then(literal("start")
                            .then(argument("name", StringArgumentType.word())
                                .executes(context -> startBenchmark(StringArgumentType.getString(context, "name"), 0, context.getSource()))
                                .then(argument("seconds", IntegerArgumentType.integer(1))
                                    .executes(context -> startBenchmark(
                                        StringArgumentType.getString(context, "name"),
                                        IntegerArgumentType.getInteger(context, "seconds"),
                                        context.getSource()
                                    ))
                                )
                            )
                        )
                        .then(literal("stop")
                            .executes(context -> stopBenchmark(context.getSource()))
                        )
                        .then(literal("list")
                            .executes(context -> listBenchmarks(context.getSource()))
                        )
                        .then(literal("view")
                            .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(BenchmarkManager.listSaved(), builder))
                                .executes(context -> viewBenchmark(StringArgumentType.getString(context, "name"), context.getSource()))
                            )
                        )
                        .then(literal("delete")
                            .then(literal("all")
                                .executes(context -> deleteAllBenchmarks(context.getSource()))
                            )
                            .then(argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(BenchmarkManager.listSaved(), builder))
                                .executes(context -> deleteBenchmark(StringArgumentType.getString(context, "name"), context.getSource()))
                            )
                        )
                        .then(literal("gui")
                            .executes(context -> {
                                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                                client.execute(() -> {
                                    client.gui.setScreen(new com.jamster.client.benchmark.ui.BenchmarkScreen());
                                });
                                return 1;
                            })
                        )
                        .then(literal("compare")
                            .then(argument("run1", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(BenchmarkManager.listSaved(), builder))
                                .then(argument("run2", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(BenchmarkManager.listSaved(), builder))
                                    .executes(context -> compareBenchmarks(
                                        StringArgumentType.getString(context, "run1"),
                                        StringArgumentType.getString(context, "run2"),
                                        context.getSource()
                                    ))
                                )
                            )
                        )
                    )
            );
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static int startBenchmark(String name, int seconds, FabricClientCommandSource source) {
        if (BenchmarkManager.isRecording()) {
            source.sendFeedback(Component.literal("A benchmark is already running (" + BenchmarkManager.getActiveName() + "). Stop it first.").withStyle(ChatFormatting.RED));
            return 0;
        }
        BenchmarkManager.start(name, seconds, activeMetrics);
        if (seconds > 0) {
            source.sendFeedback(Component.literal("Started benchmark '" + name + "' for " + seconds + " seconds.").withStyle(ChatFormatting.GREEN));
        } else {
            source.sendFeedback(Component.literal("Started benchmark '" + name + "' (manual stop).").withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    private static int stopBenchmark(FabricClientCommandSource source) {
        if (!BenchmarkManager.isRecording()) {
            source.sendFeedback(Component.literal("No active benchmark recording to stop.").withStyle(ChatFormatting.RED));
            return 0;
        }
        BenchmarkSession run = BenchmarkManager.stop();
        if (run != null) {
            source.sendFeedback(Component.literal(String.format("Stopped benchmark '%s' (%.1fs, %d samples). Saved to disk.", run.name, run.durationMs / 1000.0, run.samples)).withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    private static int listBenchmarks(FabricClientCommandSource source) {
        List<String> runs = BenchmarkManager.listSaved();
        if (runs.isEmpty()) {
            source.sendFeedback(Component.literal("No benchmark recordings found in logs/probe_benchmarks/").withStyle(ChatFormatting.GRAY));
        } else {
            source.sendFeedback(Component.literal("Saved Benchmarks: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.join(", ", runs)).withStyle(ChatFormatting.WHITE)));
        }
        return 1;
    }

    private static int viewBenchmark(String name, FabricClientCommandSource source) {
        BenchmarkSession session = BenchmarkManager.load(name);
        if (session == null) {
            source.sendFeedback(Component.literal("Could not find benchmark recording: " + name).withStyle(ChatFormatting.RED));
            return 0;
        }

        List<Component> reportLines = BenchmarkManager.buildViewComponents(session);
        for (Component line : reportLines) {
            source.sendFeedback(line);
        }
        return 1;
    }

    private static int deleteBenchmark(String name, FabricClientCommandSource source) {
        if (BenchmarkManager.delete(name)) {
            source.sendFeedback(Component.literal("Deleted benchmark recording: " + name).withStyle(ChatFormatting.GREEN));
            return 1;
        } else {
            source.sendFeedback(Component.literal("Could not find benchmark recording: " + name).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int deleteAllBenchmarks(FabricClientCommandSource source) {
        int count = BenchmarkManager.deleteAll();
        source.sendFeedback(Component.literal("Deleted " + count + " benchmark recording(s).").withStyle(ChatFormatting.GREEN));
        return count;
    }

    private static int compareBenchmarks(String run1, String run2, FabricClientCommandSource source) {
        BenchmarkSession b1 = BenchmarkManager.load(run1);
        BenchmarkSession b2 = BenchmarkManager.load(run2);

        if (b1 == null) {
            source.sendFeedback(Component.literal("Could not find benchmark recording: " + run1).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (b2 == null) {
            source.sendFeedback(Component.literal("Could not find benchmark recording: " + run2).withStyle(ChatFormatting.RED));
            return 0;
        }

        List<Component> reportLines = BenchmarkManager.buildComparisonComponents(b1, b2);
        for (Component line : reportLines) {
            source.sendFeedback(line);
        }
        return 1;
    }
}