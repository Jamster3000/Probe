package com.jamster.probe.mixin;

import com.jamster.probe.TimingStore;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class MixinGui {
    private long startNanos;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onGuiRenderStart(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        startNanos = System.nanoTime();
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onGuiRenderEnd(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        TimingStore.addGuiRenderNanos(System.nanoTime() - startNanos);
    }
}