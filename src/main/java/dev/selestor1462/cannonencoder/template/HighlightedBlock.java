package dev.selestor1462.cannonencoder.template;

import dev.selestor1462.cannonencoder.calc.CodingSlot;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record HighlightedBlock(
    BlockPos pos,
    CodingSlot slot,
    HighlightType type,
    Direction facing,
    int bitIndex,
    int level,
    int column,
    int customColor
) {
    public HighlightedBlock {
        pos = Objects.requireNonNull(pos, "pos");
        slot = Objects.requireNonNull(slot, "slot");
        type = type != null ? type : HighlightType.OBSERVER;
    }

    public HighlightedBlock(BlockPos pos, CodingSlot slot, HighlightType type, Direction facing, int bitIndex, int level, int column) {
        this(pos, slot, type, facing, bitIndex, level, column, 0);
    }

    public HighlightedBlock(BlockPos pos, CodingSlot slot) {
        this(pos, slot, HighlightType.OBSERVER, Direction.DOWN, 0, 0, 0, 0);
    }
}
