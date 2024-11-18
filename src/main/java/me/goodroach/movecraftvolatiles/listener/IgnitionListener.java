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
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
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
            igniteOddsMap = getFieldValue(fireBlockInstance, "igniteOdds");

            return (block) -> {
                if (igniteOddsMap != null && blockRetrievalMethod != null) {
                    Object blockObj = null;
                    try {
                        blockObj = blockRetrievalMethod.invoke(block.getType());
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        return block.getType().isBurnable();
                    }
                    return igniteOddsMap.getInt(blockObj) > 0;
                } else {
                    return block.getType().isBurnable();
                }
            };
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException |
                 NoSuchFieldException e) {
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

    protected static boolean handleVolatile(Consumer<Boolean> setEventCancelled, Block affectedBlock, Entity cause, final VolatileBlock.EReactionType eventType) {
        VolatileBlock volatileBlock = MovecraftVolatiles.getInstance().getVolatilesManager().getVolatileBlock(affectedBlock.getType());
        if (volatileBlock == null) {
            return false;
        }

        if (!eventType.coveredByMask(volatileBlock)) {
            return false;
        }

        double randomNumber = Math.random();
        if (volatileBlock.explosionProbability() < randomNumber) {
            return false;
        }

        final Block nextBlock = getNextBurnableBlock(affectedBlock);
        if (nextBlock != null) {
            return false;
        }
        final Craft craft = fastNearestPlayerCraftToLoc(nextBlock.getLocation());
        if (volatileBlock.requiresCraft()) {
            if (craft == null) {
                return false;
            }
            if (!craft.getHitBox().contains(MathUtils.bukkit2MovecraftLoc(nextBlock.getLocation()))) {
                return false;
            }
        }

        final boolean explosionSuccessful = nextBlock.getWorld().createExplosion(nextBlock.getLocation(), (float) volatileBlock.explosivePower(), volatileBlock.isIncendiary());
        if (explosionSuccessful) {
            setEventCancelled.accept(true);

            if (craft != null && craft instanceof PlayerCraft) {
                if (cause != null) {
                    damagedCraft((PlayerCraft) craft, cause.getUniqueId());
                }
            }
            return true;
        }
        return false;
    }

    static void damagedCraft(@NotNull PlayerCraft craft, UUID playerUUID) {
        UUID sender = playerUUID;
        Player cause = MovecraftCombat.getInstance().getServer().getPlayer(sender);

        DamageRecord damageRecord = new DamageRecord(cause, craft.getPilot(), new Volatile());
        Bukkit.getPluginManager().callEvent(new CraftDamagedByEvent(craft, damageRecord));
    }

    static Craft fastNearestPlayerCraftToLoc(Location source) {
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBurnt(BlockBurnEvent event) {
        final Block block = event.getBlock();
        final Block volatileBlock = getNextBurnableBlock(block);
        if (volatileBlock != null)
            this.handleVolatile(event::setCancelled, volatileBlock, null, VolatileBlock.EReactionType.BLOCK_BURNT);
    }

    // Support volatile tnt too!
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTNTPrimeEvent(TNTPrimeEvent event) {
        // This is different, here, the TNT is the burnt block
        if (event.getCause() != TNTPrimeEvent.PrimeCause.FIRE || event.getBlock() == null) {
            return;
        }

        final Block block = event.getBlock();
        this.handleVolatile(event::setCancelled, block, event.getPrimingEntity(), VolatileBlock.EReactionType.BLOCK_EXPLODED);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
        if (testBlock != null)
            handleVolatile(event::setCancelled, testBlock, ignitingPlayer, VolatileBlock.EReactionType.BLOCK_CATCH_FIRE);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitBlock() == null) {
            return;
        }

        Entity shooter = null;
        if (event.getEntity().getShooter() != null && event.getEntity().getShooter() instanceof Entity) {
            shooter = (Entity) event.getEntity().getShooter();
        }

        final VolatileBlock.EReactionType reactionType = event.getEntity().getFireTicks() > 0 ? VolatileBlock.EReactionType.BLOCK_HIT_BY_BURNING_PROJECTILE : VolatileBlock.EReactionType.BLOCK_HIT_BY_PROJECTILE;
        if (handleVolatile(event::setCancelled, event.getHitBlock(), shooter, reactionType)) {
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExploded(BlockExplodeEvent event) {
        event.blockList().removeIf((block) -> {
            return handleVolatile((b) -> {}, block, null, VolatileBlock.EReactionType.BLOCK_EXPLODED);
        });
    }

}

