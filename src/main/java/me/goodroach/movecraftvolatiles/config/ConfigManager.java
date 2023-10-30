package me.goodroach.movecraftvolatiles.config;

import me.goodroach.movecraftvolatiles.MovecraftVolatiles;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumSet;
import java.util.logging.Level;

public class ConfigManager {
    private File configFile;
    private FileConfiguration config;

    public ConfigManager() {
        configFile = new File(MovecraftVolatiles.getInstance().getDataFolder(), "config.yml");
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        var section = config.getConfigurationSection("VolatileBlocks");
        if (section == null) {
            return;
        }

        for (var entry : section.getValues(false).entrySet()) {
            EnumSet<Material> materials = Tags.parseMaterials(entry.getKey());
            if (materials != null) {
                for (Material material : materials) {
                    var blockSection = section.getConfigurationSection(entry.getKey());
                    double explosivePower = blockSection.getDouble("ExplosivePower", 1.0);
                    double explosionProbability = blockSection.getDouble("ExplosionProbability", 1.0);
                    boolean isIncendiary = blockSection.getBoolean("IsIncendiary", false);
                    MovecraftVolatiles.getInstance().getVolatilesManager().addVolatileBlock(material, explosivePower, explosionProbability, isIncendiary);
                }
            } else {
                MovecraftVolatiles.getInstance().getLogger().log(
                        Level.WARNING, "[ERROR] Invalid Material or Tag: " + ChatColor.RED + entry.getKey());
            }
        }
    }
}

