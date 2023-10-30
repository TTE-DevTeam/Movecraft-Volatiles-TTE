package me.goodroach.movecraftvolatiles.listener;

import me.goodroach.movecraftvolatiles.MovecraftVolatiles;
import me.goodroach.movecraftvolatiles.data.VolatileBlock;
import me.goodroach.movecraftvolatiles.tracking.Volatile;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.combat.MovecraftCombat;
import net.countercraft.movecraft.combat.features.tracking.DamageRecord;
import net.countercraft.movecraft.combat.features.tracking.DamageTracking;
import net.countercraft.movecraft.combat.features.tracking.events.CraftDamagedByEvent;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.util.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static org.bukkit.block.BlockFace.DOWN;
import static org.bukkit.block.BlockFace.EAST;
import static org.bukkit.block.BlockFace.NORTH;
import static org.bukkit.block.BlockFace.SOUTH;
import static org.bukkit.block.BlockFace.UP;
import static org.bukkit.block.BlockFace.WEST;

public class IgnitionListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL)
            return;

        @Nullable
        Player ignitingPlayer = null;
        if (event.getIgnitingEntity() instanceof Player) {
            ignitingPlayer = (Player) event.getIgnitingEntity();
            if (ignitingPlayer.getInventory().getItemInMainHand().getType() == Material.FIRE_CHARGE ||
                    ignitingPlayer.getInventory().getItemInOffHand().getType() == Material.FIRE_CHARGE) {
                return;
            }
        }

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

        if (!testBlock.getType().isBurnable() || testBlock == null) {
            return;
        }

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
        if (craft instanceof PlayerCraft && ignitingPlayer != null) {
            damagedCraft((PlayerCraft) craft, ignitingPlayer.getUniqueId());
        }
    }

    private void damagedCraft(@NotNull PlayerCraft craft, UUID playerUUID) {
        UUID sender = playerUUID;
        Player cause = MovecraftCombat.getInstance().getServer().getPlayer(sender);

        DamageRecord damageRecord = new DamageRecord(cause, craft.getPilot(), new Volatile());
        Bukkit.getPluginManager().callEvent(new CraftDamagedByEvent(craft, damageRecord));
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

