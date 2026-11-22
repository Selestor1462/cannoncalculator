package dev.selestor1462.cannonencoder.client.validation;

import net.minecraft.core.BlockPos;

public record EncodingError(
    BlockPos pos,
    String message,
    Type type,
    boolean isError
) {
    public enum Type {
        MISSING_OBSERVER,
        OBSERVER_WRONG_FACING,
        MISSING_GLASS,
        MISSING_RAIL,
        UNEXPECTED_OBSERVER,
        TARGET_DAMAGED,
        LOWER_LEVEL_DAMAGED
    }

    public static EncodingError error(BlockPos pos, Type type, String message) {
        return new EncodingError(pos, message, type, true);
    }

    public static EncodingError warning(BlockPos pos, Type type, String message) {
        return new EncodingError(pos, message, type, false);
    }
}
