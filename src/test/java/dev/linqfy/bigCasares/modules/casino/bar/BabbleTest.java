package dev.linqfy.bigCasares.modules.casino.bar;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BabbleTest {

    @Test
    void slurringKeepsTheOriginalLettersInOrder() {
        String original = "hola como andan todos";
        String slurred = Babble.slur(original, new SplittableRandom(3));
        assertNotEquals(original, slurred);
        int cursor = 0;
        for (char character : slurred.toCharArray()) {
            if (cursor < original.length() && character == original.charAt(cursor)) {
                cursor++;
            }
        }
        assertEquals(original.length(), cursor, "every original character is still there: " + slurred);
        assertTrue(slurred.length() > original.length());
    }

    @Test
    void drinksResolveById() {
        for (DrinkType type : DrinkType.values()) {
            assertEquals(type, DrinkType.byId(type.id()).orElseThrow());
        }
        assertTrue(DrinkType.byId("agua").isEmpty());
    }
}
