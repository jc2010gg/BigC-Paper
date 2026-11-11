package dev.linqfy.bigCasares.modules.casino.slots;

import java.util.EnumMap;
import java.util.Map;

/**
 * Chance and multiplier of every outcome. The multiplier is the total returned for one chip
 * (x3 returns three times the bet). The jackpot pays the progressive pool instead of a multiplier.
 */
public record SlotPaytable(Map<SlotOutcome, Double> chances, Map<SlotOutcome, Integer> multipliers) {

    public SlotPaytable {
        chances = new EnumMap<>(chances);
        multipliers = new EnumMap<>(multipliers);
        double total = 0;
        for (Map.Entry<SlotOutcome, Double> entry : chances.entrySet()) {
            if (entry.getKey() == SlotOutcome.LOSE) {
                throw new IllegalArgumentException("The losing chance is whatever is left");
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Negative chance for " + entry.getKey());
            }
            total += entry.getValue();
        }
        if (total >= 1.0) {
            throw new IllegalArgumentException("Winning chances must add up to less than 100%");
        }
    }

    public static SlotPaytable defaults() {
        Map<SlotOutcome, Double> chances = new EnumMap<>(SlotOutcome.class);
        chances.put(SlotOutcome.EMERALDS, 0.13);
        chances.put(SlotOutcome.GOLD, 0.03);
        chances.put(SlotOutcome.DIAMONDS, 0.004);
        chances.put(SlotOutcome.COAL, 0.025);
        chances.put(SlotOutcome.JACKPOT, 0.0004);
        Map<SlotOutcome, Integer> multipliers = new EnumMap<>(SlotOutcome.class);
        multipliers.put(SlotOutcome.EMERALDS, 3);
        multipliers.put(SlotOutcome.GOLD, 10);
        multipliers.put(SlotOutcome.DIAMONDS, 50);
        multipliers.put(SlotOutcome.COAL, 0);
        return new SlotPaytable(chances, multipliers);
    }

    public double chance(SlotOutcome outcome) {
        if (outcome == SlotOutcome.LOSE) {
            return 1.0 - chances.values().stream().mapToDouble(Double::doubleValue).sum();
        }
        return chances.getOrDefault(outcome, 0.0);
    }

    public int multiplier(SlotOutcome outcome) {
        return multipliers.getOrDefault(outcome, 0);
    }

    /** Expected return per chip from fixed multipliers, ignoring the jackpot pool. */
    public double fixedReturn() {
        double total = 0;
        for (SlotOutcome outcome : SlotOutcome.values()) {
            if (outcome != SlotOutcome.JACKPOT && outcome != SlotOutcome.LOSE) {
                total += chance(outcome) * multiplier(outcome);
            }
        }
        return total;
    }
}
