package dev.selestor1462.cannonencoder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.ftl.FtlCalculationResult;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.HighlightPlan;
import dev.selestor1462.cannonencoder.template.HighlightType;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Vector3f;

public final class WorldHighlightRenderer {
    private static final double FILL_CULL_DIST_SQ = 262144.0; // 512 blocks
    private static final com.mojang.blaze3d.vertex.ByteBufferBuilder MODEL_BUFFER = new com.mojang.blaze3d.vertex.ByteBufferBuilder(512 * 1024);
    private static final MultiBufferSource.BufferSource MODEL_CONSUMERS = MultiBufferSource.immediate(MODEL_BUFFER);

    // Signature Litematica cyan / light blue glowing theme
    private static final float[] LITEMATICA_CYAN = new float[]{0.20f, 0.70f, 0.90f};
    private static final float[] LITEMATICA_LIGHT_BLUE = new float[]{0.25f, 0.70f, 1.0f};

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
            if (!state.isAir() && !state.canBeReplaced() && !state.is(Blocks.GRAY_CARPET)) {
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

    /**
     * Checks if a ghost block is already correctly satisfied by the real block placed in the world.
     */
    public static boolean isBlockSatisfied(Level level, HighlightedBlock hb) {
        if (level == null || hb == null) {
            return false;
        }
        BlockPos effectivePos = resolveEffectivePos(level, hb);
        if (!level.hasChunkAt(effectivePos)) {
            return false;
        }

        try {
            BlockState state = level.getBlockState(effectivePos);
            if (state == null || state.isAir()) {
                return false;
            }

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
                    if (!(state.getBlock() instanceof BaseRailBlock)) yield false;
                    Direction facing = hb.facing();
                    if (facing == null) yield true;
                    RailShape needed = (facing == Direction.NORTH || facing == Direction.SOUTH)
                        ? RailShape.NORTH_SOUTH
                        : RailShape.EAST_WEST;
                    if (state.hasProperty(PoweredRailBlock.SHAPE)) {
                        yield state.getValue(PoweredRailBlock.SHAPE) == needed;
                    }
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE)) {
                        RailShape shape = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE);
                        yield shape == needed;
                    }
                    yield true;
                }
                case GLASS -> state.is(Blocks.BLACK_STAINED_GLASS)
                    || state.is(Blocks.GLASS)
                    || state.getBlock() instanceof StainedGlassBlock
                    || state.is(Blocks.TINTED_GLASS)
                    || state.is(Blocks.OBSERVER)
                    || state.is(Blocks.NOTE_BLOCK)
                    || state.isSolid();
                case STAINED_GLASS -> state.getBlock() instanceof StainedGlassBlock;
                case CATCHER_BLOCK -> state.is(Blocks.RED_STAINED_GLASS) || state.is(Blocks.HONEY_BLOCK) || state.is(Blocks.SLIME_BLOCK) || !state.isAir();
                case SELECTED_LAMP -> false;
                case LANDING_BLOCK -> state.is(Blocks.POWDER_SNOW);
                case TWISTING_VINES -> state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT);
                case GRAY_CARPET -> state.is(Blocks.GRAY_CARPET) || state.is(Blocks.LIGHT_GRAY_CARPET);
            };
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Specifically checks if a rail's physical supporting block directly below is missing (air).
     * Used by sequential rails mode to ensure rails are only easy-placed/rendered after their supporting block exists.
     */
    public static boolean isRailSupportingBlockMissing(Level level, HighlightedBlock hb, List<HighlightedBlock> allBlocks) {
        if (level == null || hb == null) return false;
        if (hb.type() != HighlightType.RAIL) return false;
        return isSupportingBlockMissing(level, hb, allBlocks);
    }

    public static boolean isSupportingBlockMissing(Level level, HighlightedBlock hb) {
        return isSupportingBlockMissing(level, hb, null);
    }

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
            case GLASS -> Blocks.BLACK_STAINED_GLASS.defaultBlockState();
            case STAINED_GLASS -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
            case SELECTED_LAMP -> Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true);
            case CATCHER_BLOCK -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case LANDING_BLOCK -> Blocks.POWDER_SNOW.defaultBlockState();
            case TWISTING_VINES -> Blocks.TWISTING_VINES.defaultBlockState();
            case GRAY_CARPET -> Blocks.GRAY_CARPET.defaultBlockState();
        };
    }

    /**
     * Renders real 3D block models (Observer, Rail, Note Block, Glass) for missing planned blocks.
     */
    public static void renderBlockModels(WorldRenderContext context, HighlightManager highlightManager) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || highlightManager == null || !highlightManager.isVisible()) {
            return;
        }

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(client);
        if (sessionContext.isEmpty() || client.level == null) {
            return;
        }

        List<HighlightedBlock> blocks = highlightManager.visibleHighlights(sessionContext.get());
        if (blocks.isEmpty()) {
            return;
        }

        PoseStack poseStack = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }

        Vec3 cam = client.gameRenderer.getMainCamera().position();
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
                if (state != null && !state.isAir()) {
                    poseStack.pushPose();
                    try {
                        poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);

                        client.getBlockRenderer().renderSingleBlock(
                            state,
                            poseStack,
                            MODEL_CONSUMERS,
                            LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY
                        );
                    } finally {
                        poseStack.popPose();
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        MODEL_CONSUMERS.endBatch();
    }

    public static void render(WorldRenderContext context, HighlightManager highlightManager) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || highlightManager == null || !highlightManager.isVisible()) {
            return;
        }

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(client);
        if (sessionContext.isEmpty() || client.level == null) {
            return;
        }

        List<HighlightedBlock> blocks = highlightManager.visibleHighlights(sessionContext.get());
        if (blocks.isEmpty()) {
            return;
        }

        PoseStack poseStack = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }

        Vec3 cam = client.gameRenderer.getMainCamera().position();
        float pulse = 0.88f + 0.12f * (float) Math.sin((double) System.currentTimeMillis() / 1000.0 * 1.5);

        VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());
        VertexConsumer quads = consumers.getBuffer(RenderTypes.debugQuads());

        for (HighlightedBlock hb : blocks) {
            try {
                BlockPos pos = resolveEffectivePos(client.level, hb);
                if (isBlockSatisfied(client.level, hb)) {
                    continue;
                }
                if (highlightManager != null && highlightManager.isSequentialRails() && isRailSupportingBlockMissing(client.level, hb, blocks)) {
                    continue;
                }
                if (pos.distToCenterSqr(cam) > FILL_CULL_DIST_SQ) {
                    continue;
                }

                HighlightType type = hb.type();
                boolean isOccupiedByWrongBlock = client.level != null && !client.level.getBlockState(pos).isAir();

                // If an incorrect block or wrong state (orientation/direction) is occupying this slot
                if (isOccupiedByWrongBlock && type != HighlightType.CATCHER_BLOCK && type != HighlightType.SELECTED_LAMP && type != HighlightType.LANDING_BLOCK && type != HighlightType.TWISTING_VINES && type != HighlightType.GRAY_CARPET) {
                    BlockState currentState = client.level.getBlockState(pos);
                    boolean isFlat = (type == HighlightType.RAIL || type == HighlightType.GRAY_CARPET);
                    if (isRightBlockWrongState(hb, currentState)) {
                        Direction neededFacing = hb.facing();
                        if (neededFacing == null) {
                            neededFacing = (type == HighlightType.TARGET_OBSERVER) ? Direction.EAST : Direction.DOWN;
                        }
                        drawOrangeWarningBox(poseStack, lines, quads, pos, cam, neededFacing, type == HighlightType.RAIL, pulse);
                    } else {
                        drawRedWarningBox(poseStack, lines, quads, pos, cam, isFlat, pulse);
                    }
                    continue;
                }

                // Litematica signature glowing light-blue styling
                int cyanStroke = argb(LITEMATICA_CYAN, 0.95f);
                int cyanFill = argb(LITEMATICA_CYAN, (0.32f + 0.12f * pulse));

                switch (type) {
                    case OBSERVER -> {
                        AABB box = new AABB(pos).inflate(0.003);
                        renderFilledBox(poseStack, quads, box, cam, cyanFill);
                        renderLineBox(poseStack, lines, box, cam, cyanStroke, 2.5f);

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
                        renderArrow(poseStack, lines, start, end, cam, cyanStroke, 3.0f);

                        Direction back = facing.getOpposite();
                        Vec3 backCenter = start.add(
                            back.getStepX() * 0.45,
                            back.getStepY() * 0.45,
                            back.getStepZ() * 0.45
                        );
                        int redDot = argb(new float[]{1.0f, 0.2f, 0.2f}, 0.95f);
                        AABB dotBox = new AABB(
                            backCenter.x - 0.12, backCenter.y - 0.12, backCenter.z - 0.12,
                            backCenter.x + 0.12, backCenter.y + 0.12, backCenter.z + 0.12
                        );
                        renderFilledBox(poseStack, quads, dotBox, cam, redDot);
                    }
                    case TARGET_OBSERVER -> {
                        AABB box = new AABB(pos).inflate(0.003);
                        renderFilledBox(poseStack, quads, box, cam, cyanFill);
                        renderLineBox(poseStack, lines, box, cam, cyanStroke, 2.5f);

                        Direction facing = hb.facing() != null ? hb.facing() : Direction.EAST;
                        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        Vec3 arrowTip = center.add(
                            facing.getStepX() * 0.45,
                            facing.getStepY() * 0.45,
                            facing.getStepZ() * 0.45
                        );
                        renderArrow(poseStack, lines, center, arrowTip, cam, cyanStroke, 3.0f);
                    }
                    case NOTE_BLOCK -> {
                        AABB nbBox = new AABB(pos).inflate(0.003);
                        int nbFill = argb(new float[]{0.35f, 0.80f, 0.95f}, 0.30f * pulse);
                        renderFilledBox(poseStack, quads, nbBox, cam, nbFill);
                        renderLineBox(poseStack, lines, nbBox, cam, cyanStroke, 2.5f);
                    }
                    case REDSTONE_DUST -> {
                        AABB dustBox = new AABB(
                            pos.getX(), pos.getY(), pos.getZ() + 0.35,
                            pos.getX() + 1.0, pos.getY() + 0.08, pos.getZ() + 0.65
                        );
                        int rdFill = argb(new float[]{1.0f, 0.2f, 0.2f}, 0.60f * pulse);
                        renderFilledBox(poseStack, quads, dustBox, cam, rdFill);
                        renderLineBox(poseStack, lines, dustBox, cam, cyanStroke, 2.2f);
                    }
                    case GLASS -> {
                        AABB glassBox = new AABB(pos).inflate(0.003);
                        int glassFill = argb(new float[]{0.05f, 0.05f, 0.05f}, 0.75f);
                        renderFilledBox(poseStack, quads, glassBox, cam, glassFill);
                        renderLineBox(poseStack, lines, glassBox, cam, cyanStroke, 2.5f);
                    }
                    case RAIL -> {
                        AABB railBox = new AABB(
                            pos.getX() + 0.05, pos.getY(), pos.getZ() + 0.05,
                            pos.getX() + 0.95, pos.getY() + 0.12, pos.getZ() + 0.95
                        );
                        renderFilledBox(poseStack, quads, railBox, cam, cyanFill);
                        renderLineBox(poseStack, lines, railBox, cam, cyanStroke, 2.2f);
                    }
                    case STAINED_GLASS -> {
                        AABB box = new AABB(pos).inflate(0.003);
                        renderFilledBox(poseStack, quads, box, cam, cyanFill);
                        renderLineBox(poseStack, lines, box, cam, cyanStroke, 2.5f);
                    }
                    case CATCHER_BLOCK -> {
                        int catchFill = argb(new float[]{1.0f, 0.15f, 0.15f}, 0.40f * pulse);
                        int catchStroke = argb(new float[]{1.0f, 0.25f, 0.25f}, 1.0f);
                        AABB catchBox = new AABB(pos).inflate(0.01);
                        renderFilledBox(poseStack, quads, catchBox, cam, catchFill);
                        renderLineBox(poseStack, lines, catchBox, cam, catchStroke, 3.0f);
                    }
                    case LANDING_BLOCK -> {
                        int snowFill = argb(new float[]{0.6f, 0.9f, 1.0f}, 0.40f * pulse);
                        int snowStroke = argb(new float[]{0.3f, 0.85f, 1.0f}, 1.0f);
                        AABB snowBox = new AABB(pos).inflate(0.01);
                        renderFilledBox(poseStack, quads, snowBox, cam, snowFill);
                        renderLineBox(poseStack, lines, snowBox, cam, snowStroke, 3.0f);
                    }
                    case TWISTING_VINES -> {
                        int vineFill = argb(new float[]{0.0f, 0.85f, 0.75f}, 0.40f * pulse);
                        int vineStroke = argb(new float[]{0.0f, 1.0f, 0.85f}, 1.0f);
                        AABB vineBox = new AABB(pos).inflate(0.01);
                        renderFilledBox(poseStack, quads, vineBox, cam, vineFill);
                        renderLineBox(poseStack, lines, vineBox, cam, vineStroke, 3.0f);
                    }
                    case GRAY_CARPET -> {
                        int carpetFill = argb(new float[]{0.45f, 0.45f, 0.45f}, 0.55f * pulse);
                        int carpetStroke = argb(new float[]{0.7f, 0.7f, 0.7f}, 1.0f);
                        AABB carpetBox = new AABB(
                            pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0, pos.getY() + 0.0625, pos.getZ() + 1.0
                        );
                        renderFilledBox(poseStack, quads, carpetBox, cam, carpetFill);
                        renderLineBox(poseStack, lines, carpetBox, cam, carpetStroke, 2.5f);
                    }
                    case SELECTED_LAMP -> {
                        int lampFill = argb(new float[]{1.0f, 0.75f, 0.1f}, 0.12f * pulse);
                        int lampStroke = argb(new float[]{1.0f, 0.85f, 0.2f}, 1.0f);
                        AABB lampBox = new AABB(pos).inflate(0.005);
                        renderFilledBox(poseStack, quads, lampBox, cam, lampFill);
                        renderLineBox(poseStack, lines, lampBox, cam, lampStroke, 2.5f);
                    }
                }
            } catch (Throwable t) {
            }
        }

        renderExtraBlocks(client.level, highlightManager, cam, pulse, poseStack, lines, quads);
    }

    private static void renderExtraBlocks(Level level, HighlightManager highlightManager, Vec3 cam, float pulse, PoseStack poseStack, VertexConsumer lines, VertexConsumer quads) {
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
                            drawPurpleWarningBox(poseStack, lines, quads, obsPos, cam, pulse);
                        }

                        BlockPos railPos = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.rotateLocal(baseAnchor, baseRelX + clampedCol, railRelY, bitRelZ, rotationSteps);
                        if (!plannedPositions.contains(railPos) && railPos.distToCenterSqr(cam) <= EXTRA_CULL_DIST_SQ && level.hasChunkAt(railPos) && level.getBlockState(railPos).is(Blocks.OBSERVER)) {
                            drawPurpleWarningBox(poseStack, lines, quads, railPos, cam, pulse);
                        }

                        if (isTarget) {
                            BlockPos notePos = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.rotateLocal(baseAnchor, baseRelX + clampedCol, levelBaseRailY + 1, bitRelZ, rotationSteps);
                            if (!plannedPositions.contains(notePos) && notePos.distToCenterSqr(cam) <= EXTRA_CULL_DIST_SQ && level.hasChunkAt(notePos)) {
                                BlockState s = level.getBlockState(notePos);
                                if (s.is(Blocks.NOTE_BLOCK) || s.is(Blocks.OBSERVER)) {
                                    drawPurpleWarningBox(poseStack, lines, quads, notePos, cam, pulse);
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
                                        drawPurpleWarningBox(poseStack, lines, quads, basePos, cam, pulse);
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

    public static void drawRedWarningBox(PoseStack poseStack, VertexConsumer lines, VertexConsumer quads, BlockPos pos, Vec3 cam, boolean isFlat, float pulse) {
        float[] RED = new float[]{1.0f, 0.05f, 0.05f};
        int warnStroke = argb(RED, 0.95f * pulse);
        int warnFill = argb(new float[]{1.0f, 0.0f, 0.0f}, 0.35f * pulse);
        AABB warnBox = isFlat
            ? new AABB(pos.getX() + 0.02, pos.getY(), pos.getZ() + 0.02, pos.getX() + 0.98, pos.getY() + 0.16, pos.getZ() + 0.98)
            : new AABB(pos).inflate(0.008);
        renderFilledBox(poseStack, quads, warnBox, cam, warnFill);
        renderLineBox(poseStack, lines, warnBox, cam, warnStroke, 3.0f);

        double minX = pos.getX() + 0.05, maxX = pos.getX() + 0.95;
        double minZ = pos.getZ() + 0.05, maxZ = pos.getZ() + 0.95;
        double topY = isFlat ? pos.getY() + 0.17 : pos.getY() + 1.01;
        renderLine(poseStack, lines, new Vec3(minX, topY, minZ), new Vec3(maxX, topY, maxZ), cam, warnStroke, 2.5f);
        renderLine(poseStack, lines, new Vec3(minX, topY, maxZ), new Vec3(maxX, topY, minZ), cam, warnStroke, 2.5f);
    }

    public static void drawRedWarningBox(PoseStack poseStack, VertexConsumer lines, VertexConsumer quads, BlockPos pos, Vec3 cam, float pulse) {
        drawRedWarningBox(poseStack, lines, quads, pos, cam, false, pulse);
    }

    public static void drawOrangeWarningBox(PoseStack poseStack, VertexConsumer lines, VertexConsumer quads, BlockPos pos, Vec3 cam, Direction neededFacing, boolean isRail, float pulse) {
        float[] ORANGE = new float[]{1.0f, 0.55f, 0.0f};
        int warnStroke = argb(ORANGE, 0.95f * pulse);
        int warnFill = argb(ORANGE, 0.35f * pulse);
        AABB warnBox = isRail
            ? new AABB(pos.getX() + 0.02, pos.getY(), pos.getZ() + 0.02, pos.getX() + 0.98, pos.getY() + 0.16, pos.getZ() + 0.98)
            : new AABB(pos).inflate(0.008);
        renderFilledBox(poseStack, quads, warnBox, cam, warnFill);
        renderLineBox(poseStack, lines, warnBox, cam, warnStroke, 3.0f);

        if (isRail) {
            double minX = pos.getX() + 0.1, maxX = pos.getX() + 0.9;
            double minZ = pos.getZ() + 0.1, maxZ = pos.getZ() + 0.9;
            double midX = pos.getX() + 0.5;
            double midZ = pos.getZ() + 0.5;
            double topY = pos.getY() + 0.17;
            if (neededFacing == Direction.NORTH || neededFacing == Direction.SOUTH) {
                renderLine(poseStack, lines, new Vec3(midX, topY, minZ), new Vec3(midX, topY, maxZ), cam, warnStroke, 3.0f);
            } else {
                renderLine(poseStack, lines, new Vec3(minX, topY, midZ), new Vec3(maxX, topY, midZ), cam, warnStroke, 3.0f);
            }
        } else if (neededFacing != null) {
            double midX = pos.getX() + 0.5;
            double midY = pos.getY() + 0.5;
            double midZ = pos.getZ() + 0.5;
            Vec3 start = new Vec3(midX, midY, midZ);
            Vec3 end = start.add(
                neededFacing.getStepX() * 0.45,
                neededFacing.getStepY() * 0.45,
                neededFacing.getStepZ() * 0.45
            );
            renderArrow(poseStack, lines, start, end, cam, warnStroke, 3.0f);
        }
    }

    public static void drawPurpleWarningBox(PoseStack poseStack, VertexConsumer lines, VertexConsumer quads, BlockPos pos, Vec3 cam, float pulse) {
        float[] PURPLE = new float[]{0.80f, 0.12f, 1.0f};
        int purpleStroke = argb(PURPLE, 0.95f * pulse);
        int purpleFill = argb(PURPLE, 0.35f * pulse);
        AABB warnBox = new AABB(pos).inflate(0.008);
        renderFilledBox(poseStack, quads, warnBox, cam, purpleFill);
        renderLineBox(poseStack, lines, warnBox, cam, purpleStroke, 3.0f);

        // Draw distinct 'X' warning marks across top face
        double minX = pos.getX() + 0.05, maxX = pos.getX() + 0.95;
        double minZ = pos.getZ() + 0.05, maxZ = pos.getZ() + 0.95;
        double topY = pos.getY() + 1.01;
        renderLine(poseStack, lines, new Vec3(minX, topY, minZ), new Vec3(maxX, topY, maxZ), cam, purpleStroke, 2.5f);
        renderLine(poseStack, lines, new Vec3(minX, topY, maxZ), new Vec3(maxX, topY, minZ), cam, purpleStroke, 2.5f);
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer lines, AABB box, Vec3 cam, int color, float lineWidth) {
        ShapeRenderer.renderShape(
            poseStack,
            lines,
            Shapes.create(box),
            -cam.x, -cam.y, -cam.z,
            color,
            lineWidth
        );
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer quads, AABB box, Vec3 cam, int argb) {
        float minX = (float) (box.minX - cam.x);
        float minY = (float) (box.minY - cam.y);
        float minZ = (float) (box.minZ - cam.z);
        float maxX = (float) (box.maxX - cam.x);
        float maxY = (float) (box.maxY - cam.y);
        float maxZ = (float) (box.maxZ - cam.z);
        PoseStack.Pose pose = poseStack.last();

        // Down
        quads.addVertex(pose, minX, minY, minZ).setColor(argb);
        quads.addVertex(pose, maxX, minY, minZ).setColor(argb);
        quads.addVertex(pose, maxX, minY, maxZ).setColor(argb);
        quads.addVertex(pose, minX, minY, maxZ).setColor(argb);

        // Up
        quads.addVertex(pose, minX, maxY, maxZ).setColor(argb);
        quads.addVertex(pose, maxX, maxY, maxZ).setColor(argb);
        quads.addVertex(pose, maxX, maxY, minZ).setColor(argb);
        quads.addVertex(pose, minX, maxY, minZ).setColor(argb);

        // North
        quads.addVertex(pose, minX, minY, minZ).setColor(argb);
        quads.addVertex(pose, minX, maxY, minZ).setColor(argb);
        quads.addVertex(pose, maxX, maxY, minZ).setColor(argb);
        quads.addVertex(pose, maxX, minY, minZ).setColor(argb);

        // South
        quads.addVertex(pose, maxX, minY, maxZ).setColor(argb);
        quads.addVertex(pose, maxX, maxY, maxZ).setColor(argb);
        quads.addVertex(pose, minX, maxY, maxZ).setColor(argb);
        quads.addVertex(pose, minX, minY, maxZ).setColor(argb);

        // West
        quads.addVertex(pose, minX, minY, maxZ).setColor(argb);
        quads.addVertex(pose, minX, maxY, maxZ).setColor(argb);
        quads.addVertex(pose, minX, maxY, minZ).setColor(argb);
        quads.addVertex(pose, minX, minY, minZ).setColor(argb);

        // East
        quads.addVertex(pose, maxX, minY, minZ).setColor(argb);
        quads.addVertex(pose, maxX, maxY, minZ).setColor(argb);
        quads.addVertex(pose, maxX, maxY, maxZ).setColor(argb);
        quads.addVertex(pose, maxX, minY, maxZ).setColor(argb);
    }

    private static void renderLine(PoseStack poseStack, VertexConsumer lines, Vec3 start, Vec3 end, Vec3 cam, int color, float lineWidth) {
        PoseStack.Pose pose = poseStack.last();
        Vector3f dir = new Vector3f((float)(end.x - start.x), (float)(end.y - start.y), (float)(end.z - start.z));
        float len = dir.length();
        if (len < 0.001f) return;
        dir.normalize();

        lines.addVertex(pose, (float)(start.x - cam.x), (float)(start.y - cam.y), (float)(start.z - cam.z))
            .setColor(color)
            .setNormal(pose, dir)
            .setLineWidth(lineWidth);
        lines.addVertex(pose, (float)(end.x - cam.x), (float)(end.y - cam.y), (float)(end.z - cam.z))
            .setColor(color)
            .setNormal(pose, dir)
            .setLineWidth(lineWidth);
    }

    private static void renderArrow(PoseStack poseStack, VertexConsumer lines, Vec3 start, Vec3 end, Vec3 cam, int color, float lineWidth) {
        PoseStack.Pose pose = poseStack.last();
        Vector3f dir = new Vector3f((float)(end.x - start.x), (float)(end.y - start.y), (float)(end.z - start.z));
        float len = dir.length();
        if (len < 0.001f) return;
        dir.normalize();

        lines.addVertex(pose, (float)(start.x - cam.x), (float)(start.y - cam.y), (float)(start.z - cam.z))
            .setColor(color)
            .setNormal(pose, dir)
            .setLineWidth(lineWidth);
        lines.addVertex(pose, (float)(end.x - cam.x), (float)(end.y - cam.y), (float)(end.z - cam.z))
            .setColor(color)
            .setNormal(pose, dir)
            .setLineWidth(lineWidth);
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
