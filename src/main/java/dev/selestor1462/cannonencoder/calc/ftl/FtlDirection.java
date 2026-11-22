package dev.selestor1462.cannonencoder.calc.ftl;

public record FtlDirection(int direction, int angle) {
    public static final int WEST_NORTH_WEST = 0;
    public static final int EAST_NORTH_EAST = 1;
    public static final int NORTH_NORTH_WEST = 2;
    public static final int NORTH_NORTH_EAST = 3;
    public static final int SOUTH_SOUTH_EAST = 4;
    public static final int SOUTH_SOUTH_WEST = 5;
    public static final int EAST_SOUTH_EAST = 6;
    public static final int WEST_SOUTH_WEST = 7;

    public static FtlDirection calculate(Vec3d vec) {
        double cos45 = Math.cos(Math.toRadians(45.0));
        double sin45 = Math.sin(Math.toRadians(45.0));
        double rotatedX = vec.x() * cos45 - vec.z() * sin45;
        double rotatedZ = vec.x() * sin45 + vec.z() * cos45;
        double scale = (double) FtlConstants.NUM_OF_ANGLES / Math.max(Math.abs(rotatedX), Math.abs(rotatedZ));
        int angle = (int) Math.floor(Math.min(Math.abs(rotatedX * scale), Math.abs(rotatedZ * scale)));

        double vecAngle = Math.toDegrees(Math.atan2(vec.z(), vec.x()));
        if (vecAngle < 0.0) {
            vecAngle += 360.0;
        }

        int dir;
        if (vecAngle < 45.0) {
            dir = EAST_SOUTH_EAST;
        } else if (vecAngle < 90.0) {
            dir = SOUTH_SOUTH_EAST;
        } else if (vecAngle < 135.0) {
            dir = SOUTH_SOUTH_WEST;
        } else if (vecAngle < 180.0) {
            dir = WEST_SOUTH_WEST;
        } else if (vecAngle < 225.0) {
            dir = WEST_NORTH_WEST;
        } else if (vecAngle < 270.0) {
            dir = NORTH_NORTH_WEST;
        } else if (vecAngle < 315.0) {
            dir = NORTH_NORTH_EAST;
        } else {
            dir = EAST_NORTH_EAST;
        }

        return new FtlDirection(dir, angle);
    }
}
