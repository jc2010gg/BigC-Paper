package dev.linqfy.bigCasares.modules.schizophrenia;

/**
 * When an episode may start by itself. It is meant to be rare: a hidden stress level only builds
 * while a player is alone in the dark or deep underground, builds faster without sleep, at night or
 * when hurt, and drains as soon as they are with people, in the light or asleep. Even at the top it
 * only starts after days without sleep, outside every cooldown, with a final roll of the dice.
 */
final class OnsetPolicy {

    static final long TICKS_PER_DAY = 24_000L;

    /** What the player is going through right now. */
    record Conditions(boolean alone, int lightLevel, double y, boolean night, long ticksSinceRest,
                      double health, boolean sleeping) {
    }

    private OnsetPolicy() {
    }

    static int stressDelta(Conditions now, SchizophreniaSettings.Natural natural) {
        if (now.sleeping() || !now.alone()) {
            return -5;
        }
        boolean dark = now.lightLevel() <= natural.maxLightLevel();
        boolean deep = now.y() < natural.deepBelowY();
        if (!dark && !deep) {
            return -2;
        }
        int delta = 1;
        if (dark && deep) {
            delta++;
        }
        if (now.night()) {
            delta++;
        }
        if (insomniac(now.ticksSinceRest(), natural.insomniaDays())) {
            delta += 2;
        }
        if (now.health() <= 6.0) {
            delta++;
        }
        return delta;
    }

    static int applyStress(int stress, int delta, SchizophreniaSettings.Natural natural) {
        return Math.max(0, Math.min(natural.stressThreshold() * 2, stress + delta));
    }

    static boolean insomniac(long ticksSinceRest, int insomniaDays) {
        return insomniaDays <= 0 || ticksSinceRest >= insomniaDays * TICKS_PER_DAY;
    }

    /** Everything except the final dice roll. */
    static boolean mayStart(
        int stress, long ticksSinceRest, long nowMillis, Long lastPlayerOnsetMillis, long lastServerOnsetMillis,
        int activeNaturalEpisodes, SchizophreniaSettings.Natural natural
    ) {
        if (stress < natural.stressThreshold() || !insomniac(ticksSinceRest, natural.insomniaDays())) {
            return false;
        }
        if (activeNaturalEpisodes >= natural.maxConcurrent()) {
            return false;
        }
        if (nowMillis - lastServerOnsetMillis < natural.serverCooldownMinutes() * 60_000L) {
            return false;
        }
        return lastPlayerOnsetMillis == null
            || nowMillis - lastPlayerOnsetMillis >= natural.playerCooldownHours() * 3_600_000L;
    }
}
