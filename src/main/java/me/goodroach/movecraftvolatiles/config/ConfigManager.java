package me.goodroach.movecraftvolatiles.config;

import me.goodroach.movecraftvolatiles.MovecraftVolatiles;
import me.goodroach.movecraftvolatiles.data.VolatileBlock;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumSet;
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

        Settings.enableArrowsPlacingFire = config.getBoolean("ArrowsPlaceFire", true);
        Settings.arrowsPlaceFireChance = config.getDouble("ArrowsPlaceFireChance", 0.5);
        Settings.projectilePassthrough = config.getBoolean("ProjectilePassthrough", true);

        List<VolatileBlock> volatiles = (List<VolatileBlock>) config.getList("VolatileBlocks");
        for (VolatileBlock volatileBlock : volatiles) {
            EnumSet<Material> materials;
            if (volatileBlock.blockMask().contains(",")) {
                materials = EnumSet.noneOf(Material.class);
                String[] strings = volatileBlock.blockMask().split(",");
                for (String mat : strings) {
                    try {
                        materials.addAll(Tags.parseMaterials(mat));
                    } catch (IllegalArgumentException iae) {
                        MovecraftVolatiles.getInstance().getLogger().log(
                                Level.WARNING, "[ERROR] Invalid Material or Tag: " + ChatColor.RED + mat);
                    }
                }
            } else {
                try {
                    materials = Tags.parseMaterials(volatileBlock.blockMask());
                } catch (IllegalArgumentException iae) {
                    MovecraftVolatiles.getInstance().getLogger().log(
                            Level.WARNING, "[ERROR] Invalid Material or Tag: " + ChatColor.RED + volatileBlock.blockMask());
                    continue;
                }
            }

            if (materials.isEmpty()) {
                continue;
            }

            for (Material material : materials) {
                MovecraftVolatiles.getInstance().getVolatilesManager().addVolatileBlock(volatileBlock, material);
            }
        }
    }
}

