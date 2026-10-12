package com.jamster.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.LoggerFactory;

public class MetricsLogger {
    private static final Path LOG_DIR = Paths.get("logs");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Path STARTUP_LOG = LOG_DIR.resolve("probe_logging/startup_timings.csv");
    private static boolean startupLogged = false;
    private Path logFile;

    public MetricsLogger() {
        try {
            Files.createDirectories(LOG_DIR.resolve("probe_logging"));
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            this.logFile = LOG_DIR.resolve("probe_logging/probe_" + timestamp + ".csv");
            
            String header = "timestamp,used_memory_mb,max_memory_mb,fps,one_percent_low_fps,mspt,tps,gc_count,gc_time_ms,entities,chunks,dirty_sections,max_dirty_sections,top_entity_type,top_entity_count\n";
            Files.write(logFile, header.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").error("Failed to initialize metrics logger", e);
        }
    }

    public void log(MetricsCollector metrics) {
        if (!startupLogged) {
            logStartupTimings();
        }
        
        try {
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            String line = String.format("%s,%d,%d,%d,%d,%.2f,%.1f,%d,%d,%d,%d,%d,%d,%s,%d\n",
                timestamp,
                metrics.getUsedMemory(),
                metrics.getMaxMemory(),
                metrics.getFPS(),
                metrics.getOnePercentLowFps(),
                metrics.getMspt(),
                metrics.getTps(),
                metrics.getGcCount(),
                metrics.getGcTimeMs(),
                metrics.getEntityCount(),
                metrics.getLoadedChunkCount(),
                metrics.getDirtyChunkSections(),
                metrics.getMaxDirtyChunkSections(),
                metrics.getTopEntityType(),
                metrics.getTopEntityCount()
            );
            Files.write(logFile, line.getBytes(), StandardOpenOption.APPEND);
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").error("Failed to write metrics to file", e);
        }
    }

    private static void logStartupTimings() {
        if (startupLogged) return;
        
        try {
            Map<String, Long> timings = StartupTimings.getAllTimings();
            if (timings.isEmpty()) return;
            
            boolean headerExists = Files.exists(STARTUP_LOG);
            
            if (!headerExists) {
                StringBuilder header = new StringBuilder("timestamp");
                for (String phase : timings.keySet()) {
                    header.append(",").append(phase);
                }
                header.append("\n");
                Files.write(STARTUP_LOG, header.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder line = new StringBuilder(timestamp);
            for (long time : timings.values()) {
                line.append(",").append(time);
            }
            line.append("\n");
            Files.write(STARTUP_LOG, line.toString().getBytes(), StandardOpenOption.APPEND);
            startupLogged = true;
        } catch (Exception e) {
            LoggerFactory.getLogger("probe").error("Failed to log startup timings", e);
        }
    }
}