package dev.linqfy.bigCasares.modules.schizophrenia;

/** Vector math for "is the player staring at it" and "where can a phantom hide". */
final class GazeMath {

    private GazeMath() {
    }

    /** Angle in degrees between a look direction and the direction from the eye to a target. */
    static double angleTo(
        double lookX, double lookY, double lookZ,
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ
    ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double targetLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double lookLength = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (targetLength < 1.0E-6 || lookLength < 1.0E-6) {
            return 0.0;
        }
        double cos = (lookX * dx + lookY * dy + lookZ * dz) / (targetLength * lookLength);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
    }

    /**
     * Horizontal offset for a phantom. {@code side} 0 is straight behind the player, 1 is the edge
     * of peripheral vision. Minecraft yaw: 0 faces +Z, 90 faces -X.
     */
    static double[] offset(float playerYaw, double side, boolean left, double distance) {
        double relative = 180.0 - Math.max(0.0, Math.min(1.0, side)) * 110.0;
        double yaw = Math.toRadians(playerYaw + (left ? relative : -relative));
        return new double[] {-Math.sin(yaw) * distance, Math.cos(yaw) * distance};
    }

    /** Yaw that makes something at (fromX, fromZ) face (toX, toZ). */
    static float yawTowards(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }
}
