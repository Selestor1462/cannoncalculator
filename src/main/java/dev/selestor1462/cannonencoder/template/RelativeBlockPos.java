/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 */
package dev.selestor1462.cannonencoder.template;

import net.minecraft.core.BlockPos;

public record RelativeBlockPos(int x, int y, int z) {
    public RelativeBlockPos multiply(int factor) {
        return new RelativeBlockPos(this.x * factor, this.y * factor, this.z * factor);
    }

    public RelativeBlockPos add(RelativeBlockPos other) {
        return new RelativeBlockPos(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public BlockPos addTo(BlockPos origin) {
        return origin.offset(this.x, this.y, this.z);
    }
}

