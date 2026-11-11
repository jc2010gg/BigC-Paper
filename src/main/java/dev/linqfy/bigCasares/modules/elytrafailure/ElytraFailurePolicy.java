package dev.linqfy.bigCasares.modules.elytrafailure;

/** Pure rules of the failure, kept apart from Bukkit so they are unit-testable. */
final class ElytraFailurePolicy {

    private final ElytraFailureSettings settings;

    ElytraFailurePolicy(ElytraFailureSettings settings) {
        this.settings = settings;
    }

    boolean shouldFail(long glidingTicks, double roll) {
        return glidingTicks >= settings.minFlightTicks() && roll < settings.chancePerCheck();
    }

    /** New damage value after the wing breaks; never fully destroys the elytra, always leaves it repairable. */
    int damageAfterFailure(int currentDamage, int maxDamage) {
        if (maxDamage <= 1) {
            return currentDamage;
        }
        int loss = Math.max(1, (int) Math.round(maxDamage * settings.durabilityLossFraction()));
        return Math.min(maxDamage - 1, Math.max(0, currentDamage) + loss);
    }

    /**
     * Horizontal unit vector pointing to the broken side of a player looking along {@code (dirX, dirZ)}.
     * Minecraft's X grows east and Z south, so the right of north {@code (0, -1)} is east {@code (1, 0)}.
     */
    static double[] sideways(double dirX, double dirZ, ElytraWing wing) {
        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < 1.0E-6) {
            return new double[] {0.0, 0.0};
        }
        double x = dirX / length;
        double z = dirZ / length;
        return wing == ElytraWing.RIGHT ? new double[] {-z, x} : new double[] {z, -x};
    }
}
