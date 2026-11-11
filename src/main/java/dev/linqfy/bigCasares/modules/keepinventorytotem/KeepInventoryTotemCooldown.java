package dev.linqfy.bigCasares.modules.keepinventorytotem;

/** Pure cooldown arithmetic for the totem, kept apart from Bukkit so it is unit-testable. */
final class KeepInventoryTotemCooldown {

    private KeepInventoryTotemCooldown() {
    }

    /** Milliseconds left before the totem can fire again; zero when it is ready or was never used. */
    static long remainingMillis(Long lastUseMillis, long nowMillis, long cooldownMillis) {
        if (lastUseMillis == null || cooldownMillis <= 0L) {
            return 0L;
        }
        long elapsed = nowMillis - lastUseMillis;
        if (elapsed < 0L) {
            // Clock went backwards: treat the use as just made rather than unlocking early.
            return cooldownMillis;
        }
        return Math.max(0L, cooldownMillis - elapsed);
    }

    /** Spanish short form such as {@code 3h 05m} or {@code 12m 40s}. */
    static String format(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + String.format("%02dm", minutes);
        }
        if (minutes > 0L) {
            return minutes + "m " + String.format("%02ds", seconds);
        }
        return seconds + "s";
    }
}
