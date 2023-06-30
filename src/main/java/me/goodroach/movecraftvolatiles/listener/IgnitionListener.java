package me.goodroach.movecraftvolatiles.listener;

import me.goodroach.movecraftvolatiles.MovecraftVolatiles;
import me.goodroach.movecraftvolatiles.data.VolatileBlock;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.util.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;

public class IgnitionListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL)
            return;
        if (event.getIgnitingEntity() == null)
            return;

        Block sourceBlock = event.getBlock();
        Block testBlock = sourceBlock.getRelative(BlockFace.EAST);
        if (!testBlock.getType().isBurnable())
            testBlock = sourceBlock.getRelative(BlockFace.WEST);
        if (!testBlock.getType().isBurnable())
            testBlock = sourceBlock.getRelative(BlockFace.NORTH);
        if (!testBlock.getType().isBurnable())
            testBlock = sourceBlock.getRelative(BlockFace.SOUTH);
        if (!testBlock.getType().isBurnable())
            testBlock = sourceBlock.getRelative(BlockFace.UP);
        if (!testBlock.getType().isBurnable())
            testBlock = sourceBlock.getRelative(BlockFace.DOWN);
        if (!testBlock.getType().isBurnable())
            return;

        // To prevent infinite recursion we call the event with SPREAD as the cause
        BlockIgniteEvent igniteEvent = new BlockIgniteEvent(testBlock, BlockIgniteEvent.IgniteCause.SPREAD, event.getIgnitingEntity());
        Bukkit.getPluginManager().callEvent(igniteEvent);
        if (igniteEvent.isCancelled())
            return;

        Location blockLocation = testBlock.getLocation();

        Craft craft = fastNearestPlayerCraftToLoc(blockLocation);
        if (craft == null || event.isCancelled()) {
            return;
        }

        if (!craft.getHitBox().contains(MathUtils.bukkit2MovecraftLoc(blockLocation))) {
            return;
        }

        Material material = testBlock.getType();
        if (!MovecraftVolatiles.getInstance().getVolatilesManager().isVolatileBlock(material)) {
            return;
        }

        double randomNumber = Math.random();
        VolatileBlock volatileBlock = MovecraftVolatiles.getInstance().getVolatilesManager().getVolatileBlock(material);
        if (volatileBlock.getExplosionProbability() < randomNumber) {
            return;
        }

        craft.getWorld().createExplosion(blockLocation, (float) volatileBlock.getExplosivePower(), volatileBlock.isIncendiary());
    }

    private Craft fastNearestPlayerCraftToLoc(Location source) {
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(source);
        Craft closest = null;
        long closestDistSquared = Long.MAX_VALUE;
        for (Craft other : CraftManager.getInstance()) {
            if (other.getWorld() != source.getWorld() || !(other instanceof Craft)) {
                continue;
            }

            long distSquared = other.getHitBox().getMidPoint().distanceSquared(loc);
            if (distSquared < closestDistSquared) {
                closestDistSquared = distSquared;
                closest = other;
            }
        }
        return closest;
    }
}

