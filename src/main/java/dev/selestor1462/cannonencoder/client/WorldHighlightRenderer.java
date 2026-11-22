package dev.selestor1462.cannonencoder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.CodingSlot;
import dev.selestor1462.cannonencoder.calc.ftl.FtlCalculationResult;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.HighlightPlan;
import dev.selestor1462.cannonencoder.template.HighlightType;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WorldHighlightRenderer {
    private static final double FILL_CULL_DIST_SQ = 262144.0; // 512 blocks

    // Signature Litematica cyan / light blue glowing theme (matching Litematica SCHEMATIC_OVERLAY_COLOR_MISSING #2C33B3E6)
    private static final float[] LITEMATICA_CYAN = new float[]{0.20f, 0.70f, 0.90f};      // Soft Litematica Cyan (#33B3E6)
    private static final float[] LITEMATICA_LIGHT_BLUE = new float[]{0.25f, 0.70f, 1.0f};// Light Blue (#40B2FF)

    public static int findObstacleGroundY(Level world, int x, int startY, int z) {
        if (world == null) return startY;
        int maxY = Math.min(startY, world.getMaxY() - 2);
        BlockPos p = new BlockPos(x, maxY, z);
        while (p.getY() > world.getMinY()) {
            BlockState state = world.getBlockState(p.below());
            if (state.is(Blocks.POWDER_SNOW)) {
                return p.below().getY();
            }
            if (state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT)) {
                return p.below().getY();
            }
            if (!state.isAir() && !state.canBeReplaced() && !state.is(Blocks.CARPET.gray())) {
                return p.getY();
            }
            p = p.below();
        }
        return world.getMinY() + 1;
    }

    public static BlockPos resolveEffectivePos(Level level, HighlightedBlock hb) {
        if (hb == null) return BlockPos.ZERO;
        if (hb.type() == HighlightType.LANDING_BLOCK || hb.type() == HighlightType.GRAY_CARPET) {
            if (level != null && level.hasChunkAt(hb.pos())) {
                int groundY = findObstacleGroundY(level, hb.pos().getX(), level.getMaxY() - 2, hb.pos().getZ());
                if (hb.type() == HighlightType.LANDING_BLOCK) {
                    return new BlockPos(hb.pos().getX(), groundY, hb.pos().getZ());
                } else {
                    return new BlockPos(hb.pos().getX(), groundY + 1, hb.pos().getZ());
                }
            }
        } else if (hb.type() == HighlightType.TWISTING_VINES) {
            if (level != null && level.hasChunkAt(hb.pos())) {
                int groundY = findObstacleGroundY(level, hb.pos().getX(), level.getMaxY() - 2, hb.pos().getZ());
                return new BlockPos(hb.pos().getX(), groundY, hb.pos().getZ());
            }
        }
        return hb.pos();
    }

    public static boolean isRightBlockWrongState(HighlightedBlock hb, BlockState state) {
        if (hb == null || state == null) return false;
        return switch (hb.type()) {
            case OBSERVER, TARGET_OBSERVER -> state.is(Blocks.OBSERVER);
            case RAIL -> BaseRailBlock.isRail(state);
            default -> false;
        };
    }

    public static boolean isBlockSatisfied(Level level, HighlightedBlock hb) {
        if (level == null || hb == null) return false;
        try {
            BlockPos effPos = resolveEffectivePos(level, hb);
            BlockState state = level.getBlockState(effPos);
            if (state == null) return false;
            return switch (hb.type()) {
                case OBSERVER -> {
                    if (!state.is(Blocks.OBSERVER) || !state.hasProperty(ObserverBlock.FACING)) yield false;
                    Direction neededFacing = hb.facing() != null ? hb.facing() : Direction.DOWN;
                    yield state.getValue(ObserverBlock.FACING) == neededFacing;
                }
                case TARGET_OBSERVER -> {
                    if (!state.is(Blocks.OBSERVER) || !state.hasProperty(ObserverBlock.FACING)) yield false;
                    Direction neededFacing = hb.facing() != null ? hb.facing() : Direction.EAST;
                    yield state.getValue(ObserverBlock.FACING) == neededFacing;
                }
                case NOTE_BLOCK -> state.is(Blocks.NOTE_BLOCK);
                case REDSTONE_DUST -> state.is(Blocks.REDSTONE_WIRE);
                case RAIL -> {
                    if (!BaseRailBlock.isRail(state)) yield false;
                    RailShape shape = null;
                    if (state.hasProperty(PoweredRailBlock.SHAPE)) {
                        shape = state.getValue(PoweredRailBlock.SHAPE);
                    } else if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE)) {
                        shape = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE);
                    }
                    if (shape != null) {
                        Direction facing = hb.facing();
                        RailShape needed = (facing == Direction.NORTH || facing == Direction.SOUTH)
                            ? RailShape.NORTH_SOUTH
                            : RailShape.EAST_WEST;
                        yield shape == needed;
                    }
                    yield true;
                }
                case GLASS -> state.is(Blocks.STAINED_GLASS.black())
                    || state.is(Blocks.GLASS)
                    || state.getBlock() instanceof StainedGlassBlock
                    || state.is(Blocks.TINTED_GLASS)
                    || state.is(Blocks.OBSERVER)
                    || state.is(Blocks.NOTE_BLOCK);
                case STAINED_GLASS -> state.getBlock() instanceof StainedGlassBlock;
                case CATCHER_BLOCK -> state.is(Blocks.STAINED_GLASS.red()) || state.is(Blocks.HONEY_BLOCK) || state.is(Blocks.SLIME_BLOCK) || !state.isAir();
                case SELECTED_LAMP -> false;
                case LANDING_BLOCK -> state.is(Blocks.POWDER_SNOW);
                case TWISTING_VINES -> state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT);
                case GRAY_CARPET -> state.is(Blocks.CARPET.gray()) || state.is(Blocks.CARPET.lightGray());
            };
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Checks if a ghost rail has its supporting block directly below placed in the world.
     * If the supporting block is air or an unsatisfied planned block, this returns true (missing).
     */
    public static boolean isRailSupportingBlockMissing(Level level, HighlightedBlock hb, List<HighlightedBlock> allBlocks) {
        if (level == null || hb == null) return false;
        if (hb.type() != HighlightType.RAIL) return false;
        return isSupportingBlockMissing(level, hb, allBlocks);
    }

    /**
     * Checks if a top-level block (rail, redstone dust, carpet, or floating observer)
     * is missing its supporting block directly below. If the support below is air,
     * the top block is hidden to prevent visual clutter and obstruction.
     */
    public static boolean isSupportingBlockMissing(Level level, HighlightedBlock hb) {
        return isSupportingBlockMissing(level, hb, null);
    }

    /**
     * Checks if a top-level block (rail, redstone dust, carpet, or floating observer)
     * is missing its supporting block directly below, or if the supporting block
     * in the current encoding plan is not yet satisfied (e.g. wrong block like old glass).
     * If unsatisfied or air, the top block is hidden to prevent visual clutter and obstruction.
     */
    public static boolean isSupportingBlockMissing(Level level, HighlightedBlock hb, List<HighlightedBlock> allBlocks) {
        if (level == null || hb == null) return false;
        try {
            BlockPos effPos = resolveEffectivePos(level, hb);
            if (hb.type() == HighlightType.RAIL
                || hb.type() == HighlightType.REDSTONE_DUST
                || hb.type() == HighlightType.GRAY_CARPET
                || (hb.type() == HighlightType.OBSERVER && hb.facing() == Direction.DOWN)) {
                BlockPos below = effPos.below();
                if (allBlocks != null) {
                    for (HighlightedBlock other : allBlocks) {
                        if (resolveEffectivePos(level, other).equals(below)) {
                            if (!isBlockSatisfied(level, other)) {
                                return true; // Planned supporting block is not yet correctly placed!
                            }
                        }
                    }
                }
                BlockState belowState = level.getBlockState(below);
                return belowState.isAir() || belowState.canBeReplaced();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private WorldHighlightRenderer() {
    }

    /**
     * Resolves the canonical BlockState for a given HighlightedBlock.
     * Used by BlockPickerManager and EasyPlaceManager.
     */
    public static BlockState resolveBlockState(HighlightedBlock hb) {
        if (hb == null) return Blocks.AIR.defaultBlockState();
        return switch (hb.type()) {
            case OBSERVER -> {
                Direction facing = hb.facing() != null ? hb.facing() : Direction.DOWN;
                yield Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, facing);
            }
            case TARGET_OBSERVER -> {
                Direction facing = hb.facing() != null ? hb.facing() : Direction.EAST;
                yield Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, facing);
            }
            case NOTE_BLOCK -> Blocks.NOTE_BLOCK.defaultBlockState();
            case REDSTONE_DUST -> Blocks.REDSTONE_WIRE.defaultBlockState()
                .setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE)
                .setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE);
            case RAIL -> {
                Direction facing = hb.facing();
                RailShape shape = (facing == Direction.NORTH || facing == Direction.SOUTH)
                    ? RailShape.NORTH_SOUTH
                    : RailShape.EAST_WEST;
                yield Blocks.ACTIVATOR_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape);
            }
            case GLASS -> Blocks.STAINED_GLASS.black().defaultBlockState();
            case STAINED_GLASS -> resolveStainedGlass(hb);
            case CATCHER_BLOCK -> Blocks.STAINED_GLASS.red().defaultBlockState();
            case SELECTED_LAMP -> Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true);
            case LANDING_BLOCK -> Blocks.POWDER_SNOW.defaultBlockState();
            case TWISTING_VINES -> Blocks.TWISTING_VINES.defaultBlockState();
            case GRAY_CARPET -> Blocks.CARPET.gray().defaultBlockState();
        };
    }

    private static BlockState resolveStainedGlass(HighlightedBlock hb) {
        int color = hb.customColor();
        if (color == 0xFFE65100) return Blocks.STAINED_GLASS.orange().defaultBlockState();
        if (color == 0xFFD50000) return Blocks.STAINED_GLASS.red().defaultBlockState();
        if (color == 0xFFFFD600) return Blocks.STAINED_GLASS.yellow().defaultBlockState();
        if (color == 0xFF00C853) return Blocks.STAINED_GLASS.lime().defaultBlockState();
        if (color == 0xFF00B0FF) return Blocks.STAINED_GLASS.lightBlue().defaultBlockState();
        if (color == 0xFF2979FF) return Blocks.STAINED_GLASS.blue().defaultBlockState();
        if (color == 0xFFAA00FF) return Blocks.STAINED_GLASS.purple().defaultBlockState();
        if (color == 0xFFFF4081) return Blocks.STAINED_GLASS.pink().defaultBlockState();
        if (color == 0xFF00E5FF) return Blocks.STAINED_GLASS.cyan().defaultBlockState();
        return Blocks.STAINED_GLASS.orange().defaultBlockState();
    }

    /**
     * Renders real 3D textured block models (Observer, Rail, Note Block, Glass, etc.) for missing planned blocks.
     */
    public static void renderBlockModels(LevelRenderContext context, HighlightManager highlightManager) {
        Minecraft client = Minecraft.getInstance();
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(client);
        if (sessionContext.isEmpty() || client.level == null) {
            return;
        }

        List<HighlightedBlock> blocks = highlightManager.visibleHighlights(sessionContext.get());
        if (blocks.isEmpty()) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        if (poseStack == null || collector == null) {
            return;
        }

        Vec3 cam = client.gameRenderer.mainCamera().position();
        double MODEL_CULL_DIST_SQ = 64.0 * 64.0; // Render 3D textured models within 64 blocks of camera

        for (HighlightedBlock hb : blocks) {
            try {
                if (hb.type() == HighlightType.SELECTED_LAMP) {
                    continue;
                }
                BlockPos pos = resolveEffectivePos(client.level, hb);
                if (isBlockSatisfied(client.level, hb)) {
                    continue;
                }
                if (highlightManager != null && highlightManager.isSequentialRails() && isRailSupportingBlockMissing(client.level, hb, blocks)) {
                    continue;
                }
                // Do not display ghost model if an occupied non-replaceable wrong block is present until it is removed
                BlockState currentAtPos = client.level != null ? client.level.getBlockState(pos) : null;
                boolean isOccupiedByWrongBlock = currentAtPos != null && !currentAtPos.isAir() && !currentAtPos.canBeReplaced();
                if (isOccupiedByWrongBlock) {
                    continue;
                }
                double distSq = pos.distToCenterSqr(cam);
                if (distSq > MODEL_CULL_DIST_SQ) {
                    continue;
                }

                BlockState state = resolveBlockState(hb);
                if (state == null || state.isAir()) {
                    continue;
                }

                poseStack.pushPose();
                try {
                    poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);

                    MovingBlockRenderState renderState = new MovingBlockRenderState();
                    renderState.randomSeedPos = pos;
                    renderState.blockPos = pos;
                    renderState.blockState = state;
                    renderState.biome = client.level.getBiome(pos);
                    renderState.cardinalLighting = client.level.cardinalLighting();
                    renderState.lightEngine = client.level.getLightEngine();

                    // 0 = standard full-fidelity 3D textured model rendering
                    collector.submitMovingBlock(poseStack, renderState, 0);
                } finally {
                    poseStack.popPose();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void render(LevelRenderContext context, HighlightManager highlightManager) {
        Minecraft client = Minecraft.getInstance();
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(client);
        if (sessionContext.isEmpty()) {
            return;
        }

        List<HighlightedBlock> blocks = highlightManager.visibleHighlights(sessionContext.get());
        if (blocks.isEmpty()) {
            return;
        }

        Vec3 cam = client.gameRenderer.mainCamera().position();
        float pulse = 0.88f + 0.12f * (float) Math.sin((double) System.currentTimeMillis() / 1000.0 * 1.5);

        try (Gizmos.TemporaryCollection ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (HighlightedBlock hb : blocks) {
                try {
                    BlockPos pos = resolveEffectivePos(client.level, hb);
                    if (isBlockSatisfied(client.level, hb)) {
                        continue;
                    }
                    HighlightType type = hb.type();
                    boolean isOccupiedByWrongBlock = client.level != null && !client.level.getBlockState(pos).isAir();

                    if (highlightManager != null && highlightManager.isSequentialRails() && isRailSupportingBlockMissing(client.level, hb, blocks)) {
                        continue;
                    }
                    if (type != HighlightType.CATCHER_BLOCK
                        && type != HighlightType.LANDING_BLOCK
                        && type != HighlightType.TWISTING_VINES
                        && type != HighlightType.GRAY_CARPET
                        && pos.distToCenterSqr(cam) > FILL_CULL_DIST_SQ) {
                        continue;
                    }

                    // If an incorrect block or wrong state (orientation/direction) is occupying this slot
                    if (isOccupiedByWrongBlock && type != HighlightType.CATCHER_BLOCK && type != HighlightType.SELECTED_LAMP && type != HighlightType.LANDING_BLOCK && type != HighlightType.TWISTING_VINES && type != HighlightType.GRAY_CARPET) {
                        BlockState currentState = client.level.getBlockState(pos);
                        if (isRightBlockWrongState(hb, currentState)) {
                            Direction neededFacing = hb.facing();
                            if (neededFacing == null) {
                                neededFacing = (type == HighlightType.TARGET_OBSERVER) ? Direction.EAST : Direction.DOWN;
                            }
                            drawOrangeWarningBox(pos, neededFacing, type == HighlightType.RAIL, pulse);
                        } else {
                            drawRedWarningBox(pos, type == HighlightType.RAIL || type == HighlightType.GRAY_CARPET, pulse);
                        }
                        continue;
                    }

                    // Litematica signature glowing light-blue styling (SCHEMATIC_OVERLAY_COLOR_MISSING #2C33B3E6)
                    int cyanStroke = argb(LITEMATICA_CYAN, 0.95f);
                    int cyanFill = argb(LITEMATICA_CYAN, (0.28f + 0.10f * pulse));

                    switch (type) {
                        case OBSERVER -> {
                            // Vibrant glowing cyan blueprint cuboid
                            AABB box = new AABB(pos).inflate(0.004);
                            Gizmos.cuboid(box, GizmoStyle.fill(cyanFill));
                            Gizmos.cuboid(box, GizmoStyle.stroke(cyanStroke, 2.4f));

                            // Clear direction arrow showing observer detection face
                            Direction facing = hb.facing() != null ? hb.facing() : Direction.DOWN;
                            double midX = pos.getX() + 0.5;
                            double midY = pos.getY() + 0.5;
                            double midZ = pos.getZ() + 0.5;
                            Vec3 start = new Vec3(midX, midY, midZ);
                            Vec3 end = start.add(
                                facing.getStepX() * 0.45,
                                facing.getStepY() * 0.45,
                                facing.getStepZ() * 0.45
                            );
                            Gizmos.arrow(start, end, cyanStroke, 3.2f);

                            // Redstone output indicator on the opposite side
                            Direction back = facing.getOpposite();
                            Vec3 backCenter = start.add(
                                back.getStepX() * 0.45,
                                back.getStepY() * 0.45,
                                back.getStepZ() * 0.45
                            );
                            int redDot = argb(new float[]{1.0f, 0.2f, 0.2f}, 0.95f);
                            AABB dotBox = new AABB(
                                backCenter.x - 0.14, backCenter.y - 0.14, backCenter.z - 0.14,
                                backCenter.x + 0.14, backCenter.y + 0.14, backCenter.z + 0.14
                            );
                            Gizmos.cuboid(dotBox, GizmoStyle.fill(redDot));
                        }
                        case TARGET_OBSERVER -> {
                            // Glowing cyan ghost box with facing arrow
                            AABB box = new AABB(pos).inflate(0.004);
                            Gizmos.cuboid(box, GizmoStyle.fill(cyanFill));
                            Gizmos.cuboid(box, GizmoStyle.stroke(cyanStroke, 2.4f));

                            Direction facing = hb.facing() != null ? hb.facing() : Direction.WEST;
                            Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                            Vec3 arrowTip = center.add(
                                facing.getStepX() * 0.45,
                                facing.getStepY() * 0.45,
                                facing.getStepZ() * 0.45
                            );
                            Gizmos.arrow(center, arrowTip, cyanStroke, 3.2f);
                        }
                        case NOTE_BLOCK -> {
                            // Vibrant glowing cyan blueprint wireframe with visible translucent interior
                            AABB nbBox = new AABB(pos).inflate(0.004);
                            Gizmos.cuboid(nbBox, GizmoStyle.fill(cyanFill));
                            Gizmos.cuboid(nbBox, GizmoStyle.stroke(cyanStroke, 2.4f));
                        }
                        case REDSTONE_DUST -> {
                            // Connected redstone wire line along X axis with glowing outline
                            AABB dustBox = new AABB(
                                pos.getX(), pos.getY(), pos.getZ() + 0.35,
                                pos.getX() + 1.0, pos.getY() + 0.08, pos.getZ() + 0.65
                            );
                            int rdFill = argb(new float[]{1.0f, 0.2f, 0.2f}, 0.65f * pulse);
                            Gizmos.cuboid(dustBox, GizmoStyle.fill(rdFill));
                            Gizmos.cuboid(dustBox, GizmoStyle.stroke(cyanStroke, 2.2f));
                        }
                        case GLASS -> {
                            // Vibrant glowing cyan blueprint outline and tint around black glass
                            AABB glassBox = new AABB(pos).inflate(0.003);
                            int glassFill = argb(LITEMATICA_CYAN, (0.22f + 0.08f * pulse));
                            Gizmos.cuboid(glassBox, GizmoStyle.fill(glassFill));
                            Gizmos.cuboid(glassBox, GizmoStyle.stroke(cyanStroke, 2.2f));
                        }
                        case STAINED_GLASS -> {
                            int color = hb.customColor();
                            float[] rgb = decodeRgb(color);
                            int sgFill = argb(rgb, (0.25f + 0.08f * pulse));
                            int sgStroke = argb(rgb, 0.95f);
                            AABB box = new AABB(pos).inflate(0.003);
                            Gizmos.cuboid(box, GizmoStyle.fill(sgFill));
                            Gizmos.cuboid(box, GizmoStyle.stroke(sgStroke, 2.2f));
                        }
                        case RAIL -> {
                            // Vibrant glowing blueprint bounds around rail with directional guide
                            AABB railBox = new AABB(
                                pos.getX() + 0.04, pos.getY(), pos.getZ() + 0.04,
                                pos.getX() + 0.96, pos.getY() + 0.14, pos.getZ() + 0.96
                            );
                            Gizmos.cuboid(railBox, GizmoStyle.fill(cyanFill));
                            Gizmos.cuboid(railBox, GizmoStyle.stroke(cyanStroke, 2.2f));

                            Direction facing = hb.facing();
                            double minX = pos.getX() + 0.1, maxX = pos.getX() + 0.9;
                            double minZ = pos.getZ() + 0.1, maxZ = pos.getZ() + 0.9;
                            double midX = pos.getX() + 0.5, midZ = pos.getZ() + 0.5;
                            double topY = pos.getY() + 0.15;
                            if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                                Gizmos.line(new Vec3(midX, topY, minZ), new Vec3(midX, topY, maxZ), cyanStroke, 2.5f);
                            } else {
                                Gizmos.line(new Vec3(minX, topY, midZ), new Vec3(maxX, topY, midZ), cyanStroke, 2.5f);
                            }
                        }
                        case CATCHER_BLOCK -> {
                            int catchFill = argb(new float[]{1.0f, 0.15f, 0.15f}, 0.35f * pulse);
                            int catchStroke = argb(new float[]{1.0f, 0.25f, 0.25f}, 1.0f);
                            AABB catchBox = new AABB(pos).inflate(0.01);
                            Gizmos.cuboid(catchBox, GizmoStyle.fill(catchFill));
                            Gizmos.cuboid(catchBox, GizmoStyle.stroke(catchStroke, 2.5f));
                        }
                        case LANDING_BLOCK -> {
                            int snowFill = argb(new float[]{0.6f, 0.9f, 1.0f}, 0.35f * pulse);
                            int snowStroke = argb(new float[]{0.3f, 0.85f, 1.0f}, 1.0f);
                            AABB snowBox = new AABB(pos).inflate(0.01);
                            Gizmos.cuboid(snowBox, GizmoStyle.fill(snowFill));
                            Gizmos.cuboid(snowBox, GizmoStyle.stroke(snowStroke, 2.5f));
                        }
                        case TWISTING_VINES -> {
                            int vineFill = argb(new float[]{0.0f, 0.90f, 0.85f}, 0.35f * pulse);
                            int vineStroke = argb(new float[]{0.0f, 1.0f, 0.90f}, 1.0f);
                            AABB vineBox = new AABB(pos).inflate(0.01);
                            Gizmos.cuboid(vineBox, GizmoStyle.fill(vineFill));
                            Gizmos.cuboid(vineBox, GizmoStyle.stroke(vineStroke, 2.5f));
                        }
                        case GRAY_CARPET -> {
                            int carpetFill = argb(new float[]{0.45f, 0.45f, 0.45f}, 0.55f * pulse);
                            int carpetStroke = argb(new float[]{0.7f, 0.7f, 0.7f}, 1.0f);
                            AABB carpetBox = new AABB(
                                pos.getX(), pos.getY(), pos.getZ(),
                                pos.getX() + 1.0, pos.getY() + 0.0625, pos.getZ() + 1.0
                            );
                            Gizmos.cuboid(carpetBox, GizmoStyle.fill(carpetFill));
                            Gizmos.cuboid(carpetBox, GizmoStyle.stroke(carpetStroke, 2.0f));
                        }
                        case SELECTED_LAMP -> {
                            int lampFill = argb(new float[]{1.0f, 0.75f, 0.1f}, 0.12f * pulse);
                            int lampStroke = argb(new float[]{1.0f, 0.85f, 0.2f}, 1.0f);
                            AABB lampBox = new AABB(pos).inflate(0.025);
                            Gizmos.cuboid(lampBox, GizmoStyle.fill(lampFill));
                            Gizmos.cuboid(lampBox, GizmoStyle.stroke(lampStroke, 2.5f));
                        }
                    }
                } catch (Throwable t) {
                    // Safe guard per block rendering
                }
            }

            // Render extra/unexpected blocks along Z axis in active column in glowing purple
            renderExtraBlocks(client.level, highlightManager, cam, pulse);
        }
    }

    public static void drawRedWarningBox(BlockPos pos, boolean isFlat, float pulse) {
        // High-contrast glowing RED warning box with X for incorrect/wrong blocks
        float[] RED = new float[]{1.0f, 0.05f, 0.05f};
        int warnStroke = argb(RED, 0.95f * pulse);
        int warnFill = argb(new float[]{1.0f, 0.0f, 0.0f}, 0.35f * pulse);
        AABB warnBox = isFlat
            ? new AABB(pos.getX() + 0.02, pos.getY(), pos.getZ() + 0.02, pos.getX() + 0.98, pos.getY() + 0.16, pos.getZ() + 0.98)
            : new AABB(pos).inflate(0.008);
        Gizmos.cuboid(warnBox, GizmoStyle.fill(warnFill));
        Gizmos.cuboid(warnBox, GizmoStyle.stroke(warnStroke, 3.0f));

        double minX = pos.getX() + 0.05, maxX = pos.getX() + 0.95;
        double minZ = pos.getZ() + 0.05, maxZ = pos.getZ() + 0.95;
        double topY = isFlat ? pos.getY() + 0.17 : pos.getY() + 1.01;
        Gizmos.line(new Vec3(minX, topY, minZ), new Vec3(maxX, topY, maxZ), warnStroke, 2.5f);
        Gizmos.line(new Vec3(minX, topY, maxZ), new Vec3(maxX, topY, minZ), warnStroke, 2.5f);
    }

    public static void drawRedWarningBox(BlockPos pos, float pulse) {
        drawRedWarningBox(pos, false, pulse);
    }

    public static void drawOrangeWarningBox(BlockPos pos, Direction neededFacing, boolean isRail, float pulse) {
        // Vibrant glowing ORANGE warning box for wrong state (rail orientation / observer direction)
        float[] ORANGE = new float[]{1.0f, 0.55f, 0.0f};
        int warnStroke = argb(ORANGE, 0.95f * pulse);
        int warnFill = argb(ORANGE, 0.35f * pulse);
        AABB warnBox = isRail
            ? new AABB(pos.getX() + 0.02, pos.getY(), pos.getZ() + 0.02, pos.getX() + 0.98, pos.getY() + 0.16, pos.getZ() + 0.98)
            : new AABB(pos).inflate(0.008);
        Gizmos.cuboid(warnBox, GizmoStyle.fill(warnFill));
        Gizmos.cuboid(warnBox, GizmoStyle.stroke(warnStroke, 3.0f));

        if (isRail) {
            // Draw guideline along the expected rail orientation on top face
            double minX = pos.getX() + 0.1, maxX = pos.getX() + 0.9;
            double minZ = pos.getZ() + 0.1, maxZ = pos.getZ() + 0.9;
            double midX = pos.getX() + 0.5;
            double midZ = pos.getZ() + 0.5;
            double topY = pos.getY() + 0.17;
            if (neededFacing == Direction.NORTH || neededFacing == Direction.SOUTH) {
                Gizmos.line(new Vec3(midX, topY, minZ), new Vec3(midX, topY, maxZ), warnStroke, 3.0f);
            } else {
                Gizmos.line(new Vec3(minX, topY, midZ), new Vec3(maxX, topY, midZ), warnStroke, 3.0f);
            }
        } else if (neededFacing != null) {
            // Draw direction arrow showing the direction the observer face should point
            double midX = pos.getX() + 0.5;
            double midY = pos.getY() + 0.5;
            double midZ = pos.getZ() + 0.5;
            Vec3 start = new Vec3(midX, midY, midZ);
            Vec3 end = start.add(
                neededFacing.getStepX() * 0.45,
                neededFacing.getStepY() * 0.45,
                neededFacing.getStepZ() * 0.45
            );
            Gizmos.arrow(start, end, warnStroke, 3.0f);
        }
    }

    public static void drawPurpleWarningBox(BlockPos pos, float pulse) {
        // Vibrant neon purple glowing theme (#AA00FF / #D500F9)
        float[] PURPLE = new float[]{0.80f, 0.12f, 1.0f};
        int purpleStroke = argb(PURPLE, 0.95f * pulse);
        int purpleFill = argb(PURPLE, 0.35f * pulse);
        AABB warnBox = new AABB(pos).inflate(0.008);
        Gizmos.cuboid(warnBox, GizmoStyle.fill(purpleFill));
        Gizmos.cuboid(warnBox, GizmoStyle.stroke(purpleStroke, 3.0f));

        // Draw distinct 'X' warning marks across top face
        double minX = pos.getX() + 0.05, maxX = pos.getX() + 0.95;
        double minZ = pos.getZ() + 0.05, maxZ = pos.getZ() + 0.95;
        double topY = pos.getY() + 1.01;
        Gizmos.line(new Vec3(minX, topY, minZ), new Vec3(maxX, topY, maxZ), purpleStroke, 2.5f);
        Gizmos.line(new Vec3(minX, topY, maxZ), new Vec3(maxX, topY, minZ), purpleStroke, 2.5f);
    }

    private static void renderExtraBlocks(Level level, HighlightManager highlightManager, Vec3 cam, float pulse) {
        if (level == null || highlightManager == null) return;
        try {
            SessionState state = highlightManager.currentState();
            HighlightPlan plan = highlightManager.activePlan();
            if (state == null || plan == null) return;

            TemplateRegistry registry = highlightManager.templateRegistry();
            boolean isFtl = plan.templateId().startsWith("ftl_");

            java.util.Set<BlockPos> plannedPositions = new java.util.HashSet<>();
            for (HighlightedBlock hb : plan.blocks()) {
                plannedPositions.add(hb.pos());
            }

            double EXTRA_CULL_DIST_SQ = 64.0 * 64.0;

            if (isFtl) {
                if (state.ftlAnchor() == null || state.ftlCalculation() == null) return;
                AnchorState anchor = state.ftlAnchor();
                FtlCalculationResult calc = state.ftlCalculation();
                if (calc.encodingData() == null) return;
                int levelIdx = plan.level();
                int columnIdx = plan.column();
                int clampedLevel = Math.max(0, Math.min(3, levelIdx));
                int clampedCol = Math.max(0, Math.min(8, columnIdx));
                int baseRelX = 0;
                int baseRelZ = -49;
                BlockPos baseAnchor = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.resolveBaseAnchor(anchor.pos());
                int cannonBaseRailY = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.resolveCannonBaseY(anchor.pos());
                int levelBaseRailY = cannonBaseRailY + clampedLevel * 4;
                int rotationSteps = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.getRotationSteps(anchor.templateId());
                dev.selestor1462.cannonencoder.calc.ftl.FtlEncodingData enc = calc.encodingData();
                boolean[] bitStates = enc.getAllBits();
                int cannonSize = (enc.cannonMaxTnt() > 0) ? enc.cannonMaxTnt() : 6688;

                for (int bitIndex = 0; bitIndex < dev.selestor1462.cannonencoder.calc.ftl.FtlEncodingData.TOTAL_BITS; bitIndex++) {
                    boolean bitAllowed = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.isBitPresentInCannon(bitIndex, cannonSize);
                    boolean bitSet = bitAllowed && bitStates[bitIndex];
                    int bitRelZ = baseRelZ + bitIndex;
                    boolean isTarget = dev.selestor1462.cannonencoder.calc.ftl.FtlEncodingData.isTargetBit(bitIndex);

                    if (!bitSet) {
                        int obsRelY = isTarget ? (levelBaseRailY + 2) : (levelBaseRailY + 1);
                        int railRelY = isTarget ? (levelBaseRailY + 3) : (levelBaseRailY + 2);

                        BlockPos obsPos = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.rotateLocal(baseAnchor, baseRelX + clampedCol, obsRelY, bitRelZ, rotationSteps);
                        if (!plannedPositions.contains(obsPos) && obsPos.distToCenterSqr(cam) <= EXTRA_CULL_DIST_SQ && level.hasChunkAt(obsPos) && level.getBlockState(obsPos).is(Blocks.OBSERVER)) {
                            drawPurpleWarningBox(obsPos, pulse);
                        }

                        BlockPos railPos = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.rotateLocal(baseAnchor, baseRelX + clampedCol, railRelY, bitRelZ, rotationSteps);
                        if (!plannedPositions.contains(railPos) && railPos.distToCenterSqr(cam) <= EXTRA_CULL_DIST_SQ && level.hasChunkAt(railPos) && level.getBlockState(railPos).is(Blocks.OBSERVER)) {
                            drawPurpleWarningBox(railPos, pulse);
                        }

                        if (isTarget) {
                            BlockPos notePos = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.rotateLocal(baseAnchor, baseRelX + clampedCol, levelBaseRailY + 1, bitRelZ, rotationSteps);
                            if (!plannedPositions.contains(notePos) && notePos.distToCenterSqr(cam) <= EXTRA_CULL_DIST_SQ && level.hasChunkAt(notePos)) {
                                BlockState s = level.getBlockState(notePos);
                                if (s.is(Blocks.NOTE_BLOCK) || s.is(Blocks.OBSERVER)) {
                                    drawPurpleWarningBox(notePos, pulse);
                                }
                            }
                        }
                    }
                }
            } else {
                // Pig Cannon
                if (state.anchor() == null || state.lastCalculation() == null) return;
                AnchorState anchor = state.anchor();
                CalculationResult calc = state.lastCalculation();
                int levelIdx = plan.level();
                int columnIdx = plan.column();
                int clampedCol = Math.max(0, Math.min(8, columnIdx));
                int clampedLevel = Math.max(0, levelIdx);
                BlockPos fixedAnchor = dev.selestor1462.cannonencoder.template.DirectionTemplate.resolveFixedAnchor(level, anchor.pos());

                dev.selestor1462.cannonencoder.template.PigCannonTemplate template = registry.getTemplate(anchor.templateId());
                if (template == null) {
                    template = registry.getTemplate(TemplateRegistry.CANNON_V1_DEFAULT);
                }
                if (template != null) {
                    dev.selestor1462.cannonencoder.template.DirectionTemplate dirTemplate = template.directions().get(calc.directionCode());
                    if (dirTemplate != null) {
                        for (dev.selestor1462.cannonencoder.template.SlotPattern pattern : dirTemplate.slotPatterns().values()) {
                            int value = calc.slotValue(pattern.slot());
                            for (int b = 0; b < pattern.width(); ++b) {
                                boolean bitSet = ((value >> b) & 1) == 1;
                                if (!bitSet) {
                                    dev.selestor1462.cannonencoder.template.RelativeBlockPos stainedGlassRel = pattern.start()
                                        .add(pattern.step().multiply(b))
                                        .add(new dev.selestor1462.cannonencoder.template.RelativeBlockPos(0, 4 * clampedLevel, 0));

                                    int distanceToTarget = 9 - clampedCol;
                                    dev.selestor1462.cannonencoder.template.RelativeBlockPos slotObserverRel = stainedGlassRel
                                        .add(pattern.bridgeVec().multiply(-distanceToTarget))
                                        .add(new dev.selestor1462.cannonencoder.template.RelativeBlockPos(0, -2, 0));

                                    BlockPos basePos = slotObserverRel.addTo(fixedAnchor);
                                    if (!plannedPositions.contains(basePos) && basePos.distToCenterSqr(cam) <= EXTRA_CULL_DIST_SQ && level.hasChunkAt(basePos) && level.getBlockState(basePos).is(Blocks.OBSERVER)) {
                                        drawPurpleWarningBox(basePos, pulse);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static float[] decodeRgb(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }

    private static int argb(float[] c, float alpha) {
        return Math.round(clamp(alpha) * 255.0f) << 24
            | Math.round(clamp(c[0]) * 255.0f) << 16
            | Math.round(clamp(c[1]) * 255.0f) << 8
            | Math.round(clamp(c[2]) * 255.0f);
    }

    private static float clamp(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
