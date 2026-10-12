package com.jamster.probe.mixin;

import com.jamster.probe.StartupTimings;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public class MixinResourceManager {
    @Inject(method = "createReload", at = @At("HEAD"))
    private void onResourceReloadStart(CallbackInfoReturnable<?> cir) {
        StartupTimings.recordPhase("resource_reload_start");
    }

    @Inject(method = "createReload", at = @At("RETURN"))
    private void onResourceReloadEnd(CallbackInfoReturnable<?> cir) {
        StartupTimings.recordPhase("resource_reload_end");
    }
}