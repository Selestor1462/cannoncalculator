package dev.selestor1462.cannonencoder.state;

public record TargetRecord(
    long destX,
    long destZ,
    CannonMode mode,
    int row,
    int col,
    double distance,
    long timestamp,
    String label
) {
    public TargetRecord(long destX, long destZ, CannonMode mode, int row, int col, double distance, long timestamp) {
        this(destX, destZ, mode, row, col, distance, timestamp, "");
    }

    public static TargetRecord pig(long destX, long destZ, int row, int col, double distance) {
        return new TargetRecord(destX, destZ, CannonMode.PIG_CANNON, row, col, distance, System.currentTimeMillis(), "");
    }

    public TargetRecord withLabel(String newLabel) {
        return new TargetRecord(destX, destZ, mode, row, col, distance, timestamp, newLabel != null ? newLabel.trim() : "");
    }

    public String displayLabel() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        return String.format(
            java.util.Locale.ROOT,
            "[%s] X=%d, Z=%d (%.0fb, L%d C%d)",
            mode == CannonMode.FTL_PEARL_CANNON ? "FTL" : "Pig",
            destX,
            destZ,
            distance,
            row,
            col + 1
        );
    }
}
