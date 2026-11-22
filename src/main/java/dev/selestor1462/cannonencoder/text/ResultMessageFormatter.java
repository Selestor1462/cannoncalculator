/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 */
package dev.selestor1462.cannonencoder.text;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class ResultMessageFormatter {
    private static final String PREFIX = "[Cannon] ";

    private ResultMessageFormatter() {
    }

    public static List<Component> format(CalculationResult result) {
        int totalTicks = result.straightAccelFirst() + result.straightAccelSecond() + result.diagonalAccelThird() + result.diagonalAccelFourth() + result.directionCode();
        double minutes = totalTicks / 1200.0;
        return List.of(
            line("X Landing Spot: " + result.landingX() + "     Z Landing Spot: " + result.landingZ()),
            line("Time: " + totalTicks + " ticks (~" + String.format(java.util.Locale.ROOT, "%.1fm", minutes) + ")"),
            line("X Motion: " + result.xMotion() + "     Z Motion: " + result.zMotion()),
            line("First Accel: " + result.straightAccelFirst() + "     Second Accel: " + result.straightAccelSecond()),
            line("Third Accel: " + result.diagonalAccelThird() + "     Fourth Accel: " + result.diagonalAccelFourth()),
            line("Direction: " + result.directionCode())
        );
    }

    private static Component line(String text) {
        return Component.literal((String)(PREFIX + text));
    }
}

