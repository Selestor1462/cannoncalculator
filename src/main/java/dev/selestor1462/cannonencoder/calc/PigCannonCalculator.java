/*
 * Decompiled with CFR 0.152.
 */
package dev.selestor1462.cannonencoder.calc;

import dev.selestor1462.cannonencoder.calc.CalculationResult;

public final class PigCannonCalculator {
    public static final double BLOCKS_PER_BPT = 11.11111111111111;

    public CalculationResult calculate(int initX, int initZ, int destX, int destZ) {
        int dX = roundToFive((double) (destX - initX));
        int dZ = roundToFive((double) (destZ - initZ));
        int landingX = initX + dX;
        int landingZ = initZ + dZ;
        double xMotion = (double) dX / BLOCKS_PER_BPT;
        double zMotion = (double) dZ / BLOCKS_PER_BPT;
        boolean cancelZ = Math.abs(dX) > Math.abs(dZ);
        int[] straightAccel = distanceToAccelTicks(cancelZ ? Math.abs(dX) - Math.abs(dZ) : Math.abs(dZ) - Math.abs(dX));
        int[] diagonalAccel = distanceToAccelTicks(cancelZ ? Math.abs(dZ) : Math.abs(dX));
        return new CalculationResult(initX, initZ, destX, destZ, dX, dZ, landingX, landingZ, xMotion, zMotion, straightAccel[0], straightAccel[1], diagonalAccel[0], diagonalAccel[1], directionCode(dX, dZ));
    }

    public static int roundToFive(double value) {
        if (value > 0.0) {
            return (int) (value + 2.5) / 5 * 5;
        }
        return (int) (value - 2.5) / 5 * 5;
    }

    static int[] distanceToAccelTicks(int distance) {
        int bigAccelTicks = distance / 250;
        int smallAccelTicks = (distance - bigAccelTicks * 250) / 5;
        return new int[]{bigAccelTicks, smallAccelTicks};
    }

    static int directionCode(int dX, int dZ) {
        int direction;
        boolean cancelZ = Math.abs(dX) > Math.abs(dZ);
        int n = direction = cancelZ ? 1 : 0;
        if (dX > 0 && dZ > 0 || dX < 0 && dZ < 0) {
            direction += 2;
        }
        if (dZ < 0) {
            direction += 4;
        }
        return direction;
    }
}

