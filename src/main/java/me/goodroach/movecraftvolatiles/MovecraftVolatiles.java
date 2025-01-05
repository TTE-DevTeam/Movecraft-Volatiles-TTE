package me.goodroach.movecraftvolatiles;

import me.goodroach.movecraftvolatiles.config.ConfigManager;
import me.goodroach.movecraftvolatiles.config.Settings;
import me.goodroach.movecraftvolatiles.data.VolatileBlock;
import me.goodroach.movecraftvolatiles.data.VolatilesManager;
import me.goodroach.movecraftvolatiles.listener.ArrowImpactListener;
import me.goodroach.movecraftvolatiles.listener.IgnitionListener;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

public final class MovecraftVolatiles extends JavaPlugin {

    private static MovecraftVolatiles instance;
    private ConfigManager configManager;
    private VolatilesManager volatilesManager;

    @Override
    public void onEnable() {
        ConfigurationSerialization.registerClass(VolatileBlock.class, "VolatileBlock");
        
        instance = this;

        volatilesManager = new VolatilesManager();

        getServer().getPluginManager().registerEvents(new IgnitionListener(), this);

        if (Settings.enableArrowsPlacingFire && Settings.arrowsPlaceFireChance > 0) {
            getServer().getPluginManager().registerEvents(new ArrowImpactListener(), this);
        }

        configManager = new ConfigManager();
        configManager.reloadConfig();
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static MovecraftVolatiles getInstance () {
        return instance;
    }

    public VolatilesManager getVolatilesManager() {
        return volatilesManager;
    }
}

