package com.jamster.probe.mixin;

import com.jamster.probe.StartupTimings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onMinecraftInitEnd(CallbackInfo ci) {
        StartupTimings.recordPhase("minecraft_init_end");
    }
}