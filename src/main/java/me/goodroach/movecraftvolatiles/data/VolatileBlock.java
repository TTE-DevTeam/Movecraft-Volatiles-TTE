package me.goodroach.movecraftvolatiles.data;

public record VolatileBlock(
    double explosivePower,
    double explosionProbability,
    boolean isIncendiary,
    boolean requiresCraft,
    byte eventMask
) {

    public static enum EReactionType {

        BLOCK_CATCH_FIRE((byte) 1),
        BLOCK_BURNT((byte) 2),
        BLOCK_EXPLODED((byte) 4),
        BLOCK_HIT_BY_PROJECTILE((byte) 8),
        BLOCK_HIT_BY_BURNING_PROJECTILE((byte) 16);

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

    public VolatileBlock(double explosivePower, double explosionProbability, boolean isIncendiary, boolean requiresCraft, byte eventMask) {
        this.explosivePower = explosivePower;
        this.explosionProbability = explosionProbability;
        this.isIncendiary = isIncendiary;
        this.requiresCraft = requiresCraft;
        this.eventMask = eventMask;
    }

}

