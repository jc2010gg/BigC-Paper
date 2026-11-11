package dev.linqfy.bigCasares.modules.casino.cards;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** A shuffled multi-deck shoe that reshuffles itself once the cut card is reached. */
public final class Shoe implements CardSource {

    private final int decks;
    private final double penetration;
    private final RandomGenerator random;
    private final List<Card> cards = new ArrayList<>();
    private int next;

    public Shoe(int decks, double penetration, RandomGenerator random) {
        if (decks < 1) {
            throw new IllegalArgumentException("A shoe needs at least one deck");
        }
        if (penetration <= 0.0 || penetration > 1.0) {
            throw new IllegalArgumentException("Penetration must be in (0, 1]");
        }
        this.decks = decks;
        this.penetration = penetration;
        this.random = random;
        shuffle();
    }

    public static List<Card> orderedDeck() {
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }
        return deck;
    }

    public void shuffle() {
        cards.clear();
        for (int deck = 0; deck < decks; deck++) {
            cards.addAll(orderedDeck());
        }
        for (int index = cards.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            Card held = cards.get(index);
            cards.set(index, cards.get(swap));
            cards.set(swap, held);
        }
        next = 0;
    }

    /** True once the cut card was passed; reshuffle between rounds, never in the middle of one. */
    public boolean pastCutCard() {
        return next >= (int) Math.floor(cards.size() * penetration);
    }

    public int remaining() {
        return cards.size() - next;
    }

    @Override
    public Card draw() {
        if (next >= cards.size()) {
            shuffle();
        }
        return cards.get(next++);
    }
}
