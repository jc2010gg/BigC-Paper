package dev.linqfy.bigCasares.modules.casino;

import org.bukkit.Location;
import org.bukkit.World;

/** A placed attraction. Its position is stored as plain values so a missing world never breaks loading. */
public record Installation(String id, AttractionType type, String world, double x, double y, double z, float yaw,
                           String variant) {

    public Location location(World resolved) {
        return new Location(resolved, x, y, z, yaw, 0);
    }

    public int chunkX() {
        return (int) Math.floor(x) >> 4;
    }

    public int chunkZ() {
        return (int) Math.floor(z) >> 4;
    }

    public double distanceSquared(String otherWorld, double ox, double oy, double oz) {
        if (!world.equals(otherWorld)) {
            return Double.MAX_VALUE;
        }
        double dx = x - ox;
        double dy = y - oy;
        double dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}
