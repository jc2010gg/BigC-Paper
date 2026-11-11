package dev.linqfy.bigCasares.modules.casino.roulette;

/**
 * Kinematics of one spin, decided before the ball is launched. The winning pocket is drawn first and the
 * ball's path is solved backwards so it comes to rest exactly on that pocket: what everybody watches is the
 * real result, never a decoration over a different number.
 *
 * <p>Angles are radians. The wheel turns one way and slows down; the ball runs the other way along the rim,
 * drops to the pocket ring, bounces a few times and then rides with the wheel.
 */
public final class SpinPlan {

    private static final double DROP_START = 0.62;
    private static final double DROP_END = 0.82;
    private static final double SETTLE = 0.93;

    private final int result;
    private final int durationTicks;
    private final double wheelStart;
    private final double wheelTravel;
    private final double ballStart;
    private final double ballTravel;

    public SpinPlan(int result, int durationTicks, double wheelStart, double ballStart, int wheelTurns, int ballTurns) {
        if (durationTicks < 20) {
            throw new IllegalArgumentException("A spin lasts at least one second");
        }
        RoulettePockets.indexOf(result);
        this.result = result;
        this.durationTicks = durationTicks;
        this.wheelStart = wheelStart;
        this.wheelTravel = Math.PI * 2 * wheelTurns;
        this.ballStart = ballStart;
        // The ball's angle relative to the wheel must end on the pocket centre.
        double relativeStart = ballStart - wheelStart;
        double relativeEnd = RoulettePockets.angleOf(result);
        double backwards = mod(relativeStart - relativeEnd);
        // The ball runs the opposite way. Whole wheel turns do not change the relative angle, so the ball's
        // travel only has to cover the backwards gap plus its own laps.
        this.ballTravel = -(backwards + Math.PI * 2 * ballTurns);
    }

    public int result() {
        return result;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public double wheelAngle(int tick) {
        return wheelStart + wheelTravel * ease(progress(tick));
    }

    public double ballAngle(int tick) {
        return ballStart + ballTravel * ease(progress(tick));
    }

    /** Ball angle relative to the wheel, in [0, 2π). */
    public double relativeBallAngle(int tick) {
        return mod(ballAngle(tick) - wheelAngle(tick));
    }

    /** Pocket under the ball at {@code tick}. */
    public int pocketUnderBall(int tick) {
        int index = (int) Math.floor(mod(relativeBallAngle(tick) + RoulettePockets.pocketWidth() / 2)
            / RoulettePockets.pocketWidth()) % RoulettePockets.COUNT;
        return RoulettePockets.WHEEL_ORDER.get(index);
    }

    /** 1.0 on the outer rim, 0.0 on the pocket ring. */
    public double ballRadiusFactor(int tick) {
        double u = progress(tick);
        if (u <= DROP_START) {
            return 1.0;
        }
        if (u >= DROP_END) {
            return 0.0;
        }
        double t = (u - DROP_START) / (DROP_END - DROP_START);
        return 1.0 - t * t * (3 - 2 * t);
    }

    /** Bounce height of the ball in blocks, scaled by {@code amplitude}. */
    public double ballHeight(int tick, double amplitude) {
        double u = progress(tick);
        if (u < DROP_START || u > SETTLE) {
            return 0.0;
        }
        double t = (u - DROP_START) / (SETTLE - DROP_START);
        return Math.abs(Math.sin(t * Math.PI * 4)) * amplitude * (1.0 - t);
    }

    public boolean finished(int tick) {
        return tick >= durationTicks;
    }

    private double progress(int tick) {
        return Math.max(0.0, Math.min(1.0, tick / (double) durationTicks));
    }

    /** Cubic ease-out: fast start, gentle stop. */
    static double ease(double u) {
        double inverse = 1.0 - u;
        return 1.0 - inverse * inverse * inverse;
    }

    static double mod(double angle) {
        double full = Math.PI * 2;
        double value = angle % full;
        return value < 0 ? value + full : value;
    }
}
