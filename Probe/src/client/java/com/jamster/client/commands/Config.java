package com.jamster.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import com.jamster.probe.ProbeConfig;

public class Config {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                literal("probe")
                    .executes(context -> toggleEnabled(context.getSource()))
                    .then(literal("toggle")
                        .executes(context -> toggleEnabled(context.getSource()))
                    )
                    .then(literal("set")
                        .then(literal("enabled")
                            .then(argument("value", BoolArgumentType.bool())
                                .executes(context -> setSetting("enabled", BoolArgumentType.getBool(context, "value"), context.getSource()))
                            )
                        )
                        .then(literal("logOnStartup")
                            .then(argument("value", BoolArgumentType.bool())
                                .executes(context -> setSetting("logOnStartup", BoolArgumentType.getBool(context, "value"), context.getSource()))
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

    private static int toggleEnabled(FabricClientCommandSource source) {
        ProbeConfig config = ProbeConfig.INSTANCE;
        config.enabled = !config.enabled;
        config.save();
        source.sendFeedback(Component.literal("Probe is now " + (config.enabled ? "enabled" : "disabled")));
        return 1;
    }

    private static int setSetting(String key, boolean value, FabricClientCommandSource source) {
        ProbeConfig config = ProbeConfig.INSTANCE;
        switch (key) {
            case "enabled" -> config.enabled = value;
            case "logOnStartup" -> config.logOnStartup = value;
        }
        config.save();
        source.sendFeedback(Component.literal("Set " + key + " to " + value));
        return 1;
    }
}