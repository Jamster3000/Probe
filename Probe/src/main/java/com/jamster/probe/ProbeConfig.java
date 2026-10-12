package com.jamster.probe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.LoggerFactory;

public class ProbeConfig {
    private static final Path CONFIG_PATH = Paths.get("config/probe-config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ProbeConfig INSTANCE = load();

    public boolean enabled;
    public boolean logOnStartup;
    public boolean logMemoryStats;

    public ProbeConfig() {
        this.enabled = true;
        this.logOnStartup = true;
        this.logMemoryStats = true;
    }

    public static ProbeConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String content = Files.readString(CONFIG_PATH);
                ProbeConfig loaded = GSON.fromJson(content, ProbeConfig.class);
                return loaded != null ? loaded : new ProbeConfig();
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").warn("Failed to load config, using defaults", e);
        }
        ProbeConfig defaultConfig = new ProbeConfig();
        defaultConfig.save();
        return defaultConfig;
    }

    public void save() {
        try {
            if (CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").warn("Failed to save config", e);
        }
    }
}