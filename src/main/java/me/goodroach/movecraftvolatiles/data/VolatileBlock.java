package me.goodroach.movecraftvolatiles.data;

import me.goodroach.movecraftvolatiles.MovecraftVolatiles;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SerializableAs("VolatileBlock")
public record VolatileBlock (
    String blockMask,
    double explosivePower,
    double explosionProbability,
    boolean isIncendiary,
    double incendiaryProbability,
    boolean requiresCraft,
    int eventMask,
    String commandToRun,
    List<String> craftTypeList,
    boolean listIsBlackList
) implements ConfigurationSerializable  {

    public static enum EReactionType {

        BLOCK_CATCH_FIRE((short) 1),
        BLOCK_BURNT((short) 2),
        BLOCK_EXPLOSION_BY_BLOCK((short) 4),
        BLOCK_HIT_BY_PROJECTILE((short) 8),
        BLOCK_HIT_BY_BURNING_PROJECTILE((short) 16),
        BLOCK_EXPLOSION_BY_ENTITY((short) 32),
        BLOCK_EXPLOSION_BY_VOLATILES((short) 64),
        BLOCK_HIT_BY_ARROW((short) 128),
        BLOCK_HIT_BY_FLAMING_ARROW((short) 256);

        private final short bit;

        EReactionType(short value) {
            this.bit = value;
        }

        public boolean coveredByMask(short mask) {
            return (mask & this.bit) > (short)0;
        }

        public boolean coveredByMask(VolatileBlock volatileBlock) {
            return coveredByMask((short) volatileBlock.eventMask());
        }

        public short maskValue() {
            return this.bit;
        }
    }

    public VolatileBlock(String blockMask, double explosivePower, double explosionProbability, boolean isIncendiary, double incendiaryProbability, boolean requiresCraft, int eventMask, String commandToRun, List<String> craftTypeList, boolean listIsBlackList) {
        this.blockMask = blockMask;
        this.explosivePower = explosivePower;
        this.explosionProbability = explosionProbability;
        this.isIncendiary = isIncendiary;
        this.incendiaryProbability = incendiaryProbability;
        this.requiresCraft = requiresCraft;
        this.eventMask = eventMask;
        this.commandToRun = commandToRun;
        this.craftTypeList = craftTypeList;
        this.listIsBlackList = listIsBlackList;
    }

    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap();

        data.put("Block", this.blockMask);
        data.put("ExplosionPower", this.explosivePower);
        data.put("ExplosionProbability", this.explosionProbability);
        data.put("IsIncendiary", this.isIncendiary);
        data.put("IncendiaryProbability", this.incendiaryProbability);
        data.put("IsCraftPresenceNecessary", this.requiresCraft);
        data.put("EventMask", this.eventMask);
        data.put("CommandToRun", this.commandToRun);
        data.put("CraftTypeList", this.craftTypeList);
        data.put("CraftTypeListIsBlacklist", this.listIsBlackList);

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

        Object objectListIsBlacklist = args.getOrDefault("CraftTypeListIsBlacklist", null);
        boolean listIsBlacklist = false;
        if (objectListIsBlacklist != null && (objectListIsBlacklist instanceof Boolean)) {
            listIsBlacklist = ((Boolean) objectListIsBlacklist).booleanValue();
        }

        Object objectTypeList = args.getOrDefault("CraftTypeList", List.of());
        List<String> typeList;
        try {
            typeList = (List<String>) objectTypeList;
            if (!typeList.isEmpty()) {
                List<String> tmpList = new ArrayList<>(typeList.size());
                typeList.clear();
                final List<String> typeListTmp = typeList;
                tmpList.forEach(s -> {
                    typeListTmp.add(s.toUpperCase());
                });
            }
        } catch(ClassCastException cce) {
            MovecraftVolatiles.getInstance().getLogger().warning("Configured crafttypelist is not a string list!");
            typeList = List.of();
        }

        return new VolatileBlock(
                String.valueOf(args.getOrDefault("Block", "")),
                NumberConversions.toDouble(args.get("ExplosionPower")),
                NumberConversions.toDouble(args.get("ExplosionProbability")),
                incendiary,
                NumberConversions.toDouble(args.getOrDefault("IncendiaryProbability", 1)),
                craftIsNecessary,
                NumberConversions.toShort(args.get("EventMask")),
                String.valueOf(args.getOrDefault("CommandToRun", "")),
                typeList,
                listIsBlacklist
        );
    }


}

