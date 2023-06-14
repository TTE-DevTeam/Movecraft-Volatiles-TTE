package me.goodroach.movecraftvolatiles.config;

import me.goodroach.movecraftvolatiles.MovecraftVolatiles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {
    private File configFile;
    private FileConfiguration config;

    public ConfigManager() {
        configFile = new File(MovecraftVolatiles.getInstance().getDataFolder(), "config.yml");
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection volatileBlocks = config.getConfigurationSection("VolatileBlocks");
        if (volatileBlocks != null) {
            for (String key : volatileBlocks.getKeys(false)) {
                List<Material> materials = getBlocks(key);
                if (materials != null) {
                    for (Material material : materials) {
                        double explosivePower = config.getDouble("VolatileBlocks." + key + ".ExplosivePower", 1.0);
                        double explosionProbability = config.getDouble("VolatileBlocks." + key + ".ExplosionProbability", 1.0);
                        boolean isIncendiary = config.getBoolean("VolatileBlocks." + key + ".IsIncendiary", false);
                        System.out.println(material.toString());
                        System.out.println(explosivePower);
                        System.out.println(explosionProbability);
                        System.out.println(isIncendiary);
                        MovecraftVolatiles.getInstance().getVolatilesManager().addVolatileBlock(material, explosivePower, explosionProbability, isIncendiary);
                    }
                } else {
                    MovecraftVolatiles.getInstance().getLogger().log(
                            Level.WARNING, "[ERROR] Invalid Material: " + ChatColor.RED + key);
                }
            }
        } else {
            MovecraftVolatiles.getInstance().getLogger().log(
                    Level.WARNING, "[ERROR] Config not found!");
        }
    }

    private List<Material> getBlocks(String name) {
        List<Material> blocks = new ArrayList<>();
        if (name.startsWith("minecraft:")) {
            String tagName = name.substring(10);
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(tagName), Material.class);
            if (tag != null) {
                blocks.addAll(tag.getValues());
            } else {
                MovecraftVolatiles.getInstance().getLogger().log(
                        Level.WARNING, "Invalid tag: " + name);
            }
        } else {
            Material material = Material.matchMaterial(name.toUpperCase());
            if (material != null) {
                blocks.add(material);
            }
        }
        return blocks;
    }

}

