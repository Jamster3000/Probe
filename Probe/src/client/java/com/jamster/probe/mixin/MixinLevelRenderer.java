package com.jamster.probe.mixin;

import com.jamster.probe.StartupTimings;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onLevelRendererEnd(CallbackInfo ci) {
        StartupTimings.recordPhase("level_renderer_ready");
        StartupTimings.recordPhase("game_ready");
    }
}
