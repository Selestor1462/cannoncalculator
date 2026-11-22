package dev.selestor1462.cannonencoder.calc.ftl;

public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    public Vec3d add(Vec3d other) {
        return new Vec3d(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vec3d multiply(double scalar) {
        return new Vec3d(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public double distanceTo(Vec3d other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double dz = other.z - this.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double lengthHorizontal() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public Vec3d normalized() {
        double len = this.length();
        if (len == 0.0) return ZERO;
        return this.multiply(1.0 / len);
    }

    public String pretty() {
        return String.format("(%.3f, %.3f, %.3f)", this.x, this.y, this.z);
    }

    public String asInt() {
        return String.format("%d %d %d", (int) Math.floor(this.x), (int) Math.floor(this.y), (int) Math.floor(this.z));
    }
}
