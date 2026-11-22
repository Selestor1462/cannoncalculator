package dev.selestor1462.cannonencoder.calc;

public enum CodingSlot {
    // Pig Cannon Slots
    DIRECTION("Direction"),
    FIRST_ACCEL("First Accel"),
    SECOND_ACCEL("Second Accel"),
    THIRD_ACCEL("Third Accel"),
    FOURTH_ACCEL("Fourth Accel"),

    // 836 FTL Pearl Cannon Slots
    FTL_ANGLE("Angle"),
    FTL_VARIABLE("Variable"),
    FTL_RED_SMALL("Pink"),
    FTL_RED_BIG("Red"),
    FTL_RED_MEDIUM("Orange"),
    FTL_DIRECTION("Direction / Mode"),
    FTL_BLUE_MEDIUM("Lime"),
    FTL_BLUE_BIG("Light Blue"),
    FTL_BLUE_SMALL("Cyan"),
    FTL_UPACCEL_BIG("Blue"),
    FTL_UPACCEL_SMALL("Purple"),
    FTL_CATCHER("Catcher Block");

    private final String displayName;

    private CodingSlot(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
