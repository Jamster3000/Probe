package com.jamster.probe;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import java.util.HashMap;
import java.util.Map;

public class EntityTypeCounter {
    private final Map<String, Integer> entityCounts = new HashMap<>();
    private int totalEntityCount = 0;
    private String topEntityType = "none";
    private int topEntityCount = 0;

    public void countEntities(Iterable<Entity> entities) {
        entityCounts.clear();
        totalEntityCount = 0;
        topEntityCount = 0;
        topEntityType = "none";

        for (Entity entity : entities) {
            String entityTypeName = getEntityTypeName(entity.getType());
            int count = entityCounts.getOrDefault(entityTypeName, 0) + 1;
            entityCounts.put(entityTypeName, count);
            totalEntityCount++;

            if (count > topEntityCount) {
                topEntityCount = count;
                topEntityType = entityTypeName;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private String getEntityTypeName(EntityType<?> type) {
        try {
            var holder = type.builtInRegistryHolder();
            if (holder.unwrapKey().isPresent()) {
                String fullKey = holder.unwrapKey().get().toString();
                int lastColon = fullKey.lastIndexOf(':');
                return lastColon >= 0 ? fullKey.substring(lastColon + 1) : fullKey;
            }
        } catch (Exception e) {
            return "unknown";
        }
        return "unknown";
    }

    public int getTotalEntityCount() {
        return totalEntityCount;
    }

    public String getTopEntityType() {
        return topEntityType;
    }

    public int getTopEntityCount() {
        return topEntityCount;
    }

    public Map<String, Integer> getEntityCounts() {
        return new HashMap<>(entityCounts);
    }
}