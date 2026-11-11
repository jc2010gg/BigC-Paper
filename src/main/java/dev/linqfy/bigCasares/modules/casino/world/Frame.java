package dev.linqfy.bigCasares.modules.casino.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Local coordinates of an attraction: {@code right}, {@code up} and {@code forward} relative to an origin and a
 * facing snapped to the four directions, so every piece lines up with the block grid.
 */
public record Frame(World world, double x, double y, double z, float yaw) {

    public static Frame of(Location location) {
        return new Frame(location.getWorld(), location.getX(), location.getY(), location.getZ(),
            snap(location.getYaw()));
    }

    public static float snap(float yaw) {
        return (float) (Math.round(yaw / 90.0) * 90 % 360 + 360) % 360;
    }

    public Vector forwardVector() {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0, Math.cos(radians));
    }

    public Vector rightVector() {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.cos(radians), 0, -Math.sin(radians));
    }

    public Location at(double right, double up, double forward) {
        Vector f = forwardVector();
        Vector r = rightVector();
        return new Location(world,
            x + r.getX() * right + f.getX() * forward,
            y + up,
            z + r.getZ() * right + f.getZ() * forward,
            yaw, 0);
    }

    /** Same point as {@link #at} but looking {@code yawOffset} degrees away from the frame's facing. */
    public Location at(double right, double up, double forward, float yawOffset) {
        Location location = at(right, up, forward);
        location.setYaw(yaw + yawOffset);
        return location;
    }

    public Location origin() {
        return new Location(world, x, y, z, yaw, 0);
    }

    public Frame child(double right, double up, double forward, float yawOffset) {
        Location location = at(right, up, forward);
        return new Frame(world, location.getX(), location.getY(), location.getZ(), snap(yaw + yawOffset));
    }
}
