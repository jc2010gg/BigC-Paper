package dev.linqfy.bigCasares.modules.casino;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class CasinoFormat {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR"));

    private CasinoFormat() {
    }

    /** {@code 12500} → {@code ⛃ 12.500}. */
    public static String chips(long amount) {
        return "⛃ " + new DecimalFormat("#,##0", SYMBOLS).format(amount);
    }

    public static String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + seconds % 60 + "s";
    }
}
