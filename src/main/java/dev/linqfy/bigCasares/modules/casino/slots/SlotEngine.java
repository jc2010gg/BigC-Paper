package dev.linqfy.bigCasares.modules.casino.slots;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Draws the outcome from the paytable and then builds reels that show it. Losing pulls are sometimes shown
 * as a near miss: two premium symbols on the payline and the third one row away from it.
 */
public final class SlotEngine {

    private static final SlotSymbol[] TEASERS = {SlotSymbol.SEVEN, SlotSymbol.DIAMOND, SlotSymbol.GOLD};

    private final SlotPaytable paytable;
    private final double nearMissChance;
    private final RandomGenerator random;

    public SlotEngine(SlotPaytable paytable, double nearMissChance, RandomGenerator random) {
        if (nearMissChance < 0 || nearMissChance > 1) {
            throw new IllegalArgumentException("Near-miss chance must be within [0, 1]");
        }
        this.paytable = paytable;
        this.nearMissChance = nearMissChance;
        this.random = random;
    }

    public SlotPaytable paytable() {
        return paytable;
    }

    public SlotSpin spin() {
        SlotOutcome outcome = drawOutcome();
        if (outcome != SlotOutcome.LOSE) {
            List<SlotSymbol> payline = List.of(outcome.symbol(), outcome.symbol(), outcome.symbol());
            return new SlotSpin(outcome, frame(payline, -1, null), false);
        }
        if (random.nextDouble() < nearMissChance) {
            SlotSymbol teaser = TEASERS[random.nextInt(TEASERS.length)];
            SlotSymbol third = randomOtherThan(teaser);
            List<SlotSymbol> payline = List.of(teaser, teaser, third);
            return new SlotSpin(outcome, frame(payline, 2, teaser), true);
        }
        return new SlotSpin(outcome, frame(losingPayline(), -1, null), false);
    }

    SlotOutcome drawOutcome() {
        double roll = random.nextDouble();
        double cumulative = 0;
        for (SlotOutcome outcome : SlotOutcome.values()) {
            if (outcome == SlotOutcome.LOSE) {
                continue;
            }
            cumulative += paytable.chance(outcome);
            if (roll < cumulative) {
                return outcome;
            }
        }
        return SlotOutcome.LOSE;
    }

    private List<SlotSymbol> losingPayline() {
        while (true) {
            List<SlotSymbol> line = List.of(randomSymbol(), randomSymbol(), randomSymbol());
            if (!(line.get(0) == line.get(1) && line.get(1) == line.get(2))) {
                return line;
            }
        }
    }

    /** Fills the rows around the payline; {@code teaseReel} gets {@code teaser} just off the line. */
    private List<List<SlotSymbol>> frame(List<SlotSymbol> payline, int teaseReel, SlotSymbol teaser) {
        List<List<SlotSymbol>> window = new ArrayList<>(3);
        for (int reel = 0; reel < 3; reel++) {
            SlotSymbol above = randomOtherThan(payline.get(reel));
            SlotSymbol below = randomOtherThan(payline.get(reel));
            if (reel == teaseReel) {
                if (random.nextBoolean()) {
                    above = teaser;
                } else {
                    below = teaser;
                }
            }
            window.add(List.of(above, payline.get(reel), below));
        }
        return window;
    }

    private SlotSymbol randomSymbol() {
        SlotSymbol[] symbols = SlotSymbol.values();
        return symbols[random.nextInt(symbols.length)];
    }

    private SlotSymbol randomOtherThan(SlotSymbol excluded) {
        SlotSymbol symbol;
        do {
            symbol = randomSymbol();
        } while (symbol == excluded);
        return symbol;
    }
}
