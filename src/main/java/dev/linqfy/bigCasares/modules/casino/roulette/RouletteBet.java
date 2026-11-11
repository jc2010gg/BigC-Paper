package dev.linqfy.bigCasares.modules.casino.roulette;

import java.util.Locale;

/** A bet spot on the cloth. {@code number} is only meaningful for straight-ups, dozens and columns. */
public record RouletteBet(Kind kind, int number) {

    public enum Kind {
        STRAIGHT(35),
        RED(1),
        BLACK(1),
        EVEN(1),
        ODD(1),
        LOW(1),
        HIGH(1),
        DOZEN(2),
        COLUMN(2);

        private final int odds;

        Kind(int odds) {
            this.odds = odds;
        }

        /** Profit per chip, "35 a 1". */
        public int odds() {
            return odds;
        }
    }

    public RouletteBet {
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
        switch (kind) {
            case STRAIGHT -> require(number >= 0 && number <= 36, "straight 0-36");
            case DOZEN, COLUMN -> require(number >= 1 && number <= 3, "dozen/column 1-3");
            default -> number = 0;
        }
    }

    public static RouletteBet straight(int number) {
        return new RouletteBet(Kind.STRAIGHT, number);
    }

    public static RouletteBet of(Kind kind) {
        return new RouletteBet(kind, 0);
    }

    public boolean wins(int result) {
        if (kind == Kind.STRAIGHT) {
            return result == number;
        }
        if (result == 0) {
            return false;
        }
        return switch (kind) {
            case RED -> RoulettePockets.color(result) == RoulettePockets.Color.RED;
            case BLACK -> RoulettePockets.color(result) == RoulettePockets.Color.BLACK;
            case EVEN -> result % 2 == 0;
            case ODD -> result % 2 == 1;
            case LOW -> result <= 18;
            case HIGH -> result >= 19;
            case DOZEN -> (result - 1) / 12 + 1 == number;
            case COLUMN -> (result - 1) % 3 + 1 == number;
            case STRAIGHT -> false;
        };
    }

    /** Chips returned to the player, stake included; zero when the bet loses. */
    public long payout(long stake, int result) {
        return wins(result) ? stake * (kind.odds() + 1) : 0;
    }

    public String key() {
        return kind.name().toLowerCase(Locale.ROOT) + (needsNumber() ? "-" + number : "");
    }

    public String label() {
        return switch (kind) {
            case STRAIGHT -> number == 0 ? "§aVerde 0" : "Pleno " + RoulettePockets.legacyColor(number) + number;
            case RED -> "§cRojo";
            case BLACK -> "§8Negro";
            case EVEN -> "§fPar";
            case ODD -> "§fImpar";
            case LOW -> "§f1 a 18";
            case HIGH -> "§f19 a 36";
            case DOZEN -> "§f" + ((number - 1) * 12 + 1) + " a " + number * 12;
            case COLUMN -> "§fColumna " + number;
        };
    }

    public static RouletteBet parseKey(String key) {
        int dash = key.indexOf('-');
        Kind kind = Kind.valueOf((dash < 0 ? key : key.substring(0, dash)).toUpperCase(Locale.ROOT));
        int number = dash < 0 ? 0 : Integer.parseInt(key.substring(dash + 1));
        return new RouletteBet(kind, number);
    }

    private boolean needsNumber() {
        return kind == Kind.STRAIGHT || kind == Kind.DOZEN || kind == Kind.COLUMN;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("Invalid roulette bet: " + message);
        }
    }
}
