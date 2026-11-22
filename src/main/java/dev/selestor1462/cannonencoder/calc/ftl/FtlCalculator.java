package dev.selestor1462.cannonencoder.calc.ftl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FtlCalculator {
    public FtlCalculator() {
    }

    public List<FtlCalculationResult> calculate(
        Vec3d pearlPos,
        Vec3d destPos,
        int maxTnt,
        int maxTicks,
        double maxDistance,
        int cannonMaxTnt
    ) {
        List<FtlCalculationResult> results = new ArrayList<>();

        List<Integer> possibleTicks = new ArrayList<>();
        possibleTicks.addAll(calculatePossibleTicks(computeUpaccelTntY(FtlConstants.UPACCEL_TNT_LONGRANGE_START_Y)));
        possibleTicks.addAll(calculatePossibleTicks(computeUpaccelTntY(FtlConstants.UPACCEL_TNT_START_Y)));

        double divider = 0.0;
        Vec3d distanceExact = destPos.add(pearlPos.multiply(-1.0));
        FtlDirection dir = FtlDirection.calculate(distanceExact);
        Vec3d[] tntVecs = calculateTntVectors(distanceExact, dir);
        Vec3d earlyVec = tntVecs[0];
        Vec3d lateVec = tntVecs[1];

        double denom = earlyVec.z() * lateVec.x() - lateVec.z() * earlyVec.x();
        if (denom == 0.0) {
            return results;
        }

        double earlyTntExact = (distanceExact.z() * lateVec.x() - distanceExact.x() * lateVec.z()) / denom;
        double lateTntExact = (distanceExact.x() - earlyTntExact * earlyVec.x()) / lateVec.x();

        int upaccelTnt = 0;
        boolean longRange = false;
        boolean tryAgain = false;

        for (int tick = 1; tick <= maxTicks; tick++) {
            divider += Math.pow(FtlConstants.PEARL_DRAG, tick);

            int idx = possibleTicks.indexOf(tick);
            if (idx >= 0) {
                upaccelTnt = idx % (FtlConstants.MAX_UPACCEL_TNT + 1);
                longRange = idx <= (FtlConstants.MAX_UPACCEL_TNT + 1);
            } else {
                if (!tryAgain) {
                    continue;
                }
            }
            tryAgain = false;

            int earlyTnt = (int) Math.round(earlyTntExact / divider);
            int lateTnt = (int) Math.round(lateTntExact / divider);

            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    int ea = earlyTnt + a;
                    int lb = lateTnt + b;

                    if (ea < 0 || lb < 0) {
                        continue;
                    }

                    int totalTnt = ea + lb;
                    if (maxTnt >= 0 && totalTnt > maxTnt) {
                        continue;
                    }

                    int maxVariableTnt = cannonMaxTnt - 22;
                    if (ea / 11 + lb / 11 > maxVariableTnt / 11) {
                        continue;
                    }

                    Vec3d initMotion = new Vec3d(0.0, FtlConstants.PEARL_Y_MOTION, 0.0)
                        .add(earlyVec.multiply(ea))
                        .add(lateVec.multiply(lb));

                    FtlSimulation sim = FtlSimulation.create(pearlPos, initMotion, tick, upaccelTnt, longRange);
                    FtlPearl end = sim.getEnd();

                    double dist = end.pos().add(destPos.multiply(-1.0)).lengthHorizontal();
                    if (dist > maxDistance) {
                        continue;
                    }

                    if (end.pos().y() < FtlConstants.PEARL_STOP_HEIGHT) {
                        tryAgain = true;
                        continue;
                    }

                    FtlPearl end2 = end.copy();
                    end2.tick();
                    if (end2.pos().y() >= FtlConstants.PEARL_STOP_HEIGHT) {
                        tryAgain = true;
                        continue;
                    }

                    Vec3d blocker = getPearlBlocker(end);
                    FtlEncodingData enc = FtlEncodingData.compute(
                        ea, lb, sim.upaccelTnt(), sim.longRange(),
                        dir.direction(), dir.angle(), blocker, cannonMaxTnt
                    );

                    results.add(new FtlCalculationResult(
                        ea, lb, totalTnt, dist, tick,
                        end.pos(), sim.pos(), sim.motion(),
                        sim.upaccelTnt(), sim.longRange(),
                        dir.direction(), dir.angle(),
                        blocker, enc
                    ));
                }
            }
        }

        results.sort(Comparator.comparingDouble(FtlCalculationResult::distance));
        return results;
    }

    public static Vec3d[] calculateTntVectors(Vec3d dist, FtlDirection dir) {
        Vec3d firstPos = new Vec3d(
            dist.x() < 0.0 ? -1.0 : 1.0,
            0.0,
            dist.z() < 0.0 ? -1.0 : 1.0
        );
        Vec3d secondPos = firstPos;
        if (Math.abs(dist.x()) > Math.abs(dist.z())) {
            secondPos = new Vec3d(secondPos.x(), secondPos.y(), secondPos.z() * -1.0);
        } else {
            secondPos = new Vec3d(secondPos.x() * -1.0, secondPos.y(), secondPos.z());
        }

        firstPos = firstPos.multiply(FtlConstants.ALIGNMENT_TNT_OFFSET).add(new Vec3d(0.0, FtlConstants.ALIGNMENT_TNT_Y, 0.0));
        secondPos = secondPos.multiply(FtlConstants.ALIGNMENT_TNT_OFFSET).add(new Vec3d(0.0, FtlConstants.ALIGNMENT_TNT_Y, 0.0));

        FtlTnt firstTnt = new FtlTnt(firstPos, Vec3d.ZERO);
        FtlTnt secondTnt = new FtlTnt(secondPos, Vec3d.ZERO);
        FtlTnt basketUpaccelTnt = new FtlTnt(new Vec3d(0.0, FtlConstants.BASKET_UPACCEL_TNT_Y, 0.0), Vec3d.ZERO);

        FtlTnt earlyTnt = new FtlTnt(new Vec3d(0.0, FtlConstants.BASKET_TNT_Y, 0.0), Vec3d.ZERO);
        FtlTnt lateTnt = new FtlTnt(new Vec3d(0.0, FtlConstants.BASKET_TNT_Y, 0.0), Vec3d.ZERO);
        earlyTnt.tick();
        lateTnt.tick();

        double alignExposure = (double) (1.0f / 27.0f);
        double numAngles = (double) FtlConstants.NUM_OF_ANGLES;

        earlyTnt.setMotion(earlyTnt.motion()
            .add(firstTnt.calcVelocityFromExplosion(earlyTnt.pos(), 0.0, alignExposure, true).multiply(numAngles))
            .add(secondTnt.calcVelocityFromExplosion(earlyTnt.pos(), 0.0, alignExposure, true).multiply(dir.angle()))
            .add(basketUpaccelTnt.calcVelocityFromExplosion(earlyTnt.pos(), 0.0, 1.0, true).multiply(FtlConstants.BASKET_UPACCEL_TNT))
        );

        Vec3d earlyPosForBasket = earlyTnt.pos();
        lateTnt.setMotion(lateTnt.motion()
            .add(firstTnt.calcVelocityFromExplosion(lateTnt.pos(), 0.0, alignExposure, true).multiply(numAngles))
            .add(secondTnt.calcVelocityFromExplosion(lateTnt.pos(), 0.0, alignExposure, true).multiply(dir.angle() + 1.0))
            .add(basketUpaccelTnt.calcVelocityFromExplosion(earlyPosForBasket, 0.0, 1.0, true).multiply(FtlConstants.BASKET_UPACCEL_TNT))
        );

        earlyTnt.tick();
        lateTnt.tick();

        Vec3d pearlPos = new Vec3d(
            FtlConstants.PEARL_HORIZONTAL_OFFSET,
            FtlConstants.PEARL_Y,
            FtlConstants.PEARL_HORIZONTAL_OFFSET
        );

        Vec3d earlyVec = earlyTnt.calcVelocityFromExplosion(pearlPos, FtlConstants.PEARL_EYE_HEIGHT, 1.0, false);
        Vec3d lateVec = lateTnt.calcVelocityFromExplosion(pearlPos, FtlConstants.PEARL_EYE_HEIGHT, 1.0, false);

        return new Vec3d[]{earlyVec, lateVec};
    }

    public static double computeUpaccelTntY(double startY) {
        FtlTnt tnt = new FtlTnt(new Vec3d(0.0, startY, 0.0), Vec3d.ZERO);
        for (int i = 0; i < 15; i++) {
            tnt.tick();
        }
        FtlTnt basketTnt = new FtlTnt(new Vec3d(0.0, FtlConstants.BASKET_UPACCEL_TNT_Y, 0.0), Vec3d.ZERO);
        Vec3d vel = basketTnt.calcVelocityFromExplosion(tnt.pos(), 0.0, 1.0, true).multiply(FtlConstants.BASKET_UPACCEL_TNT);
        tnt.setMotion(tnt.motion().add(vel));
        tnt.tick();
        return tnt.pos().y();
    }

    public static List<Integer> calculatePossibleTicks(double upaccelTntY) {
        Vec3d pearlPos = new Vec3d(
            FtlConstants.PEARL_HORIZONTAL_OFFSET,
            FtlConstants.PEARL_Y,
            FtlConstants.PEARL_HORIZONTAL_OFFSET
        );
        Vec3d pearlMotion = new Vec3d(0.0, FtlConstants.PEARL_Y_MOTION, 0.0);

        FtlTnt tnt = new FtlTnt(new Vec3d(0.0, upaccelTntY, 0.0), Vec3d.ZERO);
        Vec3d motionPerTnt = tnt.calcVelocityFromExplosion(pearlPos, FtlConstants.PEARL_EYE_HEIGHT, 1.0, false);

        List<Integer> result = new ArrayList<>(FtlConstants.MAX_UPACCEL_TNT + 1);
        for (int n = 0; n <= FtlConstants.MAX_UPACCEL_TNT; n++) {
            FtlPearl p = new FtlPearl(pearlPos, pearlMotion.add(motionPerTnt.multiply(n)));
            int ticks = 0;
            while (p.pos().y() > FtlConstants.PEARL_STOP_HEIGHT) {
                p.tick();
                ticks++;
            }
            result.add(ticks - 1);
        }
        return result;
    }

    public static Vec3d getPearlBlocker(FtlPearl pearl) {
        Vec3d pos = pearl.pos();
        Vec3d motion = pearl.motion().add(new Vec3d(0.0, -0.03, 0.0)).multiply(FtlConstants.PEARL_DRAG);

        if (motion.y() >= 0.0 || pos.y() < FtlConstants.PEARL_STOP_HEIGHT) {
            return Vec3d.ZERO;
        }

        double t = (FtlConstants.PEARL_STOP_HEIGHT - pos.y()) / motion.y();
        if (t < 0.0 || t > 1.0) {
            return Vec3d.ZERO;
        }

        double x = pos.x() + t * motion.x();
        double z = pos.z() + t * motion.z();
        return new Vec3d(Math.floor(x), FtlConstants.PEARL_STOP_HEIGHT - 1.0, Math.floor(z));
    }
}
