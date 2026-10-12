package com.jamster.client.benchmark.ui;

import com.jamster.client.benchmark.BenchmarkManager;
import com.jamster.client.benchmark.BenchmarkSession;
import com.jamster.client.commands.Benchmark;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public class BenchmarkScreen extends Screen {
    private enum State {
        LIST, VIEW, COMPARE
    }

    public static KeyMapping openKeybind;
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.parse("probe:category"));

    private static final int ITEMS_PER_PAGE = 5;
    private int page = 0;
    
    private State currentState = State.LIST;
    private String selectedForCompare = null;
    
    private BenchmarkSession viewSession = null;
    private BenchmarkSession compareTarget = null;
    
    private EditBox nameBox;
    private EditBox durationBox;
    private Button startButton;

    public BenchmarkScreen() {
        super(Component.literal("Probe Benchmarks"));
    }

    public static void registerKeybind() {
        openKeybind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.probe.open_benchmark",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKeybind.consumeClick()) {
                if (client.player != null && !(client.gui.screen() instanceof BenchmarkScreen)) {
                    client.setScreenAndShow(new BenchmarkScreen());
                }
            }
        });
    }

    @Override
    protected void init() {
        this.clearWidgets();
        
        switch (currentState) {
            case LIST -> initListState();
            case VIEW -> initViewState();
            case COMPARE -> initCompareState();
        }
    }

    private void updateStartButtonState() {
        if (this.startButton == null || this.nameBox == null || this.durationBox == null) return;
        
        String name = this.nameBox.getValue().trim();
        boolean validDuration = false;
        try {
            int duration = Integer.parseInt(this.durationBox.getValue().trim());
            validDuration = duration > 0;
        } catch (NumberFormatException ignored) {}

        this.startButton.active = !name.isEmpty() && validDuration;
    }

    private void initListState() {
        int startX = this.width / 2 - 150;
        int startY = 40;
        int buttonHeight = 20;
        
        // start/stop recording buttons
        if (BenchmarkManager.isRecording()) {
            this.addRenderableWidget(Button.builder(Component.literal("Stop Recording"), b -> {
                BenchmarkManager.stop();
                this.init();
            }).bounds(startX, 15, 120, buttonHeight).build());
        } else {
            // Name Input Box
            this.nameBox = new EditBox(this.font, startX, 15, 120, buttonHeight, Component.literal("Name"));
            this.nameBox.setHint(Component.literal("Run Name"));
            this.nameBox.setResponder(text -> this.updateStartButtonState());
            this.addRenderableWidget(this.nameBox);

            // Duration Input Box
            this.durationBox = new EditBox(this.font, startX + 125, 15, 45, buttonHeight, Component.literal("Duration"));
            this.durationBox.setValue("10");
            this.durationBox.setHint(Component.literal("Sec"));
            this.durationBox.setResponder(text -> this.updateStartButtonState());
            this.addRenderableWidget(this.durationBox);
            
            // Start Button
            this.startButton = Button.builder(Component.literal("Start Recording"), b -> {
                String name = this.nameBox.getValue().trim();
                int duration = -1;
                try {
                    duration = Integer.parseInt(this.durationBox.getValue().trim());
                } catch (NumberFormatException ignored) {}

                if (!name.isEmpty() && duration > 0) {
                    BenchmarkManager.start(name, duration, Benchmark.getActiveMetrics());
                    this.onClose(); // Closes the GUI upon starting
                }
            }).bounds(startX + 175, 15, 125, buttonHeight).build();
            this.addRenderableWidget(this.startButton);

            this.updateStartButtonState();
        }

        //List Saved Benchmarks
        List<String> runs = BenchmarkManager.listSaved();
        int maxPages = Math.max(0, (runs.size() - 1) / ITEMS_PER_PAGE);
        if (page > maxPages) page = maxPages;
        
        int yOffset = startY + 15;
        
        for (int i = page * ITEMS_PER_PAGE; i < Math.min(runs.size(), (page + 1) * ITEMS_PER_PAGE); i++) {
            String runName = runs.get(i);
            
            // View Button
            this.addRenderableWidget(Button.builder(Component.literal("View: " + runName), b -> {
                this.viewSession = BenchmarkManager.load(runName);
                if (this.viewSession != null) {
                    this.currentState = State.VIEW;
                    this.init();
                }
            }).bounds(startX, yOffset, 140, buttonHeight).build());
            
            // Compare Toggle Button
            Component compareText = selectedForCompare != null ? 
                (selectedForCompare.equals(runName) ? Component.literal("Cancel") : Component.literal("Match Target")) 
                : Component.literal("Select Compare");
                
            this.addRenderableWidget(Button.builder(compareText, b -> {
                if (selectedForCompare == null) {
                    selectedForCompare = runName;
                    this.init(); 
                } else if (selectedForCompare.equals(runName)) {
                    selectedForCompare = null;
                    this.init();
                } else {
                    this.viewSession = BenchmarkManager.load(selectedForCompare);
                    this.compareTarget = BenchmarkManager.load(runName);
                    
                    if (this.viewSession != null && this.compareTarget != null) {
                        this.currentState = State.COMPARE;
                        this.init();
                    }
                }
            }).bounds(startX + 145, yOffset, 95, buttonHeight).build());
            
            // Delete Button
            this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
                BenchmarkManager.delete(runName);
                if (runName.equals(selectedForCompare)) selectedForCompare = null;
                this.init();
            }).bounds(startX + 245, yOffset, 55, buttonHeight).build());

            yOffset += 25;
        }

        // Pagination Controls
        if (page > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
                page--;
                this.init();
            }).bounds(startX, yOffset, 50, buttonHeight).build());
        }
        if (page < maxPages) {
            this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
                page++;
                this.init();
            }).bounds(startX + 60, yOffset, 50, buttonHeight).build());
        }
        
        // Delete All
        this.addRenderableWidget(Button.builder(Component.literal("Delete All"), b -> {
            BenchmarkManager.deleteAll();
            selectedForCompare = null;
            this.page = 0;
            this.init();
        }).bounds(this.width - 90, this.height - 30, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> {
            this.onClose();
        }).bounds(this.width - 175, this.height - 30, 80, 20).build());
    }

    private void initViewState() {
        if (this.viewSession == null) {
            this.currentState = State.LIST;
            this.init();
            return;
        }
        renderTextLines(BenchmarkManager.buildViewComponents(this.viewSession));
    }

    private void initCompareState() {
        if (this.viewSession == null || this.compareTarget == null) {
            this.currentState = State.LIST;
            this.init();
            return;
        }
        renderTextLines(BenchmarkManager.buildComparisonComponents(this.viewSession, this.compareTarget));
    }

    private void renderTextLines(List<Component> lines) {
        int yOffset = 20;
        int startX = this.width / 2 - 150;

        for (Component line : lines) {
            StringWidget textWidget = new StringWidget(startX, yOffset, 300, 10, line, this.font);
            
            String plainText = line.getString();
            for (String label : List.of("Avg FPS", "1% Low FPS", "Avg MSPT", "Avg TPS", "Peak Mem", "GC Cycles", "GC Time", "Entity Rndr", "GUI Rndr", "Avg Alloc", "Peak Alloc")) {
                if (plainText.contains(label + ":")) {
                    String langKey = BenchmarkManager.getTooltipKey(label);
                    if (langKey != null) {
                        textWidget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable(langKey)
                        ));
                    }
                    break;
                }
            }
            
            this.addRenderableWidget(textWidget);
            yOffset += 12;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back to List"), b -> {
            this.currentState = State.LIST;
            this.selectedForCompare = null;
            this.init();
        }).bounds(this.width / 2 - 50, yOffset + 15, 100, 20).build());
    }
}