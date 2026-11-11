package dev.linqfy.bigCasares.modules.schizophrenia;

import java.util.random.RandomGenerator;

/**
 * The horror-game director: quiet stretches, dread that builds, one scare that pays it off, and a
 * moment of dead silence before it starts again. Worse episodes shorten the quiet parts.
 */
final class HorrorPacing {

    enum Phase { CALM, DREAD, SCARE, AFTERMATH }

    private Phase phase = Phase.CALM;
    private long phaseStartedAt;
    private long phaseEndsAt;

    HorrorPacing(long now, int intensity, RandomGenerator random) {
        enter(Phase.CALM, now, intensity, random);
        // A fresh episode starts already uneasy rather than with a full quiet stretch.
        phaseEndsAt = now + (phaseEndsAt - now) / 2;
    }

    Phase phase() {
        return phase;
    }

    /** Moves to the next phase when the current one ran out; returns true on a phase change. */
    boolean advance(long now, int intensity, RandomGenerator random) {
        if (now < phaseEndsAt) {
            return false;
        }
        Phase next = switch (phase) {
            case CALM -> Phase.DREAD;
            case DREAD -> Phase.SCARE;
            case SCARE -> Phase.AFTERMATH;
            case AFTERMATH -> Phase.CALM;
        };
        enter(next, now, intensity, random);
        return true;
    }

    /** Jumps straight into dread, e.g. when an admin provokes an episode. */
    void forceDread(long now, int intensity, RandomGenerator random) {
        enter(Phase.DREAD, now, intensity, random);
    }

    /** 0 = safe, 1 = something is about to happen. Rises through dread, peaks at the scare, decays after. */
    double tension(long now) {
        double progress = phaseEndsAt <= phaseStartedAt ? 1.0
            : Math.max(0.0, Math.min(1.0, (now - phaseStartedAt) / (double) (phaseEndsAt - phaseStartedAt)));
        return switch (phase) {
            case CALM -> 0.05 + 0.1 * progress;
            case DREAD -> 0.2 + 0.75 * progress;
            case SCARE -> 1.0;
            case AFTERMATH -> 0.6 - 0.45 * progress;
        };
    }

    static long durationTicks(Phase phase, int intensity, double jitter) {
        int level = EpisodeMath.clampIntensity(intensity) - 1;
        double seconds = switch (phase) {
            case CALM -> 75 - level * 10.0;
            case DREAD -> 35 - level * 3.0;
            case SCARE -> 6 + level;
            case AFTERMATH -> 15 - level * 2.0;
        };
        double spread = phase == Phase.SCARE ? 1.0 : 0.75 + 0.5 * Math.max(0.0, Math.min(1.0, jitter));
        return Math.max(20L, Math.round(seconds * spread * 20.0));
    }

    private void enter(Phase next, long now, int intensity, RandomGenerator random) {
        phase = next;
        phaseStartedAt = now;
        phaseEndsAt = now + durationTicks(next, intensity, random.nextDouble());
    }
}
