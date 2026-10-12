package com.jamster.probe.mixin;

import com.jamster.probe.StartupTimings;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onSoundEngineInitEnd(CallbackInfo ci) {
        StartupTimings.recordPhase("sound_engine_end");
    }
}