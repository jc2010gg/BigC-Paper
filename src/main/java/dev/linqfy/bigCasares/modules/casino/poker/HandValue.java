package dev.linqfy.bigCasares.modules.casino.poker;

import java.util.List;

/** Comparable strength of the best five-card hand. Higher {@link #score()} wins; equal scores split. */
public record HandValue(HandCategory category, long score, List<Integer> ranks) implements Comparable<HandValue> {

    public HandValue {
        ranks = List.copyOf(ranks);
    }

    @Override
    public int compareTo(HandValue other) {
        return Long.compare(score, other.score);
    }

    public String label() {
        if (category == HandCategory.STRAIGHT_FLUSH && ranks.getFirst() == 14) {
            return "Escalera real";
        }
        return category.label();
    }
}
