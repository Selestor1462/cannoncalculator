package dev.selestor1462.cannonencoder.calc.ftl;

public final class FtlConstants {
    private FtlConstants() {}

    public static final double PEARL_STOP_HEIGHT = 256.0;
    public static final int NUM_OF_ANGLES = 4;

    public static final double PEARL_EYE_HEIGHT = 0.25 * ((double) 0.85f);
    public static final double EXPLOSION_HEIGHT = ((double) 0.98f) * ((double) 0.0625f);

    public static final double BASKET_TNT_Y = 173.875 - ((double) 0.98f);
    public static final int BASKET_UPACCEL_TNT = 159;
    public static final double BASKET_UPACCEL_TNT_Y = 169.0;
    public static final double ALIGNMENT_TNT_Y = 172.79375;
    public static final double ALIGNMENT_TNT_OFFSET = 1.8125;

    public static final double PEARL_Y = 256.22376922490804;
    public static final double PEARL_Y_MOTION = -0.03338941371413851;
    public static final double PEARL_HORIZONTAL_OFFSET = -(((double) 0.51f) - 0.51);

    public static final int MAX_UPACCEL_TNT = 31;
    // Minecraft 26.2+ uses 32-bit float drag precision cast to double
    public static final double PEARL_DRAG = (double) 0.99f;
    public static final double TNT_DRAG = (double) 0.98f;

    public static final double UPACCEL_TNT_START_Y = 177.625;
    public static final double UPACCEL_TNT_LONGRANGE_START_Y = 177.5;

    public static final int DEFAULT_MAX_TNT = 6688;
    public static final int DEFAULT_MAX_TICKS = 500;
    public static final double DEFAULT_MAX_DISTANCE = 50.0;
}
