package dev.selestor1462.cannonencoder.template;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.CodingSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SlotPattern(
    CodingSlot slot,
    RelativeBlockPos start,
    RelativeBlockPos step,
    RelativeBlockPos bridgeVec,
    Direction targetObserverFacing,
    int width,
    int bitOffset
) {
    public SlotPattern {
        slot = Objects.requireNonNull(slot, "slot");
        start = Objects.requireNonNull(start, "start");
        step = Objects.requireNonNull(step, "step");
        bridgeVec = Objects.requireNonNull(bridgeVec, "bridgeVec");
        targetObserverFacing = Objects.requireNonNull(targetObserverFacing, "targetObserverFacing");
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0, got " + width);
        }
    }

    public List<HighlightedBlock> resolve(BlockPos anchor, CalculationResult result, int level, int column) {
        int value = result.slotValue(this.slot);
        ArrayList<HighlightedBlock> blocks = new ArrayList<>();
        int clampedCol = Math.max(0, Math.min(8, column));
        int clampedLevel = Math.max(0, level);
        int distanceToTarget = 9 - clampedCol;

        for (int b = 0; b < this.width; ++b) {
            int globalBitIdx = this.bitOffset + b;
            // Target stained glass position at current level
            RelativeBlockPos stainedGlassRel = this.start
                .add(this.step.multiply(b))
                .add(new RelativeBlockPos(0, 4 * clampedLevel, 0));

            // Target observer under stained glass (at x=31 in rot0)
            RelativeBlockPos targetObserverRel = stainedGlassRel.add(new RelativeBlockPos(0, -1, 0));

            // Stained glass block highlight
            blocks.add(new HighlightedBlock(
                stainedGlassRel.addTo(anchor),
                this.slot,
                HighlightType.STAINED_GLASS,
                Direction.UP,
                globalBitIdx,
                clampedLevel,
                clampedCol
            ));

            // Target observer highlight under stained glass
            blocks.add(new HighlightedBlock(
                targetObserverRel.addTo(anchor),
                this.slot,
                HighlightType.TARGET_OBSERVER,
                this.targetObserverFacing,
                globalBitIdx,
                clampedLevel,
                clampedCol
            ));

            // If bit is 1, place observer looking at column rail and glass+rail bridge
            if ((value >> b & 1) == 1) {
                // Placed observer at selected column (looking down into column rail at dy - 3)
                RelativeBlockPos slotObserverRel = stainedGlassRel
                    .add(this.bridgeVec.multiply(-distanceToTarget))
                    .add(new RelativeBlockPos(0, -2, 0));

                blocks.add(new HighlightedBlock(
                    slotObserverRel.addTo(anchor),
                    this.slot,
                    HighlightType.OBSERVER,
                    Direction.DOWN,
                    globalBitIdx,
                    clampedLevel,
                    clampedCol
                ));

                // Glass bridge from slotObserver + 1 towards target
                for (int k = 1; k < distanceToTarget; ++k) {
                    RelativeBlockPos glassRel = slotObserverRel.add(this.bridgeVec.multiply(k));
                    blocks.add(new HighlightedBlock(
                        glassRel.addTo(anchor),
                        this.slot,
                        HighlightType.GLASS,
                        Direction.UP,
                        globalBitIdx,
                        clampedLevel,
                        clampedCol
                    ));
                }

                // Rails on top of the observer and glass bridge
                Direction railFacing = (this.bridgeVec.x() != 0) ? Direction.EAST : Direction.NORTH;
                for (int k = 0; k < distanceToTarget; ++k) {
                    RelativeBlockPos railRel = slotObserverRel
                        .add(this.bridgeVec.multiply(k))
                        .add(new RelativeBlockPos(0, 1, 0));

                    blocks.add(new HighlightedBlock(
                        railRel.addTo(anchor),
                        this.slot,
                        HighlightType.RAIL,
                        railFacing,
                        globalBitIdx,
                        clampedLevel,
                        clampedCol
                    ));
                }
            }
        }
        return List.copyOf(blocks);
    }
}
