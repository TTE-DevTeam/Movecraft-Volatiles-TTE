package me.goodroach.movecraftvolatiles.data;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public class VolatilesManager {
    private Map<VolatileBlock.EReactionType, Map<Material, VolatileBlock>> volatileBlockMap;

    public VolatilesManager() {
        this.volatileBlockMap = new HashMap<>();
    }

    public VolatileBlock getVolatileBlock(VolatileBlock.EReactionType type, Material material) {
        if (volatileBlockMap.getOrDefault(type, null) == null) {
            return null;
        }
        return volatileBlockMap.get(type).getOrDefault(material, null);
    }

    public boolean isVolatileBlock(Material material) {
        return volatileBlockMap.keySet().contains(material);
    }

    public void addVolatileBlock(Material m, double power, double probability, boolean incendiary, boolean requireCraft, byte typeMask, String commandToRun) {
        VolatileBlock volatileBlock = new VolatileBlock(power, probability, incendiary, requireCraft, typeMask, commandToRun);
        for (VolatileBlock.EReactionType type : VolatileBlock.EReactionType.values()) {
            if (type.coveredByMask(volatileBlock)) {
                volatileBlockMap.computeIfAbsent(type, (t) -> {
                    return new HashMap<>();
                }).put(m, volatileBlock);
            }
        }
    }
}

