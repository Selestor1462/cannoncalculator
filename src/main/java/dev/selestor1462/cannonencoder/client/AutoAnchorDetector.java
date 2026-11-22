package dev.selestor1462.cannonencoder.client;

import dev.selestor1462.cannonencoder.state.CannonMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class AutoAnchorDetector {

    public record DetectionResult(BlockPos pos, String description, BlockPos panelPos) {
        public DetectionResult(BlockPos pos, String description) {
            this(pos, description, null);
        }
    }

    public static DetectionResult detectAnchor(Level world, BlockPos playerPos, CannonMode mode) {
        if (world == null || playerPos == null) return null;
        return detectPigCannonAnchor(world, playerPos);
    }

    private static boolean isSnowLayer(BlockState state) {
        if (state == null) return false;
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }

    /**
     * For Pig Cannon: locates the cannon's leftmost snow layer.
     * Uses the 4x9 Redstone Lamp panel geometry to locate the canonical snow anchor
     * and verifies snow layers in the area.
     */
    public static DetectionResult detectPigCannonAnchor(Level world, BlockPos origin) {
        if (world == null || origin == null) return null;

        // 1. First attempt: locate the 4x9 Redstone Lamp selector panel within close range (radius 16)
        BlockPos panel = dev.selestor1462.cannonencoder.template.DirectionTemplate.findPigCannonLampPanel(world, origin);
        if (panel != null && panel.getY() > 0 && origin.distSqr(panel) <= 256.0) {
            BlockPos canonicalSnow = dev.selestor1462.cannonencoder.template.DirectionTemplate.resolveFixedAnchor(world, panel);
            if (canonicalSnow != null && canonicalSnow.getY() > 0 && world.hasChunkAt(canonicalSnow)) {
                // MUST verify that canonicalSnow actually has snow layers / snow blocks
                if (isSnowLayer(world.getBlockState(canonicalSnow))) {
                    return new DetectionResult(canonicalSnow, "Aligned with 4x9 selector panel at " + panel.toShortString(), panel);
                }
                if (isSnowLayer(world.getBlockState(canonicalSnow.below()))) {
                    return new DetectionResult(canonicalSnow.below(), "Aligned with 4x9 selector panel at " + panel.toShortString(), panel);
                }
                if (isSnowLayer(world.getBlockState(canonicalSnow.above()))) {
                    return new DetectionResult(canonicalSnow.above(), "Aligned with 4x9 selector panel at " + panel.toShortString(), panel);
                }
                // Check small radius (dx/dz within 3, dy within 2) around canonicalSnow
                BlockPos bestNearby = null;
                double bestNearbyDist = Double.MAX_VALUE;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            BlockPos checkPos = canonicalSnow.offset(dx, dy, dz);
                            if (world.hasChunkAt(checkPos) && isSnowLayer(world.getBlockState(checkPos))) {
                                double d = canonicalSnow.distSqr(checkPos);
                                if (d < bestNearbyDist) {
                                    bestNearbyDist = d;
                                    bestNearby = checkPos;
                                }
                            }
                        }
                    }
                }
                if (bestNearby != null) {
                    return new DetectionResult(bestNearby, "Aligned with 4x9 selector panel at " + panel.toShortString(), panel);
                }
            }
        }

        // 2. Second attempt: search for a row of snow layers forming the cannon barrel (radius 16)
        int radius = 16;
        int minY = Math.max(world.getMinBuildHeight(), origin.getY() - 8);
        int maxY = Math.min(world.getMaxBuildHeight(), origin.getY() + 8);

        List<BlockPos> snowBlocks = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    if (world.hasChunkAt(p)) {
                        BlockState s = world.getBlockState(p);
                        if (isSnowLayer(s)) {
                            snowBlocks.add(p);
                        }
                    }
                }
            }
        }

        if (!snowBlocks.isEmpty()) {
            BlockPos closest = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos p : snowBlocks) {
                double d = origin.distSqr(p);
                if (d < bestDist) {
                    bestDist = d;
                    closest = p;
                }
            }

            List<BlockPos> cluster = new ArrayList<>();
            for (BlockPos p : snowBlocks) {
                if (Math.abs(p.getX() - closest.getX()) <= 4
                    && Math.abs(p.getY() - closest.getY()) <= 2
                    && Math.abs(p.getZ() - closest.getZ()) <= 4) {
                    cluster.add(p);
                }
            }

            // Must have at least 2 snow layers in the cluster to represent a real cannon barrel
            if (cluster.size() >= 2) {
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                int cy = closest.getY();
                for (BlockPos p : cluster) {
                    minX = Math.min(minX, p.getX());
                    maxX = Math.max(maxX, p.getX());
                    minZ = Math.min(minZ, p.getZ());
                    maxZ = Math.max(maxZ, p.getZ());
                }

                BlockPos leftmostSnow;
                if (maxZ - minZ > maxX - minX) {
                    // Row runs along Z axis (e.g. Z = -14 to -11). Leftmost snow layer is maxZ (-11).
                    leftmostSnow = new BlockPos(closest.getX(), cy, maxZ);
                } else if (maxX > minX) {
                    // Row runs along X axis. Leftmost snow layer is maxX.
                    leftmostSnow = new BlockPos(maxX, cy, closest.getZ());
                } else {
                    leftmostSnow = closest;
                }

                if (leftmostSnow != null && leftmostSnow.getY() > 0) {
                    return new DetectionResult(leftmostSnow, "Found cannon snow layer row at " + leftmostSnow.toShortString());
                }
            }
        }

        return null;
    }
}
