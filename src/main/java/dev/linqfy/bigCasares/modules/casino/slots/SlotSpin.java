package dev.linqfy.bigCasares.modules.casino.slots;

import java.util.List;

/**
 * Result of one pull. {@code window} is three reels of three rows (above, payline, below); only the payline
 * pays. {@code nearMiss} marks a loss dressed up with the missing symbol just above or below the payline.
 */
public record SlotSpin(SlotOutcome outcome, List<List<SlotSymbol>> window, boolean nearMiss) {

    public static final int PAYLINE = 1;

    public SlotSpin {
        window = window.stream().map(List::copyOf).toList();
        if (window.size() != 3 || window.stream().anyMatch(reel -> reel.size() != 3)) {
            throw new IllegalArgumentException("A slot window is three reels of three symbols");
        }
    }

    public List<SlotSymbol> payline() {
        return window.stream().map(reel -> reel.get(PAYLINE)).toList();
    }
}
