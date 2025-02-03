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
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.events.CraftCollisionExplosionEvent;
import net.countercraft.movecraft.util.MathUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public class IgnitionListener implements Listener {

    static final Function<Block, Boolean> canBlockBurnFunction = buildCanBlockBurnFunction();

    static @Nullable Method blockRetrievalMethod;
    static @Nullable Object2IntMap<Object> igniteOddsMap;

    static final String VOLATILE_EXPLOSION_METADATA_FLAG = "Movecraft-Volatiles-TTE-ExplosionTag";

    static Function<Block, Boolean> buildCanBlockBurnFunction() {
        try {
            final Class<?> magicNumbersClass = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".util.CraftMagicNumbers");
            blockRetrievalMethod = magicNumbersClass.getMethod("getBlock", Material.class);
            Object fireBlockInstance = blockRetrievalMethod.invoke(null, Material.FIRE);
            igniteOddsMap = getFieldValue(fireBlockInstance, "igniteOdds");

            return (block) -> {
                if (igniteOddsMap != null && blockRetrievalMethod != null) {
                    try {
                        Object blockObj = blockRetrievalMethod.invoke(null, block.getType());
                        return igniteOddsMap.getInt(blockObj) > 0;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        return block.getType().isBurnable();
                    }
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

    static BlockFace[] CHECK_SIDES = new BlockFace[] {BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.UP, BlockFace.DOWN};

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
        VolatileBlock volatileBlock = MovecraftVolatiles.getInstance().getVolatilesManager().getVolatileBlock(eventType, affectedBlock.getType());
        if (volatileBlock == null) {
            return false;
        }

        if (!eventType.coveredByMask(volatileBlock)) {
        }

        double randomNumber = Math.random();
        if (volatileBlock.explosionProbability() < randomNumber) {
            return false;
        }

        if (affectedBlock == null) {
            if (eventType != VolatileBlock.EReactionType.BLOCK_CATCH_FIRE) {
                affectedBlock = affectedBlock;
            } else {
                return false;
            }
        }
        final Craft craft = fastNearestPlayerCraftToLoc(affectedBlock.getLocation());

        if (volatileBlock.requiresCraft()) {
            if (craft == null) {
                return false;
            }
            if (!craft.getHitBox().contains(MathUtils.bukkit2MovecraftLoc(affectedBlock.getLocation()))) {
                return false;
            }
        }

        if (craft != null && !volatileBlock.craftTypeList().isEmpty()) {
            boolean inList = volatileBlock.craftTypeList().contains(craft.getType().getStringProperty(CraftType.NAME).toUpperCase());
            if (volatileBlock.listIsBlackList() && inList) {
                return false;
            }
            if (!volatileBlock.listIsBlackList() && !inList) {
                return false;
            }
        }

        // Remove block, then check for explosion, if necessary, revert that. Revert happens later
        final BlockData blockData = affectedBlock.getBlockData().clone();
        affectedBlock.breakNaturally(false);

        final boolean explosionSuccessful = createExplosion(affectedBlock.getWorld(), affectedBlock.getLocation().add(0.5, 0.5, 0.5), volatileBlock);
        if (explosionSuccessful) {
            setEventCancelled.accept(true);

            if (volatileBlock.commandToRun() != null && !volatileBlock.commandToRun().isBlank()) {
                String command = getCommand(affectedBlock, cause, volatileBlock);
                MovecraftVolatiles.getInstance().getLogger().log(Level.INFO, "Command to run: " + command);
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
            }

            if (craft != null && craft instanceof PlayerCraft) {
                if (cause != null) {
                    damagedCraft((PlayerCraft) craft, cause.getUniqueId());
                }
            }

            return true;
        } else {
            affectedBlock.setBlockData(blockData);
        }
        return false;
    }

    protected static boolean createExplosion(@NotNull World world, Location location, VolatileBlock volatileBlock) {
        TNTPrimed tntPrimed = world.spawn(location, TNTPrimed.class);
        if (tntPrimed == null) {
            return false;
        }

        tntPrimed.setInvisible(true);
        if (volatileBlock.isIncendiary() && (volatileBlock.incendiaryProbability() >= 1.0 || volatileBlock.incendiaryProbability() < Math.random())) {
            tntPrimed.setIsIncendiary(true);
        }
        tntPrimed.setYield((float)volatileBlock.explosivePower());
        tntPrimed.setNoPhysics(false);
        tntPrimed.setSilent(true);
        tntPrimed.setFuseTicks(0);
        tntPrimed.setMetadata(VOLATILE_EXPLOSION_METADATA_FLAG, new FixedMetadataValue(MovecraftVolatiles.getInstance(), true));
//
//        affectedBlock.getWorld().createExplosion(
//                affectedBlock.getLocation().add(0.5, 0.5, 0.5),
//                (float) volatileBlock.explosivePower(),
//                volatileBlock.isIncendiary()
//        );
        return true;
    }

    private static @NotNull String getCommand(Block affectedBlock, Entity cause, VolatileBlock volatileBlock) {
        String command = volatileBlock.commandToRun();

        command = command.replaceAll("%POS_WORLD%", affectedBlock.getWorld().getName());
        command = command.replaceAll("%POS_X%", "" + (affectedBlock.getLocation().getBlockX() + 0.5D));
        command = command.replaceAll("%POS_Y%", "" + (affectedBlock.getLocation().getBlockY() + 0.5D));
        command = command.replaceAll("%POS_Z%", "" + (affectedBlock.getLocation().getBlockZ() + 0.5D));
        if (cause != null) {
            command = command.replaceAll("%CAUSER_UUID", cause.getUniqueId().toString());
        }
        return command;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurnt(BlockBurnEvent event) {
        // The relevant block is the one that was destroyed by fire
        final Block block = event.getBlock();
        if (block != null)
            this.handleVolatile(event::setCancelled, block, null, VolatileBlock.EReactionType.BLOCK_BURNT);
    }

    // Support volatile tnt too!
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTNTPrimeEvent(TNTPrimeEvent event) {
        // This is different, here, the TNT is the burnt block
        if (event.getBlock() == null) {
            return;
        }

        VolatileBlock.EReactionType reactionType = VolatileBlock.EReactionType.BLOCK_EXPLOSION_BY_BLOCK;
        Consumer<Boolean> setCancelledFunction = event::setCancelled;
        if (event.getBlock().getType() == Material.TNT) {
            setCancelledFunction = (cancelled) -> {
              event.setCancelled(cancelled);
              if (cancelled) {
                  event.getBlock().breakNaturally(false);
              }
            };

            switch(event.getCause()) {
                case FIRE:
                    reactionType = VolatileBlock.EReactionType.BLOCK_CATCH_FIRE;
                    break;
                case PROJECTILE:
                    reactionType = VolatileBlock.EReactionType.BLOCK_HIT_BY_BURNING_PROJECTILE;
                    break;
                case BLOCK_BREAK:
                case EXPLOSION:
                    reactionType = VolatileBlock.EReactionType.BLOCK_EXPLOSION_BY_BLOCK;
                    break;
                default:
                    return;
            }
        }

        final Block block = event.getBlock();
        this.handleVolatile(setCancelledFunction, block, event.getPrimingEntity(), VolatileBlock.EReactionType.BLOCK_EXPLOSION_BY_BLOCK);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!(
                event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL ||
                event.getCause() == BlockIgniteEvent.IgniteCause.LIGHTNING
        ))
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitBlock() == null || event.getEntity().isDead()) {
            return;
        }

        Entity shooter = null;
        if (event.getEntity().getShooter() != null && event.getEntity().getShooter() instanceof Entity) {
            shooter = (Entity) event.getEntity().getShooter();
        }

        final boolean flamingProjectile = event.getEntity().getFireTicks() > 0;
        final boolean projectileIsArrow = event.getEntity() instanceof AbstractArrow;

        VolatileBlock.EReactionType reactionType;

        if (projectileIsArrow) {
            reactionType = flamingProjectile ? VolatileBlock.EReactionType.BLOCK_HIT_BY_FLAMING_ARROW : VolatileBlock.EReactionType.BLOCK_HIT_BY_ARROW;
        } else {
            reactionType = flamingProjectile ? VolatileBlock.EReactionType.BLOCK_HIT_BY_BURNING_PROJECTILE : VolatileBlock.EReactionType.BLOCK_HIT_BY_PROJECTILE;
        }

        if (handleVolatile(event::setCancelled, event.getHitBlock(), shooter, reactionType)) {
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExploded(BlockExplodeEvent event) {
        Location location = new Location(event.getBlock().getLocation().getWorld(), event.getBlock().getLocation().getBlockX(), event.getBlock().getLocation().getBlockY(), event.getBlock().getLocation().getBlockZ());
        if (craftCollisionExplosions.remove(location)) {
            return;
        }
        event.blockList().removeIf((block) -> {
            return handleVolatile((b) -> {}, block, null, VolatileBlock.EReactionType.BLOCK_EXPLOSION_BY_BLOCK);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExploded(EntityExplodeEvent event) {
        VolatileBlock.EReactionType eventType = VolatileBlock.EReactionType.BLOCK_EXPLOSION_BY_ENTITY;
        if (event.getEntity() != null && event.getEntity() instanceof TNTPrimed && event.getEntity().hasMetadata(VOLATILE_EXPLOSION_METADATA_FLAG)) {
            eventType = VolatileBlock.EReactionType.BLOCK_EXPLOSION_BY_VOLATILES;
        }
        final VolatileBlock.EReactionType finalEventType = eventType;
        event.blockList().removeIf((block) -> {
            return handleVolatile((b) -> {}, block, null, finalEventType);
        });
    }

    static Set<Location> craftCollisionExplosions = Collections.synchronizedSet(new HashSet<>());

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftCollisionExplosion(CraftCollisionExplosionEvent event) {
        // we need to handle craft explosions separately, otherwise they will cause chained volatile reactions
        final Location location = new Location(event.getLocation().getWorld(), event.getLocation().getBlockX(), event.getLocation().getBlockY(), event.getLocation().getBlockZ());
        craftCollisionExplosions.add(location);
        Bukkit.getScheduler().runTaskLaterAsynchronously(MovecraftVolatiles.getInstance(), () -> {
           craftCollisionExplosions.remove(location);
        }, 200L);
    }

}

