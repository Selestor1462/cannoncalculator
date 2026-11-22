package dev.selestor1462.cannonencoder.template;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.CodingSlot;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;

public record DirectionTemplate(int directionCode, Map<CodingSlot, SlotPattern> slotPatterns, int rotSteps) {
    private static final Set<CodingSlot> REQUIRED_SLOTS = Set.of(
        CodingSlot.DIRECTION,
        CodingSlot.FOURTH_ACCEL,
        CodingSlot.THIRD_ACCEL,
        CodingSlot.SECOND_ACCEL,
        CodingSlot.FIRST_ACCEL
    );

    public DirectionTemplate(int directionCode, Map<CodingSlot, SlotPattern> slotPatterns) {
        this(directionCode, slotPatterns, 0);
    }

    public DirectionTemplate {
        slotPatterns = Map.copyOf(slotPatterns);
    }

    public boolean isConfigured() {
        return this.slotPatterns.keySet().containsAll(REQUIRED_SLOTS);
    }

    public Optional<HighlightPlan> resolve(String templateId, BlockPos anchor, CalculationResult result) {
        return this.resolve(templateId, anchor, result, 0, 0);
    }

    public static BlockPos resolveFixedAnchor(BlockPos anchor) {
        return resolveFixedAnchor(null, anchor, null);
    }

    public static BlockPos resolveFixedAnchor(Level world, BlockPos anchor) {
        return resolveFixedAnchor(world, anchor, null);
    }

    public static BlockPos resolveFixedAnchor(Level world, BlockPos anchor, BlockPos knownPanelPos) {
        if (anchor == null) return BlockPos.ZERO;
        BlockPos panelBottomLeft = knownPanelPos != null ? knownPanelPos : findPigCannonLampPanel(world, anchor);
        if (panelBottomLeft != null) {
            Level lvl = world;
            if (lvl == null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) lvl = mc.level;
            }
            int widthX = 0;
            if (lvl != null && lvl.hasChunkAt(panelBottomLeft)) {
                while (lvl.getBlockState(panelBottomLeft.offset(widthX, 0, 0)).is(Blocks.REDSTONE_LAMP)) {
                    widthX++;
                }
            }
            if (widthX >= 7 || (widthX == 0 && panelBottomLeft.getX() > anchor.getX())) {
                return new BlockPos(
                    panelBottomLeft.getX() - 6,
                    panelBottomLeft.getY(),
                    panelBottomLeft.getZ() + 6
                );
            } else {
                return new BlockPos(
                    panelBottomLeft.getX() + 6,
                    panelBottomLeft.getY(),
                    panelBottomLeft.getZ() - 6
                );
            }
        }
        return new BlockPos(anchor.getX(), anchor.getY(), anchor.getZ());
    }

    public Optional<HighlightPlan> resolve(String templateId, BlockPos anchor, CalculationResult result, int level, int column) {
        return this.resolve(templateId, anchor, result, level, column, null);
    }

    public Optional<HighlightPlan> resolve(String templateId, BlockPos anchor, CalculationResult result, int level, int column, BlockPos knownPanelPos) {
        if (!this.isConfigured() || anchor == null || anchor.getY() <= 0) {
            return Optional.empty();
        }

        int clampedLevel = Math.max(0, Math.min(3, level));
        int clampedCol = Math.max(0, Math.min(8, column));

        // 1. Locate the exact 4x9 Redstone Lamp panel in the control room
        BlockPos panelBottomLeft = knownPanelPos != null ? knownPanelPos : findPigCannonLampPanel(anchor);
        BlockPos fixedAnchor = resolveFixedAnchor(null, anchor, panelBottomLeft);
        if (fixedAnchor == null || fixedAnchor.getY() <= 0) {
            return Optional.empty();
        }
        BlockPos lampPos = null;

        if (panelBottomLeft != null) {
            Minecraft mc = Minecraft.getInstance();
            int widthX = 0;
            if (mc != null && mc.level != null && mc.level.hasChunkAt(panelBottomLeft)) {
                while (mc.level.getBlockState(panelBottomLeft.offset(widthX, 0, 0)).is(Blocks.REDSTONE_LAMP)) {
                    widthX++;
                }
            }
            boolean runsAlongX = (widthX >= 7 || (widthX == 0 && panelBottomLeft.getX() > fixedAnchor.getX()));
            if (runsAlongX) {
                lampPos = new BlockPos(
                    panelBottomLeft.getX() + clampedCol,
                    panelBottomLeft.getY() + clampedLevel,
                    panelBottomLeft.getZ()
                );
            } else {
                lampPos = new BlockPos(
                    panelBottomLeft.getX(),
                    panelBottomLeft.getY() + clampedLevel,
                    panelBottomLeft.getZ() + clampedCol
                );
            }
        }

        ArrayList<HighlightedBlock> blocks = new ArrayList<>();
        if (result != null) {
            for (CodingSlot slot : REQUIRED_SLOTS) {
                SlotPattern pattern = this.slotPatterns.get(slot);
                if (pattern != null) {
                    blocks.addAll(pattern.resolve(fixedAnchor, result, level, column));
                }
            }
        }

        // Add Selected Lamp in the world on the Lamp Selector Panel ONLY if panel is known
        if (lampPos != null) {
            blocks.add(new HighlightedBlock(
                lampPos,
                CodingSlot.DIRECTION,
                HighlightType.SELECTED_LAMP,
                null, 0, clampedLevel, clampedCol, 0xFFFFB300
            ));
        }

        // Add Landing Block for Pig Cannon at destination (Twisting Vines destination indicator)
        if (result != null) {
            int lx = result.landingX();
            int lz = result.landingZ();
            Minecraft mc = Minecraft.getInstance();
            int startY = (mc != null && mc.level != null) ? mc.level.getMaxY() - 2 : 256;
            int landY = startY;
            if (mc != null && mc.level != null && mc.level.hasChunkAt(new BlockPos(lx, startY, lz))) {
                landY = dev.selestor1462.cannonencoder.client.WorldHighlightRenderer.findObstacleGroundY(mc.level, lx, startY, lz);
            }
            blocks.add(new HighlightedBlock(
                new BlockPos(lx, landY, lz),
                CodingSlot.DIRECTION,
                HighlightType.TWISTING_VINES,
                null, 0, 0, 0, 0xFF00E5FF
            ));
        }

        return Optional.of(new HighlightPlan(templateId, this.directionCode, blocks, level, column));
    }

    private static BlockPos cachedPigAnchor = null;
    private static BlockPos cachedPigPanel = null;
    private static long lastPigSearchTime = 0;

    public static synchronized BlockPos findPigCannonLampPanel(BlockPos anchor) {
        return findPigCannonLampPanel(null, anchor);
    }

    public static synchronized BlockPos findPigCannonLampPanel(Level world, BlockPos anchor) {
        if (anchor == null) return null;
        long now = System.currentTimeMillis();
        if (cachedPigPanel != null && anchor.equals(cachedPigAnchor) && (now - lastPigSearchTime < 5000)) {
            return cachedPigPanel;
        }

        Level lvl = world;
        if (lvl == null) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.level != null) {
                lvl = client.level;
            }
        }
        if (lvl == null) {
            return cachedPigPanel;
        }
        cachedPigAnchor = anchor;
        lastPigSearchTime = now;

        if (lvl.hasChunkAt(anchor) && lvl.getBlockState(anchor).is(Blocks.REDSTONE_LAMP)) {
            BlockPos bl = tracePigLampPanel(lvl, anchor);
            if (bl != null) {
                cachedPigPanel = bl;
                return bl;
            }
        }

        // Search outward up to radius 40, dy +-15
        int originY = anchor.getY();
        int minY = Math.max(lvl.getMinY(), originY - 15);
        int maxY = Math.min(lvl.getMaxY(), originY + 15);

        for (int r = 0; r <= 40; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int y = originY; y <= maxY; y++) {
                        BlockPos p = new BlockPos(anchor.getX() + dx, y, anchor.getZ() + dz);
                        if (lvl.hasChunkAt(p) && lvl.getBlockState(p).is(Blocks.REDSTONE_LAMP)) {
                            BlockPos bl = tracePigLampPanel(lvl, p);
                            if (bl != null) {
                                cachedPigPanel = bl;
                                return bl;
                            }
                        }
                    }
                    for (int y = originY - 1; y >= minY; y--) {
                        BlockPos p = new BlockPos(anchor.getX() + dx, y, anchor.getZ() + dz);
                        if (lvl.hasChunkAt(p) && lvl.getBlockState(p).is(Blocks.REDSTONE_LAMP)) {
                            BlockPos bl = tracePigLampPanel(lvl, p);
                            if (bl != null) {
                                cachedPigPanel = bl;
                                return bl;
                            }
                        }
                    }
                }
            }
        }

        if (cachedPigPanel != null) {
            return cachedPigPanel;
        }
        return null;
    }

    private static BlockPos tracePigLampPanel(Level world, BlockPos p) {
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
        if (width >= 4 && height >= 3) {
            return bottom;
        }
        return null;
    }

    private static BlockPos cachedBaseYAnchor = null;
    private static int cachedBaseYVal = 2;
    private static long lastBaseYSearch = 0;

    public static synchronized int resolvePigCannonBaseY(BlockPos anchor) {
        if (anchor == null) return 2;
        long now = System.currentTimeMillis();
        if (anchor.equals(cachedBaseYAnchor) && (now - lastBaseYSearch < 5000)) {
            return cachedBaseYVal;
        }

        cachedBaseYAnchor = anchor;
        lastBaseYSearch = now;

        BlockPos panel = findPigCannonLampPanel(anchor);
        if (panel != null) {
            cachedBaseYVal = panel.getY() - 13;
            return cachedBaseYVal;
        }

        cachedBaseYVal = (anchor.getY() >= 15) ? (anchor.getY() - 13) : 2;
        return cachedBaseYVal;
    }
}
