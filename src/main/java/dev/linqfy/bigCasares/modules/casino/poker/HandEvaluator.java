package dev.linqfy.bigCasares.modules.casino.poker;

import dev.linqfy.bigCasares.modules.casino.cards.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Finds the best five-card poker hand among five to seven cards. */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    public static HandValue best(List<Card> cards) {
        if (cards.size() < 5 || cards.size() > 7) {
            throw new IllegalArgumentException("Need 5 to 7 cards, got " + cards.size());
        }
        HandValue best = null;
        int count = cards.size();
        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                for (int c = b + 1; c < count; c++) {
                    for (int d = c + 1; d < count; d++) {
                        for (int e = d + 1; e < count; e++) {
                            HandValue value = evaluateFive(List.of(
                                cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)));
                            if (best == null || value.compareTo(best) > 0) {
                                best = value;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    static HandValue evaluateFive(List<Card> five) {
        int[] counts = new int[15];
        boolean flush = true;
        for (Card card : five) {
            counts[card.rank().strength()]++;
            flush &= card.suit() == five.getFirst().suit();
        }
        int straightHigh = straightHigh(counts);

        // Ranks ordered by (count desc, rank desc): the comparison order of every category.
        List<Integer> ordered = new ArrayList<>();
        for (int rank = 14; rank >= 2; rank--) {
            if (counts[rank] > 0) {
                ordered.add(rank);
            }
        }
        ordered.sort(Comparator.comparingInt((Integer rank) -> counts[rank]).reversed()
            .thenComparing(Comparator.reverseOrder()));
        int[] shape = ordered.stream().mapToInt(rank -> counts[rank]).toArray();

        HandCategory category;
        List<Integer> ranks = ordered;
        if (straightHigh > 0 && flush) {
            category = HandCategory.STRAIGHT_FLUSH;
            ranks = List.of(straightHigh);
        } else if (shape[0] == 4) {
            category = HandCategory.FOUR_OF_A_KIND;
        } else if (shape[0] == 3 && shape.length == 2) {
            category = HandCategory.FULL_HOUSE;
        } else if (flush) {
            category = HandCategory.FLUSH;
        } else if (straightHigh > 0) {
            category = HandCategory.STRAIGHT;
            ranks = List.of(straightHigh);
        } else if (shape[0] == 3) {
            category = HandCategory.THREE_OF_A_KIND;
        } else if (shape[0] == 2 && shape[1] == 2) {
            category = HandCategory.TWO_PAIR;
        } else if (shape[0] == 2) {
            category = HandCategory.PAIR;
        } else {
            category = HandCategory.HIGH_CARD;
        }
        long score = category.ordinal();
        for (int index = 0; index < 5; index++) {
            score = score * 15 + (index < ranks.size() ? ranks.get(index) : 0);
        }
        return new HandValue(category, score, ranks);
    }

    private static int straightHigh(int[] counts) {
        for (int high = 14; high >= 6; high--) {
            final int top = high;
            if (Arrays.stream(new int[]{0, 1, 2, 3, 4}).allMatch(offset -> counts[top - offset] == 1)) {
                return high;
            }
        }
        boolean wheel = counts[14] == 1 && counts[2] == 1 && counts[3] == 1 && counts[4] == 1 && counts[5] == 1;
        return wheel ? 5 : 0;
    }
}
