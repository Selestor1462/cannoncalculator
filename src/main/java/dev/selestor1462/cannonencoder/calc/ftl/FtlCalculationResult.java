package dev.selestor1462.cannonencoder.calc.ftl;

public record FtlCalculationResult(
    int earlyTnt,
    int lateTnt,
    int totalTnt,
    double distance,
    int ticks,
    Vec3d endPos,
    Vec3d simPos,
    Vec3d simMotion,
    int upaccelTnt,
    boolean longRange,
    int direction,
    int directionAngle,
    Vec3d catcherPos,
    FtlEncodingData encodingData
) {
}
