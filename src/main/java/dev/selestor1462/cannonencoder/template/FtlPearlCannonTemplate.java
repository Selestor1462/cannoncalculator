package dev.selestor1462.cannonencoder.template;

import dev.selestor1462.cannonencoder.calc.CodingSlot;
import dev.selestor1462.cannonencoder.calc.ftl.FtlCalculationResult;
import dev.selestor1462.cannonencoder.calc.ftl.FtlEncodingData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FtlPearlCannonTemplate {
    public static final String TEMPLATE_ID_PREFIX = "ftl_836_";

    private final String id;
    private final int rotationSteps; // 0, 1, 2, 3 for 0, 90, 180, 270 deg

    public FtlPearlCannonTemplate(String id, int rotationSteps) {
        this.id = id;
        this.rotationSteps = rotationSteps;
    }

    public String id() {
        return this.id;
    }

    public HighlightPlan resolve(BlockPos anchor, FtlCalculationResult calculation, int level, int column) {
        return this.resolve(anchor, calculation, level, column, null);
    }

    public HighlightPlan resolve(BlockPos anchor, FtlCalculationResult calculation, int level, int column, BlockPos knownPanelPos) {
        List<HighlightedBlock> blocks = new ArrayList<>();
        if (anchor == null || anchor.getY() <= 0) {
            return new HighlightPlan(this.id, 0, blocks, level, column);
        }

        int clampedLevel = Math.max(0, Math.min(3, level));
        int clampedCol = Math.max(0, Math.min(8, column));

        BlockPos panel = (knownPanelPos != null && knownPanelPos.getY() > 0) ? knownPanelPos : findFtlLampPanel(anchor);
        BlockPos baseAnchor = resolveBaseAnchor(anchor, panel);
        int cannonBaseRailY = resolveCannonBaseY(anchor, panel);

        BlockPos lampPos = (panel != null)
            ? toWorldPos(panel, clampedCol, panel.getY() + clampedLevel, 0)
            : null;

        // Add Selected Lamp in the world on the Lamp Selector Panel ONLY if panel is known
        if (lampPos != null) {
            blocks.add(new HighlightedBlock(
                lampPos,
                CodingSlot.FTL_ANGLE,
                HighlightType.SELECTED_LAMP,
                null, 0, clampedLevel, clampedCol, 0xFFFFB300
            ));
        }

        if (calculation == null || baseAnchor == null || baseAnchor.getY() <= 0 || cannonBaseRailY <= 0) {
            return new HighlightPlan(this.id, 0, blocks, level, column);
        }

        FtlEncodingData enc = calculation.encodingData();
        boolean[] bitStates = enc.getAllBits();

        int baseRelX = 0;
        int baseRelZ = -49;
        int levelBaseRailY = cannonBaseRailY + clampedLevel * 4;

        for (int bitIndex = 0; bitIndex < FtlEncodingData.TOTAL_BITS; bitIndex++) {
            boolean bitSet = bitStates[bitIndex];
            int bitRelZ = baseRelZ + bitIndex;
            int color = enc.getBitColor(bitIndex);
            CodingSlot slot = getSlotForBit(bitIndex);

            if (FtlEncodingData.isTargetBit(bitIndex)) {
                // Target bit at Z=85 (bit 3) and Z=104 (bit 22)
                if (bitSet) {
                    // 1. Note block on top of target at levelBaseRailY + 1
                    blocks.add(new HighlightedBlock(
                        toWorldPos(baseAnchor, baseRelX + clampedCol, levelBaseRailY + 1, bitRelZ),
                        slot, HighlightType.NOTE_BLOCK, null, bitIndex, clampedLevel, clampedCol, color
                    ));

                    // 2. Observer facing DOWN into note block at levelBaseRailY + 2
                    blocks.add(new HighlightedBlock(
                        toWorldPos(baseAnchor, baseRelX + clampedCol, levelBaseRailY + 2, bitRelZ),
                        slot, HighlightType.OBSERVER, rotateDirection(Direction.DOWN), bitIndex, clampedLevel, clampedCol, color
                    ));

                    // 3. Redstone dust on top at levelBaseRailY + 3
                    blocks.add(new HighlightedBlock(
                        toWorldPos(baseAnchor, baseRelX + clampedCol, levelBaseRailY + 3, bitRelZ),
                        slot, HighlightType.REDSTONE_DUST, null, bitIndex, clampedLevel, clampedCol, color
                    ));

                    // 4. Glass bridge and redstone wire from 0 to clampedCol - 1 connecting into repeater at X=45
                    for (int x = 0; x < clampedCol; x++) {
                        // Glass support at levelBaseRailY + 2
                        blocks.add(new HighlightedBlock(
                            toWorldPos(baseAnchor, baseRelX + x, levelBaseRailY + 2, bitRelZ),
                            slot, HighlightType.GLASS, null, bitIndex, clampedLevel, clampedCol, 0xFFFFFFFF
                        ));
                        // Redstone dust on top of glass at levelBaseRailY + 3
                        blocks.add(new HighlightedBlock(
                            toWorldPos(baseAnchor, baseRelX + x, levelBaseRailY + 3, bitRelZ),
                            slot, HighlightType.REDSTONE_DUST, null, bitIndex, clampedLevel, clampedCol, 0xFFFF2222
                        ));
                    }
                }
            } else {
                BlockPos targetObserverPos = toWorldPos(baseAnchor, baseRelX - 1, levelBaseRailY + 2, bitRelZ);
                int cannonSize = (enc.cannonMaxTnt() > 0) ? enc.cannonMaxTnt() : 6688;
                boolean bitAllowed = isBitPresentInCannon(bitIndex, cannonSize);

                if (bitAllowed) {
                    // Standard bit:
                    // Cannon input observer at relX = baseRelX - 1, levelBaseRailY + 2, facing EAST (towards +X)
                    blocks.add(new HighlightedBlock(
                        targetObserverPos,
                        slot, HighlightType.TARGET_OBSERVER, rotateDirection(Direction.EAST), bitIndex, clampedLevel, clampedCol, color
                    ));

                    if (bitSet) {
                        // 1. Observer at (clampedCol, levelBaseRailY + 1, bitRelZ) facing DOWN into base rail
                        blocks.add(new HighlightedBlock(
                            toWorldPos(baseAnchor, baseRelX + clampedCol, levelBaseRailY + 1, bitRelZ),
                            slot, HighlightType.OBSERVER, rotateDirection(Direction.DOWN), bitIndex, clampedLevel, clampedCol, color
                        ));

                        // 2. Rail on top of observer at (clampedCol, levelBaseRailY + 2, bitRelZ)
                        blocks.add(new HighlightedBlock(
                            toWorldPos(baseAnchor, baseRelX + clampedCol, levelBaseRailY + 2, bitRelZ),
                            slot, HighlightType.RAIL, rotateDirection(Direction.EAST), bitIndex, clampedLevel, clampedCol, color
                        ));

                        // 3. Glass bridge and rail track from 0 to clampedCol - 1 leading to cannon input observer
                        for (int x = 0; x < clampedCol; x++) {
                            // Glass support at levelBaseRailY + 1
                            blocks.add(new HighlightedBlock(
                                toWorldPos(baseAnchor, baseRelX + x, levelBaseRailY + 1, bitRelZ),
                                slot, HighlightType.GLASS, null, bitIndex, clampedLevel, clampedCol, 0xFFFFFFFF
                            ));
                            // Rail on top of glass at levelBaseRailY + 2
                            blocks.add(new HighlightedBlock(
                                toWorldPos(baseAnchor, baseRelX + x, levelBaseRailY + 2, bitRelZ),
                                slot, HighlightType.RAIL, rotateDirection(Direction.EAST), bitIndex, clampedLevel, clampedCol, 0xFF00E5FF
                            ));
                        }
                    }
                }
            }
        }



        // Add Catcher Block if available
        if (calculation.catcherPos() != null) {
            int cx = (int) Math.floor(calculation.catcherPos().x());
            int cy = (int) Math.floor(calculation.catcherPos().y());
            int cz = (int) Math.floor(calculation.catcherPos().z());
            blocks.add(new HighlightedBlock(
                new BlockPos(cx, cy, cz),
                CodingSlot.FTL_CATCHER,
                HighlightType.CATCHER_BLOCK,
                null, 0, 0, 0, 0xFFFFD700
            ));
        }

        // Add Landing Pad (Powder Snow with Gray Carpet on top, moving down until hitting an obstacle)
        if (calculation.endPos() != null) {
            int lx = (int) Math.floor(calculation.endPos().x());
            int lz = (int) Math.floor(calculation.endPos().z());
            int startY = 256;

            Minecraft mc = Minecraft.getInstance();
            int landY = startY;
            if (mc != null && mc.level != null && mc.level.hasChunkAt(new BlockPos(lx, startY, lz))) {
                landY = findObstacleGroundY(mc.level, lx, startY, lz);
            }

            // Powder Snow block
            blocks.add(new HighlightedBlock(
                new BlockPos(lx, landY, lz),
                CodingSlot.FTL_ANGLE,
                HighlightType.LANDING_BLOCK,
                null, 0, 0, 0, 0xFF4DD0E1
            ));

            // Gray Carpet on top of Powder Snow
            blocks.add(new HighlightedBlock(
                new BlockPos(lx, landY + 1, lz),
                CodingSlot.FTL_ANGLE,
                HighlightType.GRAY_CARPET,
                null, 0, 0, 0, 0xFF888888
            ));
        }

        return new HighlightPlan(this.id, 0, blocks, level, column);
    }

    public static int findObstacleGroundY(Level world, int x, int startY, int z) {
        return dev.selestor1462.cannonencoder.client.WorldHighlightRenderer.findObstacleGroundY(world, x, startY, z);
    }

    private static BlockPos cachedFtlAnchor = null;
    private static BlockPos cachedFtlPanel = null;
    private static long lastFtlPanelSearch = 0;

    public static synchronized BlockPos findFtlLampPanel(BlockPos anchor) {
        if (anchor == null) return null;
        long now = System.currentTimeMillis();
        if (cachedFtlPanel != null && anchor.equals(cachedFtlAnchor) && (now - lastFtlPanelSearch < 5000)) {
            return cachedFtlPanel;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return cachedFtlPanel;
        }
        Level world = client.level;
        cachedFtlAnchor = anchor;
        lastFtlPanelSearch = now;

        // Check if anchor itself is part of the lamp panel
        if (world.hasChunkAt(anchor) && world.getBlockState(anchor).is(Blocks.REDSTONE_LAMP)) {
            BlockPos bl = traceLampPanelBottomLeft(world, anchor);
            if (bl != null) {
                cachedFtlPanel = bl;
                return bl;
            }
        }

        // Fast search in immediate vicinity only (radius 16, within +-6 Y)
        int originY = anchor.getY();
        for (int dy = -6; dy <= 6; dy++) {
            int y = originY + dy;
            if (y < world.getMinY() || y >= world.getMaxY()) continue;
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos p = new BlockPos(anchor.getX() + dx, y, anchor.getZ() + dz);
                    if (world.hasChunkAt(p) && world.getBlockState(p).is(Blocks.REDSTONE_LAMP)) {
                        BlockPos bl = traceLampPanelBottomLeft(world, p);
                        if (bl != null) {
                            cachedFtlPanel = bl;
                            return bl;
                        }
                    }
                }
            }
        }

        if (cachedFtlPanel != null) {
            return cachedFtlPanel;
        }
        return null;
    }

    private static BlockPos traceLampPanelBottomLeft(Level world, BlockPos p) {
        BlockPos bottom = p;
        while (world.getBlockState(bottom.below()).is(Blocks.REDSTONE_LAMP)) {
            bottom = bottom.below();
        }
        while (world.getBlockState(bottom.west()).is(Blocks.REDSTONE_LAMP)) {
            bottom = bottom.west();
        }
        while (world.getBlockState(bottom.north()).is(Blocks.REDSTONE_LAMP)) {
            bottom = bottom.north();
        }
        int widthX = 0;
        while (world.getBlockState(bottom.offset(widthX, 0, 0)).is(Blocks.REDSTONE_LAMP)) {
            widthX++;
        }
        int widthZ = 0;
        while (world.getBlockState(bottom.offset(0, 0, widthZ)).is(Blocks.REDSTONE_LAMP)) {
            widthZ++;
        }
        int height = 0;
        while (world.getBlockState(bottom.offset(0, height, 0)).is(Blocks.REDSTONE_LAMP)) {
            height++;
        }
        int width = Math.max(widthX, widthZ);
        // The selector matrix is 9 columns wide and 4 rows high
        if (width >= 4 && height >= 3) {
            return bottom;
        }
        return null;
    }

    public static boolean isOptionalBit(int bitIndex) {
        // Red big bits (8: value 4, 9: value 2) and Light Blue big bits (27: value 4, 28: value 2)
        return bitIndex == 8 || bitIndex == 9 || bitIndex == 27 || bitIndex == 28;
    }

    public static boolean isBitPresentInCannon(int bitIndex, int cannonSize) {
        if (cannonSize <= 1672) {
            // 1/4 cannon (1672): omit 2nd and 3rd big bits (values 2 and 4)
            if (bitIndex == 8 || bitIndex == 9 || bitIndex == 27 || bitIndex == 28) {
                return false;
            }
        } else if (cannonSize <= 3344) {
            // Half cannon (3344): omit 3rd big bit (value 4)
            if (bitIndex == 8 || bitIndex == 27) {
                return false;
            }
        }
        return true;
    }

    public static BlockPos resolveBaseAnchor(BlockPos anchor) {
        return resolveBaseAnchor(anchor, null);
    }

    public static BlockPos resolveBaseAnchor(BlockPos anchor, BlockPos knownPanelPos) {
        if (knownPanelPos != null && knownPanelPos.getY() > 0) return knownPanelPos;
        BlockPos panel = findFtlLampPanel(anchor);
        if (panel != null && panel.getY() > 0) return panel;
        if (anchor != null && anchor.getY() > 0) return anchor;
        Minecraft client = Minecraft.getInstance();
        BlockPos playerPos = (client != null && client.player != null) ? client.player.blockPosition() : null;
        if (playerPos != null && playerPos.getY() > 0) return playerPos;
        return null;
    }

    public static int resolveCannonBaseY(BlockPos anchor) {
        return resolveCannonBaseY(anchor, null);
    }

    public static int resolveCannonBaseY(BlockPos anchor, BlockPos knownPanelPos) {
        BlockPos panel = (knownPanelPos != null && knownPanelPos.getY() > 0) ? knownPanelPos : findFtlLampPanel(anchor);
        if (panel != null) {
            return panel.getY() - 24;
        }
        Minecraft client = Minecraft.getInstance();
        BlockPos playerPos = (client != null && client.player != null) ? client.player.blockPosition() : null;
        BlockPos baseAnchor = (anchor != null) ? anchor : ((playerPos != null) ? playerPos : new BlockPos(0, 128, 0));
        int refY = (baseAnchor.getY() < 64 && playerPos != null && playerPos.getY() >= 64) ? playerPos.getY() : baseAnchor.getY();
        return (refY >= 100) ? (refY - 24) : 104;
    }

    private BlockPos toWorldPos(BlockPos anchor, int relX, int targetY, int relZ) {
        return new BlockPos(anchor.getX() + relX, targetY, anchor.getZ() + relZ);
    }

    public static CodingSlot getSlotForBit(int bitIndex) {
        if (bitIndex >= 0 && bitIndex <= 1) return CodingSlot.FTL_ANGLE;
        if (bitIndex >= 2 && bitIndex <= 3) return CodingSlot.FTL_VARIABLE;
        if (bitIndex >= 4 && bitIndex <= 7) return CodingSlot.FTL_RED_SMALL;
        if (bitIndex >= 8 && bitIndex <= 10) return CodingSlot.FTL_RED_BIG;
        if (bitIndex >= 11 && bitIndex <= 16) return CodingSlot.FTL_RED_MEDIUM;
        if (bitIndex >= 17 && bitIndex <= 20) return CodingSlot.FTL_DIRECTION;
        if (bitIndex >= 21 && bitIndex <= 26) return CodingSlot.FTL_BLUE_MEDIUM;
        if (bitIndex >= 27 && bitIndex <= 29) return CodingSlot.FTL_BLUE_BIG;
        if (bitIndex >= 30 && bitIndex <= 33) return CodingSlot.FTL_BLUE_SMALL;
        if (bitIndex >= 34 && bitIndex <= 35) return CodingSlot.FTL_UPACCEL_BIG;
        if (bitIndex >= 36 && bitIndex <= 38) return CodingSlot.FTL_UPACCEL_SMALL;
        return CodingSlot.FTL_ANGLE;
    }

    public static int getRotationSteps(String templateId) {
        return 0;
    }

    public static BlockPos rotateLocal(BlockPos anchor, int localX, int localY, int localZ, int rotationSteps) {
        return new BlockPos(anchor.getX() + localX, localY, anchor.getZ() + localZ);
    }

    private Direction rotateDirection(Direction direction) {
        return direction;
    }

    public static BlockPos getPearlChamberPos(BlockPos anchor, String templateId) {
        if (anchor == null) return new BlockPos(0, 0, 0);
        return anchor;
    }
}
