package dev.selestor1462.cannonencoder.calc.ftl;

public final class FtlPearl {
    private Vec3d pos;
    private Vec3d motion;

    public FtlPearl(Vec3d pos, Vec3d motion) {
        this.pos = pos;
        this.motion = motion;
    }

    public Vec3d pos() {
        return this.pos;
    }

    public Vec3d motion() {
        return this.motion;
    }

    public void tick() {
        this.motion = this.motion.add(new Vec3d(0.0, -0.03, 0.0));
        this.motion = this.motion.multiply(FtlConstants.PEARL_DRAG);
        this.pos = this.pos.add(this.motion);
    }

    public FtlPearl copy() {
        return new FtlPearl(this.pos, this.motion);
    }
}
