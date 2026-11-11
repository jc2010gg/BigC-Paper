package dev.linqfy.bigCasares.modules.schizophrenia;

/** Pure timing rules: how bad an episode has become and how often it strikes. */
final class EpisodeMath {

    static final int MAX_INTENSITY = 5;

    private EpisodeMath() {
    }

    static int intensity(int startIntensity, long elapsedMillis, double minutesPerIntensity) {
        int base = clampIntensity(startIntensity);
        if (elapsedMillis <= 0) {
            return base;
        }
        double minutes = elapsedMillis / 60_000.0;
        return clampIntensity(base + (int) Math.floor(minutes / minutesPerIntensity));
    }

    static int clampIntensity(int intensity) {
        return Math.max(1, Math.min(MAX_INTENSITY, intensity));
    }

    /** Seconds until the next beat: slowest when tension is 0, fastest at full tension, jitter in [0,1). */
    static double beatSeconds(double tension, int slowest, int fastest, double jitter) {
        double progress = Math.max(0.0, Math.min(1.0, tension));
        double base = slowest + (fastest - slowest) * progress;
        double spread = 0.6 + 0.8 * Math.max(0.0, Math.min(1.0, jitter));
        return Math.max(1.0, base * spread);
    }

    static boolean expired(long elapsedMillis, int maxEpisodeMinutes) {
        return maxEpisodeMinutes > 0 && elapsedMillis >= maxEpisodeMinutes * 60_000L;
    }

    /** How many hallucinations land on one beat: one, a second only at peak tension late in an episode. */
    static int stackedBeats(double tension, int intensity) {
        return clampIntensity(intensity) >= 4 && tension >= 0.8 ? 2 : 1;
    }

    /** A dread phase pays off with exactly one scare: a slow burn, not a barrage. */
    static int scaresInCombo(int intensity) {
        return 1;
    }

    /** Sanity shown on the bar: drops with how long it has gone untreated and spikes down with tension. */
    static float sanity(int intensity, double tension) {
        double value = 1.0 - (clampIntensity(intensity) - 1) * 0.12 - Math.max(0.0, Math.min(1.0, tension)) * 0.4;
        return (float) Math.max(0.02, Math.min(1.0, value));
    }
}
