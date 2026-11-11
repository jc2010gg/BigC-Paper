package dev.linqfy.bigCasares.modules.casino.bar;

import java.util.random.RandomGenerator;

/** Turns a chat line into drunk speech: stretched vowels, swapped letters and the odd "hic". */
public final class Babble {

    private static final String VOWELS = "aeiouáéíóú";
    private static final String NOISE = "shjrgl";

    private Babble() {
    }

    public static String slur(String message, RandomGenerator random) {
        StringBuilder out = new StringBuilder(message.length() + 16);
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            out.append(character);
            if (VOWELS.indexOf(Character.toLowerCase(character)) >= 0 && random.nextDouble() < 0.35) {
                int repeats = 1 + random.nextInt(3);
                out.append(String.valueOf(character).repeat(repeats));
            } else if (Character.isLetter(character) && random.nextDouble() < 0.12) {
                out.append(NOISE.charAt(random.nextInt(NOISE.length())));
            }
            if (character == ' ' && random.nextDouble() < 0.1) {
                out.append("*hic* ");
            }
        }
        if (random.nextDouble() < 0.5) {
            out.append(" ...hic");
        }
        return out.toString();
    }
}
