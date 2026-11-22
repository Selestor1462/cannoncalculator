package dev.selestor1462.cannonencoder.client;

import dev.selestor1462.cannonencoder.state.CannonMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AutoAnchorDetector {

    public record DetectionResult(CannonMode mode, BlockPos pos, String description, BlockPos panelPos) {
        public DetectionResult(CannonMode mode, BlockPos pos, String description) {
            this(mode, pos, description, null);
        }
    }

    public static DetectionResult detectAnyAnchor(Level world, BlockPos playerPos) {
        if (world == null || playerPos == null) return null;

        DetectionResult pigRes = detectPigCannonAnchor(world, playerPos);
        DetectionResult ftlRes = detectFtlAnchor(world, playerPos);

        boolean pigHasPanel = (pigRes != null && pigRes.description() != null && pigRes.description().contains("panel"));
        boolean ftlHasSign = (ftlRes != null && ftlRes.description() != null && ftlRes.description().contains("Sign"));

        if (pigHasPanel && !ftlHasSign) {
            return pigRes;
        }
        if (ftlHasSign && !pigHasPanel) {
            return ftlRes;
        }

        if (ftlRes != null && pigRes != null) {
            double dFtl = playerPos.distSqr(ftlRes.pos());
            double dPig = playerPos.distSqr(pigRes.pos());
            return (dFtl <= dPig) ? ftlRes : pigRes;
        } else if (pigRes != null) {
            return pigRes;
        } else if (ftlRes != null) {
            return ftlRes;
        }
        return null;
    }

    public static DetectionResult detectAnchor(Level world, BlockPos playerPos, CannonMode mode) {
        if (world == null || playerPos == null) return null;

        if (mode == CannonMode.FTL_PEARL_CANNON) {
            return detectFtlAnchor(world, playerPos);
        } else {
            return detectPigCannonAnchor(world, playerPos);
        }
    }

    private static boolean isAttachedToLampPanel(Level world, BlockPos pos, BlockPos panel) {
        if (panel == null || world == null || pos == null) return false;
        for (int dy = 0; dy < 4; dy++) {
            for (int d = 0; d < 9; d++) {
                BlockPos pX = panel.offset(d, dy, 0);
                if (world.hasChunkAt(pX) && world.getBlockState(pX).is(Blocks.REDSTONE_LAMP)) {
                    if (pos.distManhattan(pX) <= 1) return true;
                }
                BlockPos pZ = panel.offset(0, dy, d);
                if (world.hasChunkAt(pZ) && world.getBlockState(pZ).is(Blocks.REDSTONE_LAMP)) {
                    if (pos.distManhattan(pZ) <= 1) return true;
                }
            }
        }
        return false;
    }

    /**
     * For FTL Pearl Cannon: detects the cannon's firing/multishot lever.
     * The anchor MUST be a lever.
     */
    public static DetectionResult detectFtlAnchor(Level world, BlockPos origin) {
        if (world == null || origin == null) return null;

        int radius = 12;
        int minY = Math.max(world.getMinY(), origin.getY() - 6);
        int maxY = Math.min(world.getMaxY(), origin.getY() + 6);

        List<BlockPos> nearbyLevers = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = minY - origin.getY(); dy <= maxY - origin.getY(); dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (p.getY() > 0 && world.hasChunkAt(p) && world.getBlockState(p).is(Blocks.LEVER)) {
                        nearbyLevers.add(p);
                    }
                }
            }
        }

        if (nearbyLevers.isEmpty()) {
            return null;
        }

        // Sort levers by distance to player (closest first)
        nearbyLevers.sort(Comparator.comparingDouble(origin::distSqr));

        // Priority 1: A lever that has a sign nearby with cannon/multishot keywords
        for (BlockPos leverPos : nearbyLevers) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos sp = leverPos.offset(dx, dy, dz);
                        BlockEntity be = world.getBlockEntity(sp);
                        if (be instanceof SignBlockEntity sign) {
                            String text = getSignFullText(sign).toLowerCase(Locale.ROOT);
                            if (text.contains("multishot") || text.contains("ftl") || text.contains("836")
                                || text.contains("pearl") || text.contains("cannon") || text.contains("fire")) {
                                BlockPos panel = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.findFtlLampPanel(leverPos);
                                if (!isAttachedToLampPanel(world, leverPos, panel)) {
                                    return new DetectionResult(
                                        CannonMode.FTL_PEARL_CANNON,
                                        leverPos,
                                        "Sign '" + text.trim() + "' with lever at " + leverPos.toShortString(),
                                        panel
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }

        // Priority 2: A lever with a verified 4x9 FTL Lamp Panel nearby
        for (BlockPos leverPos : nearbyLevers) {
            BlockPos panel = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.findFtlLampPanel(leverPos);
            if (panel != null && panel.getY() > 0) {
                if (!isAttachedToLampPanel(world, leverPos, panel)) {
                    return new DetectionResult(
                        CannonMode.FTL_PEARL_CANNON,
                        leverPos,
                        "Lever with 4x9 panel at " + panel.toShortString(),
                        panel
                    );
                }
            }
        }

        // Priority 3: Fall back to closest lever not directly attached to a lamp panel
        for (BlockPos leverPos : nearbyLevers) {
            BlockPos panel = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.findFtlLampPanel(leverPos);
            if (!isAttachedToLampPanel(world, leverPos, panel)) {
                return new DetectionResult(
                    CannonMode.FTL_PEARL_CANNON,
                    leverPos,
                    "Lever at " + leverPos.toShortString(),
                    panel
                );
            }
        }

        return null;
    }

    private static String getSignFullText(SignBlockEntity sign) {
        if (sign == null) return "";
        try {
            StringBuilder sb = new StringBuilder();
            java.lang.reflect.Method getFront = null;
            try {
                getFront = sign.getClass().getMethod("getFrontText");
            } catch (Throwable ignored) {}

            if (getFront != null) {
                Object front = getFront.invoke(sign);
                extractMessages(front, sb);
                try {
                    java.lang.reflect.Method getBack = sign.getClass().getMethod("getBackText");
                    Object back = getBack.invoke(sign);
                    extractMessages(back, sb);
                } catch (Throwable ignored) {}
            } else {
                for (java.lang.reflect.Method m : sign.getClass().getMethods()) {
                    if (m.getName().equals("getText") && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        if (paramType.isEnum()) {
                            for (Object enumConst : paramType.getEnumConstants()) {
                                Object st = m.invoke(sign, enumConst);
                                extractMessages(st, sb);
                            }
                        }
                    }
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void extractMessages(Object signTextObj, StringBuilder sb) {
        if (signTextObj == null) return;
        try {
            for (java.lang.reflect.Method m : signTextObj.getClass().getMethods()) {
                if (m.getName().equals("getMessages") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
                    Object res = m.invoke(signTextObj, false);
                    if (res instanceof Iterable<?> iter) {
                        for (Object comp : iter) {
                            if (comp instanceof Component c) sb.append(c.getString()).append(" ");
                        }
                    } else if (res instanceof Component[] arr) {
                        for (Component c : arr) {
                            if (c != null) sb.append(c.getString()).append(" ");
                        }
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}
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
                    return new DetectionResult(CannonMode.PIG_CANNON, canonicalSnow, "Aligned with 4x9 selector panel at " + panel.toShortString(), panel);
                }
                if (isSnowLayer(world.getBlockState(canonicalSnow.below()))) {
                    return new DetectionResult(CannonMode.PIG_CANNON, canonicalSnow.below(), "Aligned with 4x9 selector panel at " + panel.toShortString(), panel);
                }
                if (isSnowLayer(world.getBlockState(canonicalSnow.above()))) {
                    return new DetectionResult(CannonMode.PIG_CANNON, canonicalSnow.above(), "Aligned with 4x9 selector panel at " + panel.toShortString(), panel);
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
                    return new DetectionResult(CannonMode.PIG_CANNON, bestNearby, "Aligned with 4x9 selector panel at " + panel.toShortString(), panel);
                }
            }
        }

        // 2. Second attempt: search for a row of snow layers forming the cannon barrel (radius 16)
        int radius = 16;
        int minY = Math.max(world.getMinY(), origin.getY() - 8);
        int maxY = Math.min(world.getMaxY(), origin.getY() + 8);

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

            // Must have at least 2 snow layers in the cluster to represent a cannon barrel
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
                    return new DetectionResult(CannonMode.PIG_CANNON, leftmostSnow, "Found cannon snow layer row at " + leftmostSnow.toShortString());
                }
            }
        }

        return null;
    }
}
