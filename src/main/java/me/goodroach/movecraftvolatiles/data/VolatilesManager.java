package me.goodroach.movecraftvolatiles.data;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public class VolatilesManager {
    private Map<Material, VolatileBlock> volatileBlockMap;

    public VolatilesManager() {
        this.volatileBlockMap = new HashMap<>();
    }

    public VolatileBlock getVolatileBlock(Material material) {
        if (volatileBlockMap.get(material) == null) {
            return null;
        }
        return volatileBlockMap.get(material);
    }

    public boolean isVolatileBlock(Material material) {
        return volatileBlockMap.keySet().contains(material);
    }

    public void addVolatileBlock(Material m, double power, double probability, boolean incendiary, boolean requireCraft, byte typeMask) {
        volatileBlockMap.put(m, new VolatileBlock(power, probability, incendiary, requireCraft, typeMask));
    }
}

