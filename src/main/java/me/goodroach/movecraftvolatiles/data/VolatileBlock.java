package me.goodroach.movecraftvolatiles.data;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@SerializableAs("VolatileBlock")
public record VolatileBlock (
    String blockMask,
    double explosivePower,
    double explosionProbability,
    boolean isIncendiary,
    boolean requiresCraft,
    byte eventMask,
    String commandToRun
) implements ConfigurationSerializable  {

    public static enum EReactionType {

        BLOCK_CATCH_FIRE((byte) 1),
        BLOCK_BURNT((byte) 2),
        BLOCK_EXPLOSION_BY_BLOCK((byte) 4),
        BLOCK_HIT_BY_PROJECTILE((byte) 8),
        BLOCK_HIT_BY_BURNING_PROJECTILE((byte) 16),
        BLOCK_EXPLOSION_BY_ENTITY((byte) 32),
        BLOCK_EXPLOSION_BY_VOLATILES((byte) 64);

        private final byte bit;

        EReactionType(byte value) {
            this.bit = value;
        }

        public boolean coveredByMask(byte mask) {
            return (mask & this.bit) > (byte)0;
        }

        public boolean coveredByMask(VolatileBlock volatileBlock) {
            return coveredByMask(volatileBlock.eventMask());
        }

        public byte maskValue() {
            return this.bit;
        }
    }

    public VolatileBlock(String blockMask, double explosivePower, double explosionProbability, boolean isIncendiary, boolean requiresCraft, byte eventMask, String commandToRun) {
        this.blockMask = blockMask;
        this.explosivePower = explosivePower;
        this.explosionProbability = explosionProbability;
        this.isIncendiary = isIncendiary;
        this.requiresCraft = requiresCraft;
        this.eventMask = eventMask;
        this.commandToRun = commandToRun;
    }

    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap();

        data.put("Block", this.blockMask);
        data.put("ExplosionPower", this.explosivePower);
        data.put("ExplosionProbability", this.explosionProbability);
        data.put("IsIncendiary", this.isIncendiary);
        data.put("IsCraftPresenceNecessary", this.requiresCraft);
        data.put("EventMask", this.eventMask);
        data.put("CommandToRun", this.commandToRun);

        return data;
    }

    public static @NotNull VolatileBlock deserialize(@NotNull Map<String, Object> args) {
        Object objectIncendiary = args.getOrDefault("IsIncendiary", null);
        boolean incendiary = false;
        if (objectIncendiary != null && (objectIncendiary instanceof Boolean)) {
            incendiary = ((Boolean) objectIncendiary).booleanValue();
        }

        Object objectRequiresCraft = args.getOrDefault("IsCraftPresenceNecessary", null);
        boolean craftIsNecessary = false;
        if (objectRequiresCraft != null && (objectRequiresCraft instanceof Boolean)) {
            craftIsNecessary = ((Boolean) objectRequiresCraft).booleanValue();
        }

        return new VolatileBlock(
                String.valueOf(args.getOrDefault("Block", "")),
                NumberConversions.toDouble(args.get("ExplosionPower")),
                NumberConversions.toDouble(args.get("ExplosionProbability")),
                incendiary,
                craftIsNecessary,
                NumberConversions.toByte(args.get("EventMask")),
                String.valueOf(args.getOrDefault("CommandToRun", ""))
        );
    }


}

