/*
 * Decompiled with CFR 0.152.
 */
package dev.selestor1462.cannonencoder.calc;

import dev.selestor1462.cannonencoder.calc.CodingSlot;

public record CalculationResult(int initX, int initZ, int destX, int destZ, int dX, int dZ, int landingX, int landingZ, double xMotion, double zMotion, int straightAccelFirst, int straightAccelSecond, int diagonalAccelThird, int diagonalAccelFourth, int directionCode) {
    public int slotValue(CodingSlot slot) {
        return switch (slot) {
            default -> throw new MatchException(null, null);
            case CodingSlot.DIRECTION -> this.directionCode;
            case CodingSlot.FIRST_ACCEL -> this.straightAccelFirst;
            case CodingSlot.SECOND_ACCEL -> this.straightAccelSecond;
            case CodingSlot.THIRD_ACCEL -> this.diagonalAccelThird;
            case CodingSlot.FOURTH_ACCEL -> this.diagonalAccelFourth;
        };
    }
}

