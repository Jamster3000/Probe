package com.jamster.probe;

import com.jamster.probe.mixin.LevelAccessor;
import net.minecraft.client.Minecraft;

public class WorldSnapshot {
    public int renderDistance;
    public String dimension;
    public String biome;
    public int blockEntityCount;

    public static WorldSnapshot capture() {
        WorldSnapshot snap = new WorldSnapshot();
        Minecraft client = Minecraft.getInstance();
        updateSnapshot(snap, client);
        return snap;
    }

    public static void updateSnapshot(WorldSnapshot snap, Minecraft client) {
        if (client.level != null && client.options != null && client.player != null) {
            snap.renderDistance = client.options.renderDistance().get();

            snap.dimension = client.level.dimension().identifier().toString();

            snap.biome = client.level.getBiome(client.player.blockPosition())
                .unwrapKey()
                .map(k -> k.identifier().toString())
                .orElse("unknown");

            snap.blockEntityCount = ((LevelAccessor) client.level).getBlockEntityTickers().size();
        }
    }
}