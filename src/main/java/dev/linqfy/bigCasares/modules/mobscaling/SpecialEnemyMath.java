package dev.linqfy.bigCasares.modules.mobscaling;

import org.bukkit.util.Vector;

/** Pure decisions behind the special enemies, kept free of server state so they can be tested. */
final class SpecialEnemyMath {

    /** Vanilla thrown-item gravity per tick (ender pearls, snowballs). */
    static final double PEARL_GRAVITY = 0.03;
    /** Vanilla projectile air drag per tick. */
    static final double PEARL_DRAG = 0.99;
    /** Ignore distance jitter smaller than this when deciding whether a player is running away. */
    private static final double FLEE_EPSILON = 0.25;
    private static final double MAX_PEARL_SPEED = 3.0;

    enum ZombieVariant { SPEED_BABY, TOXIC, RABBIT_LANCER, PEARL_THROWER }

    private SpecialEnemyMath() {
    }

    /**
     * Picks one of four equally likely zombie mutations from {@code roll} (0-3). A disabled special enemy falls
     * back to one of the two original mutations so their odds stay the same as before.
     */
    static ZombieVariant pickZombieVariant(boolean rabbitLancerEnabled, boolean pearlZombieEnabled, int roll) {
        return switch (Math.floorMod(roll, 4)) {
            case 0 -> ZombieVariant.SPEED_BABY;
            case 1 -> ZombieVariant.TOXIC;
            case 2 -> rabbitLancerEnabled ? ZombieVariant.RABBIT_LANCER : ZombieVariant.SPEED_BABY;
            default -> pearlZombieEnabled ? ZombieVariant.PEARL_THROWER : ZombieVariant.TOXIC;
        };
    }

    static boolean rollNuclear(boolean thundering, double chance, double roll) {
        return thundering && chance > 0.0 && roll < chance;
    }

    /** A player flees when the gap to the zombie grows and is still inside the throwing range. */
    static boolean isFleeing(double previousDistance, double currentDistance, double minDistance, double maxDistance) {
        if (Double.isNaN(previousDistance)) {
            return false;
        }
        return currentDistance > previousDistance + FLEE_EPSILON
                && currentDistance >= minDistance
                && currentDistance <= maxDistance;
    }

    /**
     * Point ahead of the player where the pearl should land: {@code lead} blocks along the player's horizontal
     * movement, or straight away from the zombie when the movement is too small to tell a direction.
     */
    static Vector interceptPoint(Vector zombiePos, Vector playerPos, Vector playerMovement, double lead) {
        Vector direction = new Vector(playerMovement.getX(), 0, playerMovement.getZ());
        if (direction.lengthSquared() < 0.01) {
            direction = new Vector(playerPos.getX() - zombiePos.getX(), 0, playerPos.getZ() - zombiePos.getZ());
        }
        if (direction.lengthSquared() < 1.0E-6) {
            return playerPos.clone();
        }
        return playerPos.clone().add(direction.normalize().multiply(lead));
    }

    /** Launch velocity that lands a pearl at {@code to}, compensating vanilla gravity and air drag. */
    static Vector pearlLaunchVelocity(Vector from, Vector to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int ticks = (int) Math.max(8, Math.min(40, Math.round(horizontal / 1.1)));
        // distance covered by a unit velocity decaying with drag over the flight
        double travel = (1.0 - Math.pow(PEARL_DRAG, ticks)) / (1.0 - PEARL_DRAG);
        double gravityDrop = PEARL_GRAVITY * ticks * ticks / 2.0;
        Vector velocity = new Vector(dx / travel, (dy + gravityDrop) / travel, dz / travel);
        if (velocity.length() > MAX_PEARL_SPEED) {
            velocity.normalize().multiply(MAX_PEARL_SPEED);
        }
        return velocity;
    }
}
