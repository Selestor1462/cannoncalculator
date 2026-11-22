package dev.selestor1462.cannonencoder.calc.ftl;

public final class FtlTnt {
    private Vec3d pos;
    private Vec3d motion;

    public FtlTnt(Vec3d pos, Vec3d motion) {
        this.pos = pos;
        this.motion = motion;
    }

    public Vec3d pos() {
        return this.pos;
    }

    public Vec3d motion() {
        return this.motion;
    }

    public void setMotion(Vec3d motion) {
        this.motion = motion;
    }

    public void tick() {
        this.motion = this.motion.add(new Vec3d(0.0, -0.04, 0.0));
        this.pos = this.pos.add(this.motion);
        this.motion = this.motion.multiply(FtlConstants.TNT_DRAG);
    }

    public Vec3d calcVelocityFromExplosion(Vec3d entityPos, double eyeHeight, double exposure, boolean isTnt) {
        Vec3d explosionPos = this.pos.add(new Vec3d(0.0, FtlConstants.EXPLOSION_HEIGHT, 0.0));
        double distNorm = explosionPos.distanceTo(entityPos) / 8.0;
        if (distNorm > 1.0) {
            return Vec3d.ZERO;
        }

        double entityY = isTnt ? entityPos.y() : entityPos.y() + eyeHeight;
        Vec3d dir = new Vec3d(
            entityPos.x() - explosionPos.x(),
            entityY - explosionPos.y(),
            entityPos.z() - explosionPos.z()
        );
        double len = dir.length();
        if (len == 0.0) {
            return Vec3d.ZERO;
        }

        return dir.multiply(1.0 / len).multiply((1.0 - distNorm) * exposure);
    }
}
