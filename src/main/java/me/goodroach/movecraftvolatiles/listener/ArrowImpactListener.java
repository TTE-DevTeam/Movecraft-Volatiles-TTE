package me.goodroach.movecraftvolatiles.listener;

import me.goodroach.movecraftvolatiles.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

public class ArrowImpactListener implements Listener {

    // TODO: Colored tracers for potion arrows => Color of the potion
    // TODO: Add somethign special for spectral arrows => They dont cause volatile reactions but can place fires if on fire

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHitBlock(final ProjectileHitEvent event) {
        Entity projectile = event.getEntity();
        if (!(projectile instanceof AbstractArrow) || projectile instanceof Trident) {
            return;
        }

        if (!Settings.enableArrowsPlacingFire) {
            return;
        }

        // We are only interested in burning arrows
        if (projectile.getFireTicks() <= 0) {
            return;
        }

        if (event.getHitBlock() == null || event.getHitBlockFace() == null) {
            return;
        }

        if (IgnitionListener.canBlockBurnFunction == null) {
            throw new RuntimeException("Somehow, the function to retrieve whether or not a block can burn is null!");
        }

        if (!IgnitionListener.canBlockBurnFunction.apply(event.getHitBlock())) {
            return;
        }

        if (Math.random() >= Settings.arrowsPlaceFireChance) {
            return;
        }

        // All good, set fire on the face the arrow is on
        BlockFace face = event.getHitBlockFace();
        Block fireBlock = event.getHitBlock().getRelative(face);
        // Already fire? Ignore it!
        if (fireBlock.getType() == Material.FIRE || fireBlock.getType() == Material.SOUL_FIRE) {
            return;
        }

        // IgnitionListener only checks fireball and lightning ignitions, so this is ok
        // call the event so movecraft combat can add fires to the hitbox
        BlockIgniteEvent blockIgniteEvent = new BlockIgniteEvent(fireBlock, BlockIgniteEvent.IgniteCause.ARROW, projectile);
        Bukkit.getPluginManager().callEvent(blockIgniteEvent);

        if (blockIgniteEvent.isCancelled()) {
            return;
        }

        // If successful, remove the arrow
        BlockData blockData = Material.FIRE.createBlockData();
        boolean placeFire = face.getOppositeFace() == BlockFace.DOWN;
        if (blockData instanceof MultipleFacing multipleFacing && multipleFacing.getAllowedFaces().contains(face.getOppositeFace())) {
            multipleFacing.setFace(face.getOppositeFace(), true);
            placeFire = true;
        }
        if (placeFire) {
            fireBlock.setBlockData(blockData);
        }

        projectile.remove();
        // Since the impact did not actually happen, return
        event.setCancelled(true);
    }

}
