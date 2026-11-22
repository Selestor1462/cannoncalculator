package dev.selestor1462.cannonencoder.state;

public enum CannonMode {
    PIG_CANNON("Pig Cannon"),
    FTL_PEARL_CANNON("836 FTL Pearl Cannon");

    private final String displayName;

    CannonMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
