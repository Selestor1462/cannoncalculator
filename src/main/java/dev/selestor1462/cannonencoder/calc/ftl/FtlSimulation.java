package dev.selestor1462.cannonencoder.calc.ftl;

public record FtlSimulation(
    Vec3d pos,
    Vec3d motion,
    int ticks,
    int upaccelTnt,
    boolean longRange
) {
    public static FtlSimulation create(
        Vec3d pos,
        Vec3d initialMotion,
        int ticks,
        int upaccelTnt,
        boolean requestedLongRange
    ) {
        boolean longRange = requestedLongRange && upaccelTnt > 0;
        double tntY = longRange
            ? FtlCalculator.computeUpaccelTntY(FtlConstants.UPACCEL_TNT_LONGRANGE_START_Y)
            : FtlCalculator.computeUpaccelTntY(FtlConstants.UPACCEL_TNT_START_Y);

        FtlTnt tnt = new FtlTnt(
            new Vec3d(
                pos.x() - FtlConstants.PEARL_HORIZONTAL_OFFSET,
                tntY,
                pos.z() - FtlConstants.PEARL_HORIZONTAL_OFFSET
            ),
            Vec3d.ZERO
        );

        Vec3d upaccelVel = tnt
            .calcVelocityFromExplosion(pos, FtlConstants.PEARL_EYE_HEIGHT, 1.0, false)
            .multiply(upaccelTnt);

        Vec3d finalMotion = initialMotion.add(upaccelVel);
        return new FtlSimulation(pos, finalMotion, ticks, upaccelTnt, longRange);
    }

    public FtlPearl getEnd() {
        FtlPearl pearl = new FtlPearl(this.pos, this.motion);
        for (int i = 0; i < this.ticks; i++) {
            pearl.tick();
        }
        return pearl;
    }
}
