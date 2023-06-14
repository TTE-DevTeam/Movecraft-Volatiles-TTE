package me.goodroach.movecraftvolatiles.config;

import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.craft.type.property.MaterialSetProperty;
import org.bukkit.NamespacedKey;

public class Keys {
    /*Note this is only for craft files. You can have these settings in your craft files to modify them
    individually.*/
    public static final NamespacedKey NOT_VOLATILE_BLOCKS_PER_CRAFT = build("not_volatile_blocks_per_craft");

    public static void register() {
        CraftType.registerProperty(new MaterialSetProperty("NotVolatileBlocks", NOT_VOLATILE_BLOCKS_PER_CRAFT, craftType -> null));
    }

    private static NamespacedKey build (String key) {return new NamespacedKey("movecraft-hitpoints", key);}
}
