package com.jamster.probe.mixin;

import com.jamster.probe.TimingStore;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;    
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    private long startNanos;

    @Inject(method = "submit", at = @At("HEAD"))
    private <S extends EntityRenderState> void onSubmitStart(
        S state, CameraRenderState cameraRenderState, double x, double y, double z,
        PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CallbackInfo ci
    ) {
        startNanos = System.nanoTime();
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private <S extends EntityRenderState> void onSubmitEnd(
        S state, CameraRenderState cameraRenderState, double x, double y, double z,
        PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CallbackInfo ci
    ) {
        TimingStore.addEntityRenderNanos(System.nanoTime() - startNanos);
    }
}