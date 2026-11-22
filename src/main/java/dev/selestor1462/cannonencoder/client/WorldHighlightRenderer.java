package dev.selestor1462.cannonencoder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.HighlightPlan;
import dev.selestor1462.cannonencoder.template.HighlightType;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

public final class WorldHighlightRenderer {
    private static final double FILL_CULL_DIST_SQ = 262144.0; // 512 blocks

    // Signature Litematica cyan / light blue glowing theme
    private static final float[] LITEMATICA_CYAN = new float[]{0.20f, 0.70f, 0.90f};
    private static final float[] LITEMATICA_LIGHT_BLUE = new float[]{0.25f, 0.70f, 1.0f};

    public static int findObstacleGroundY(Level world, int x, int startY, int z) {
        if (world == null) return startY;
        int maxY = Math.min(startY, world.getMaxBuildHeight() - 2);
        BlockPos p = new BlockPos(x, maxY, z);
        while (p.getY() > world.getMinBuildHeight()) {
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
        return world.getMinBuildHeight() + 1;
    }

    public static BlockPos resolveEffectivePos(Level level, HighlightedBlock hb) {
        if (hb == null) return BlockPos.ZERO;
        if (hb.type() == HighlightType.TWISTING_VINES) {
            if (level != null && level.hasChunkAt(hb.pos())) {
                int groundY = findObstacleGroundY(level, hb.pos().getX(), level.getMaxBuildHeight() - 2, hb.pos().getZ());
                return new BlockPos(hb.pos().getX(), groundY, hb.pos().getZ());
            }
        }
        return hb.pos();
    }

    public static boolean isRightBlockWrongState(HighlightedBlock hb, BlockState state) {
        if (hb == null || state == null || state.isAir()) return false;
        return switch (hb.type()) {
            case OBSERVER -> {
                if (!state.is(Blocks.OBSERVER) || !state.hasProperty(ObserverBlock.FACING)) yield false;
                Direction neededFacing = hb.facing() != null ? hb.facing() : Direction.DOWN;
                yield state.getValue(ObserverBlock.FACING) != neededFacing;
            }
            case TARGET_OBSERVER -> {
                if (!state.is(Blocks.OBSERVER) || !state.hasProperty(ObserverBlock.FACING)) yield false;
                Direction neededFacing = hb.facing() != null ? hb.facing() : Direction.EAST;
                yield state.getValue(ObserverBlock.FACING) != neededFacing;
            }
            case RAIL -> {
                if (!BaseRailBlock.isRail(state)) yield false;
                Direction facing = hb.facing();
                if (facing == null) yield false;
                RailShape needed = (facing == Direction.NORTH || facing == Direction.SOUTH)
                    ? RailShape.NORTH_SOUTH
                    : RailShape.EAST_WEST;
                if (state.hasProperty(PoweredRailBlock.SHAPE)) {
                    yield state.getValue(PoweredRailBlock.SHAPE) != needed;
                }
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE)) {
                    yield state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE) != needed;
                }
                yield false;
            }
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

        PoseStack poseStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }

        Vec3 cam = client.gameRenderer.getMainCamera().getPosition();
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
                boolean isOccupiedByWrongBlock = !client.level.getBlockState(pos).isAir() && !client.level.getBlockState(pos).canBeReplaced();
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
                    poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);

                    client.getBlockRenderer().renderSingleBlock(
                        state,
                        poseStack,
                        consumers,
                        LevelRenderer.getLightColor(client.level, pos),
                        OverlayTexture.NO_OVERLAY
                    );

                    poseStack.popPose();
                }
            } catch (Throwable ignored) {
            }
        }
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

        PoseStack poseStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }

        Vec3 cam = client.gameRenderer.getMainCamera().getPosition();
        float pulse = 0.88f + 0.12f * (float) Math.sin((double) System.currentTimeMillis() / 1000.0 * 1.5);

        VertexConsumer lines = consumers.getBuffer(RenderType.lines());

        for (HighlightedBlock hb : blocks) {
            try {
                BlockPos pos = resolveEffectivePos(client.level, hb);
                if (isBlockSatisfied(client.level, hb)) {
                    continue;
                }
                if (highlightManager != null && highlightManager.isSequentialRails() && isRailSupportingBlockMissing(client.level, hb, blocks)) {
                    continue;
                }
                if (hb.type() != HighlightType.CATCHER_BLOCK
                    && hb.type() != HighlightType.LANDING_BLOCK
                    && hb.type() != HighlightType.TWISTING_VINES
                    && hb.type() != HighlightType.GRAY_CARPET
                    && pos.distToCenterSqr(cam) > FILL_CULL_DIST_SQ) {
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
                        drawOrangeWarningBox(poseStack, lines, pos, cam, neededFacing, type == HighlightType.RAIL, pulse);
                    } else {
                        drawRedWarningBox(poseStack, lines, pos, cam, isFlat, pulse);
                    }
                    continue;
                }

                // Litematica signature glowing light-blue styling
                float cR = LITEMATICA_CYAN[0];
                float cG = LITEMATICA_CYAN[1];
                float cB = LITEMATICA_CYAN[2];

                switch (type) {
                    case OBSERVER -> {
                        AABB box = new AABB(pos).inflate(0.003);
                        renderBox(poseStack, lines, box, cam, cR, cG, cB, 0.95f);

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
                        renderLine(poseStack, lines, start, end, cam, cR, cG, cB, 0.95f);
                    }
                    case TARGET_OBSERVER -> {
                        AABB box = new AABB(pos).inflate(0.003);
                        renderBox(poseStack, lines, box, cam, cR, cG, cB, 0.95f);

                        Direction facing = hb.facing() != null ? hb.facing() : Direction.EAST;
                        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        Vec3 arrowTip = center.add(
                            facing.getStepX() * 0.45,
                            facing.getStepY() * 0.45,
                            facing.getStepZ() * 0.45
                        );
                        renderLine(poseStack, lines, center, arrowTip, cam, cR, cG, cB, 0.95f);
                    }
                    case NOTE_BLOCK -> {
                        AABB nbBox = new AABB(pos).inflate(0.003);
                        renderBox(poseStack, lines, nbBox, cam, cR, cG, cB, 0.95f);
                    }
                    case REDSTONE_DUST -> {
                        AABB dustBox = new AABB(
                            pos.getX(), pos.getY(), pos.getZ() + 0.35,
                            pos.getX() + 1.0, pos.getY() + 0.08, pos.getZ() + 0.65
                        );
                        renderBox(poseStack, lines, dustBox, cam, 1.0f, 0.2f, 0.2f, 0.95f);
                    }
                    case GLASS -> {
                        AABB glassBox = new AABB(pos).inflate(0.003);
                        renderBox(poseStack, lines, glassBox, cam, cR, cG, cB, 0.95f);
                    }
                    case RAIL -> {
                        AABB railBox = new AABB(
                            pos.getX() + 0.05, pos.getY(), pos.getZ() + 0.05,
                            pos.getX() + 0.95, pos.getY() + 0.12, pos.getZ() + 0.95
                        );
                        renderBox(poseStack, lines, railBox, cam, cR, cG, cB, 0.95f);
                    }
                    case STAINED_GLASS -> {
                        AABB box = new AABB(pos).inflate(0.003);
                        renderBox(poseStack, lines, box, cam, cR, cG, cB, 0.95f);
                    }
                    case SELECTED_LAMP -> {
                        AABB lampBox = new AABB(pos).inflate(0.025);
                        renderBox(poseStack, lines, lampBox, cam, 1.0f, 0.85f, 0.2f, 1.0f);
                    }
                    case TWISTING_VINES -> {
                        AABB vineBox = new AABB(pos).inflate(0.01);
                        renderBox(poseStack, lines, vineBox, cam, 0.0f, 1.0f, 0.85f, 1.0f);
                    }
                    default -> {}
                }
            } catch (Throwable t) {
            }
        }

        renderExtraBlocks(client.level, highlightManager, cam, pulse, poseStack, lines);
    }

    private static void renderExtraBlocks(Level level, HighlightManager highlightManager, Vec3 cam, float pulse, PoseStack poseStack, VertexConsumer lines) {
        if (level == null || highlightManager == null) return;
        try {
            SessionState state = highlightManager.currentState();
            HighlightPlan plan = highlightManager.activePlan();
            if (state == null || plan == null) return;

            TemplateRegistry registry = highlightManager.templateRegistry();

            java.util.Set<BlockPos> plannedPositions = new java.util.HashSet<>();
            for (HighlightedBlock hb : plan.blocks()) {
                plannedPositions.add(hb.pos());
            }

            double EXTRA_CULL_DIST_SQ = 64.0 * 64.0;

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
                                    drawPurpleWarningBox(poseStack, lines, basePos, cam, pulse);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void drawRedWarningBox(PoseStack poseStack, VertexConsumer lines, BlockPos pos, Vec3 cam, boolean isFlat, float pulse) {
        float r = 1.0f, g = 0.05f, b = 0.05f, a = 0.95f * pulse;
        AABB warnBox = isFlat
            ? new AABB(pos.getX() + 0.02, pos.getY(), pos.getZ() + 0.02, pos.getX() + 0.98, pos.getY() + 0.16, pos.getZ() + 0.98)
            : new AABB(pos).inflate(0.008);
        renderBox(poseStack, lines, warnBox, cam, r, g, b, a);

        double minX = pos.getX() + 0.05, maxX = pos.getX() + 0.95;
        double minZ = pos.getZ() + 0.05, maxZ = pos.getZ() + 0.95;
        double topY = isFlat ? pos.getY() + 0.17 : pos.getY() + 1.01;
        renderLine(poseStack, lines, new Vec3(minX, topY, minZ), new Vec3(maxX, topY, maxZ), cam, r, g, b, a);
        renderLine(poseStack, lines, new Vec3(minX, topY, maxZ), new Vec3(maxX, topY, minZ), cam, r, g, b, a);
    }

    public static void drawRedWarningBox(PoseStack poseStack, VertexConsumer lines, BlockPos pos, Vec3 cam, float pulse) {
        drawRedWarningBox(poseStack, lines, pos, cam, false, pulse);
    }

    public static void drawOrangeWarningBox(PoseStack poseStack, VertexConsumer lines, BlockPos pos, Vec3 cam, Direction neededFacing, boolean isRail, float pulse) {
        float r = 1.0f, g = 0.55f, b = 0.0f, a = 0.95f * pulse;
        AABB warnBox = isRail
            ? new AABB(pos.getX() + 0.02, pos.getY(), pos.getZ() + 0.02, pos.getX() + 0.98, pos.getY() + 0.16, pos.getZ() + 0.98)
            : new AABB(pos).inflate(0.008);
        renderBox(poseStack, lines, warnBox, cam, r, g, b, a);

        if (isRail) {
            double minX = pos.getX() + 0.1, maxX = pos.getX() + 0.9;
            double minZ = pos.getZ() + 0.1, maxZ = pos.getZ() + 0.9;
            double midX = pos.getX() + 0.5;
            double midZ = pos.getZ() + 0.5;
            double topY = pos.getY() + 0.17;
            if (neededFacing == Direction.NORTH || neededFacing == Direction.SOUTH) {
                renderLine(poseStack, lines, new Vec3(midX, topY, minZ), new Vec3(midX, topY, maxZ), cam, r, g, b, a);
            } else {
                renderLine(poseStack, lines, new Vec3(minX, topY, midZ), new Vec3(maxX, topY, midZ), cam, r, g, b, a);
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
            renderLine(poseStack, lines, start, end, cam, r, g, b, a);
        }
    }

    public static void drawPurpleWarningBox(PoseStack poseStack, VertexConsumer lines, BlockPos pos, Vec3 cam, float pulse) {
        float[] PURPLE = new float[]{0.80f, 0.12f, 1.0f};
        AABB warnBox = new AABB(pos).inflate(0.008);
        renderBox(poseStack, lines, warnBox, cam, PURPLE[0], PURPLE[1], PURPLE[2], 0.95f * pulse);

        // Draw distinct 'X' warning marks across top face
        double minX = pos.getX() + 0.05, maxX = pos.getX() + 0.95;
        double minZ = pos.getZ() + 0.05, maxZ = pos.getZ() + 0.95;
        double topY = pos.getY() + 1.01;
        renderLine(poseStack, lines, new Vec3(minX, topY, minZ), new Vec3(maxX, topY, maxZ), cam, PURPLE[0], PURPLE[1], PURPLE[2], 0.95f * pulse);
        renderLine(poseStack, lines, new Vec3(minX, topY, maxZ), new Vec3(maxX, topY, minZ), cam, PURPLE[0], PURPLE[1], PURPLE[2], 0.95f * pulse);
    }

    private static void renderLine(PoseStack poseStack, VertexConsumer lines, Vec3 start, Vec3 end, Vec3 cam, float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        org.joml.Vector3f dir = new org.joml.Vector3f((float)(end.x - start.x), (float)(end.y - start.y), (float)(end.z - start.z));
        float len = dir.length();
        if (len < 0.001f) return;
        dir.normalize();

        lines.addVertex(pose, (float)(start.x - cam.x), (float)(start.y - cam.y), (float)(start.z - cam.z))
            .setColor(r, g, b, a)
            .setNormal(pose, dir.x, dir.y, dir.z);
        lines.addVertex(pose, (float)(end.x - cam.x), (float)(end.y - cam.y), (float)(end.z - cam.z))
            .setColor(r, g, b, a)
            .setNormal(pose, dir.x, dir.y, dir.z);
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer lines, AABB box, Vec3 cam, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(
            poseStack, lines,
            box.move(-cam.x, -cam.y, -cam.z),
            r, g, b, a
        );
    }
}
