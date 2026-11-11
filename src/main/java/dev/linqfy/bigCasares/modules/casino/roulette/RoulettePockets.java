package dev.linqfy.bigCasares.modules.casino.roulette;

import java.util.List;
import java.util.Set;

/** Single-zero (European) wheel layout. */
public final class RoulettePockets {

    public enum Color {
        RED,
        BLACK,
        GREEN
    }

    /** Pockets clockwise as they appear on the wheel. */
    public static final List<Integer> WHEEL_ORDER = List.of(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
        5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    );

    public static final int COUNT = WHEEL_ORDER.size();

    private static final Set<Integer> RED = Set.of(
        1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36
    );

    private RoulettePockets() {
    }

    public static Color color(int number) {
        if (number < 0 || number > 36) {
            throw new IllegalArgumentException("Not a roulette number: " + number);
        }
        if (number == 0) {
            return Color.GREEN;
        }
        return RED.contains(number) ? Color.RED : Color.BLACK;
    }

    public static int indexOf(int number) {
        int index = WHEEL_ORDER.indexOf(number);
        if (index < 0) {
            throw new IllegalArgumentException("Not a roulette number: " + number);
        }
        return index;
    }

    /** Angle in radians of the pocket centre on the wheel, measured from pocket 0. */
    public static double angleOf(int number) {
        return indexOf(number) * pocketWidth();
    }

    public static double pocketWidth() {
        return Math.PI * 2 / COUNT;
    }

    public static String legacyColor(int number) {
        return switch (color(number)) {
            case RED -> "§c";
            case BLACK -> "§8";
            case GREEN -> "§a";
        };
    }
}
