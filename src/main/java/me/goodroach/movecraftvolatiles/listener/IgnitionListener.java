package me.goodroach.movecraftvolatiles.listener;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.goodroach.movecraftvolatiles.MovecraftVolatiles;
import me.goodroach.movecraftvolatiles.data.VolatileBlock;
import me.goodroach.movecraftvolatiles.tracking.Volatile;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.combat.MovecraftCombat;
import net.countercraft.movecraft.combat.features.tracking.DamageRecord;
import net.countercraft.movecraft.combat.features.tracking.events.CraftDamagedByEvent;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.util.MathUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class IgnitionListener implements Listener {

    static final Function<Block, Boolean> canBlockBurnFunction = buildCanBlockBurnFunction();

    static @Nullable Method blockRetrievalMethod;
    static @Nullable Object2IntMap<Object> igniteOddsMap;

    static Function<Block, Boolean> buildCanBlockBurnFunction() {
        try {
            final Class<?> magicNumbersClass = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".util.CraftMagicNumbers");
            blockRetrievalMethod = magicNumbersClass.getMethod("getBlock", Material.class);
            Object fireBlockInstance = blockRetrievalMethod.invoke(Material.FIRE);
            igniteOddsMap = getFieldValueSafe(fireBlockInstance, "igniteOdds");

            return (block) -> {
                if (igniteOddsMap != null && blockRetrievalMethod != null) {
                    Object blockObj = blockRetrievalMethod.invoke(block.getType());
                    return igniteOddsMap.getInt(blockObj) > 0;
                } else {
                    return block.getType().isBurnable();
                }
            };
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            return (b) -> {
                return b.getType().isBurnable();
            };
        }
    }

    protected static <T> Optional<T> getFieldValueSafe(@NotNull Object instance, String fieldName) {
        try {
            return Optional.ofNullable(getFieldValue(instance, fieldName));
        } catch(Exception ex) {
            return Optional.empty();
        }
    }
    protected static <T> T getFieldValue(@NotNull Object instance, String fieldName) throws IllegalAccessException, NoSuchFieldException, ClassCastException {
        Field field = FieldUtils.getField(instance.getClass(), fieldName, true);
        T obj = (T)field.get(instance);
        return obj;
    }

    static BlockFace[] CHECK_SIDES = new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    static Block getNextBurnableBlock(final Block block) {
        // A block can burn, when Blocks.Fire#canBurn() returns true
        // => That is the case, when FireBlock#igniteOdds has a value greater than 0 for it
        if (canBlockBurnFunction == null) {
            throw new RuntimeException("Somehow, the function to retrieve whether or not a block can burn is null!");
        }

        for (BlockFace face : CHECK_SIDES) {
            Block testBlock = block.getRelative(face);
            if (testBlock.getBlockData() instanceof Waterlogged waterlogged) {
                if (waterlogged.isWaterlogged()) {
                    continue;
                }
            }

            if (canBlockBurnFunction.apply(testBlock)) {
                return testBlock;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBurnt(BlockBurnEvent event) {
        Block block = event.getBlock();


    }

    // Support volatile tnt too!
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTNTPrimeEvent(TNTPrimeEvent event) {
        if (event.getCause() != TNTPrimeEvent.PrimeCause.FIRE || event.getBlock() == null) {
            return;
        }

        final Block block = event.getBlock();
        final Location blockLocation = block.getLocation();
        VolatileBlock volatileBlock = MovecraftVolatiles.getInstance().getVolatilesManager().getVolatileBlock(block.getType());
        if (volatileBlock == null) {
            return;
        }

        double randomNumber = Math.random();
        if (volatileBlock.getExplosionProbability() < randomNumber) {
            return;
        }

        final boolean explosionSuccessful = block.getWorld().createExplosion(blockLocation, (float) volatileBlock.getExplosivePower(), volatileBlock.isIncendiary());
        if (explosionSuccessful) {
            event.setCancelled(true);

            final Craft craft = fastNearestPlayerCraftToLoc(blockLocation);
            if (craft != null && craft instanceof PlayerCraft) {
                if (event.getPrimingEntity() != null) {
                    damagedCraft((PlayerCraft) craft, event.getPrimingEntity().getUniqueId());
                }
            }
        }
    }

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
        Block testBlock = getNextBurnableBlock(sourceBlock);
        if (testBlock == null) {
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

