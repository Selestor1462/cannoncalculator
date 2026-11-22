package dev.selestor1462.cannonencoder.client.validation;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.CodingSlot;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.DirectionTemplate;
import dev.selestor1462.cannonencoder.template.HighlightPlan;
import dev.selestor1462.cannonencoder.template.HighlightType;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import dev.selestor1462.cannonencoder.template.PigCannonTemplate;
import dev.selestor1462.cannonencoder.template.RelativeBlockPos;
import dev.selestor1462.cannonencoder.template.SlotPattern;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class EncodingValidator {
    private EncodingValidator() {
    }

    public static ValidationReport validate(
        Level world,
        TemplateRegistry templateRegistry,
        SessionState state
    ) {
        if (world == null || state == null || state.anchor() == null || state.anchor().pos().getY() <= 0 || state.lastCalculation() == null) {
            return new ValidationReport(0, 0, 0, List.of(
                EncodingError.error(BlockPos.ZERO, EncodingError.Type.MISSING_OBSERVER, "Cannot validate: cannon anchor (Y <= 0) or calculation is missing.")
            ));
        }

        AnchorState anchor = state.anchor();
        CalculationResult calc = state.lastCalculation();
        int level = state.selectedRow();
        int column = state.selectedCol();

        Optional<HighlightPlan> planOpt = templateRegistry.resolve(anchor, calc, level, column);
        if (planOpt.isEmpty()) {
            return new ValidationReport(level, column, 0, List.of(
                EncodingError.error(anchor.pos(), EncodingError.Type.MISSING_OBSERVER, "Template '" + anchor.templateId() + "' failed to resolve.")
            ));
        }

        HighlightPlan plan = planOpt.get();
        List<EncodingError> errors = new ArrayList<>();
        int checkedCount = 0;

        java.util.Set<BlockPos> plannedTargetObservers = new java.util.HashSet<>();
        java.util.Set<BlockPos> plannedAllPositions = new java.util.HashSet<>();
        for (HighlightedBlock hb : plan.blocks()) {
            plannedAllPositions.add(hb.pos());
            if (hb.type() == HighlightType.TARGET_OBSERVER) {
                plannedTargetObservers.add(hb.pos());
            }
        }

        for (HighlightedBlock hb : plan.blocks()) {
            checkedCount++;
            BlockPos pos = hb.pos();
            BlockState actual = world.getBlockState(pos);

            switch (hb.type()) {
                case OBSERVER -> {
                    if (!actual.is(Blocks.OBSERVER)) {
                        errors.add(EncodingError.error(
                            pos,
                            EncodingError.Type.MISSING_OBSERVER,
                            "Missing observer for " + hb.slot().displayName() + " bit " + hb.bitIndex()
                        ));
                    } else if (hb.facing() != null) {
                        Direction actualFacing = actual.getValue(ObserverBlock.FACING);
                        if (actualFacing != hb.facing()) {
                            errors.add(EncodingError.error(
                                pos,
                                EncodingError.Type.OBSERVER_WRONG_FACING,
                                "Observer facing " + actualFacing + ", expected " + hb.facing() + " (" + hb.slot().displayName() + ")"
                            ));
                        }
                    }
                }
                case GLASS -> {
                    if (!isGlass(actual) && !actual.is(Blocks.OBSERVER)) {
                        errors.add(EncodingError.error(
                            pos,
                            EncodingError.Type.MISSING_GLASS,
                            "Missing glass bridge block"
                        ));
                    }
                }
                case RAIL -> {
                    if (!(actual.getBlock() instanceof BaseRailBlock)) {
                        errors.add(EncodingError.error(
                            pos,
                            EncodingError.Type.MISSING_RAIL,
                            "Missing rail on bridge"
                        ));
                    }
                }
                case TARGET_OBSERVER -> {
                    if (!actual.is(Blocks.OBSERVER)) {
                        errors.add(EncodingError.error(
                            pos,
                            EncodingError.Type.TARGET_DAMAGED,
                            "Cannon input observer under stained glass is missing!"
                        ));
                    }
                }
                case STAINED_GLASS -> {
                    if (actual.isAir()) {
                        errors.add(EncodingError.warning(
                            pos,
                            EncodingError.Type.TARGET_DAMAGED,
                            "Stained glass marker block is missing"
                        ));
                    }
                }
                default -> {}
            }
        }

        // Check active row along Z axis in Pig Cannon for unexpected/extra observers
        PigCannonTemplate template = templateRegistry.getTemplate(anchor.templateId());
        if (template == null) {
            template = templateRegistry.getTemplate(TemplateRegistry.CANNON_V1_DEFAULT);
        }
        if (template != null) {
            DirectionTemplate dirTemplate = template.directions().get(calc.directionCode());
            if (dirTemplate != null) {
                int clampedCol = Math.max(0, Math.min(8, column));
                int clampedLevel = Math.max(0, level);
                BlockPos fixedAnchor = DirectionTemplate.resolveFixedAnchor(world, anchor.pos());
                int distanceToTarget = 9 - clampedCol;

                for (SlotPattern pattern : dirTemplate.slotPatterns().values()) {
                    int value = calc.slotValue(pattern.slot());
                    for (int b = 0; b < pattern.width(); ++b) {
                        int globalBitIdx = pattern.bitOffset() + b;
                        boolean bitSet = ((value >> b) & 1) == 1;

                        RelativeBlockPos stainedGlassRel = pattern.start()
                            .add(pattern.step().multiply(b))
                            .add(new RelativeBlockPos(0, 4 * clampedLevel, 0));

                        // Only validate the active column (clampedCol) along the Z axis.
                        // Never scan along the X axis, as other X columns contain other legitimate encodings!
                        if (!bitSet) {
                            RelativeBlockPos slotObserverRel = stainedGlassRel
                                .add(pattern.bridgeVec().multiply(-distanceToTarget))
                                .add(new RelativeBlockPos(0, -2, 0));

                            BlockPos basePos = slotObserverRel.addTo(fixedAnchor);
                            checkedCount++;
                            if (world.hasChunkAt(basePos)) {
                                BlockState baseState = world.getBlockState(basePos);
                                if (baseState.is(Blocks.OBSERVER) && !plannedTargetObservers.contains(basePos) && !plannedAllPositions.contains(basePos)) {
                                    errors.add(EncodingError.error(
                                        basePos,
                                        EncodingError.Type.UNEXPECTED_OBSERVER,
                                        "Extra observer in slot for " + pattern.slot().displayName() + " bit " + globalBitIdx
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }

        return new ValidationReport(level, column, checkedCount, errors);
    }

    private static boolean isGlass(BlockState actual) {
        return actual.is(Blocks.GLASS)
            || actual.getBlock() instanceof StainedGlassBlock
            || actual.getBlock() instanceof TintedGlassBlock;
    }

    private static int getBitInSlot(CodingSlot slot, int bitIndex) {
        return bitIndex;
    }
}
