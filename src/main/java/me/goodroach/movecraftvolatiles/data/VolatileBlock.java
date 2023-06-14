package me.goodroach.movecraftvolatiles.data;

public class VolatileBlock {
    private double explosivePower;
    private double explosionProbability;
    private boolean isIncendiary;

    public VolatileBlock(double explosivePower, double explosionProbability, boolean isIncendiary) {
        this.explosivePower = explosivePower;
        this.explosionProbability = explosionProbability;
        this.isIncendiary = isIncendiary;
    }

    public double getExplosivePower() {
        return explosivePower;
    }

    public double getExplosionProbability() {
        return explosionProbability;
    }

    public boolean isIncendiary() {
        return isIncendiary;
    }

}

