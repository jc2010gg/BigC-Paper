package dev.linqfy.bigCasares.modules.casino.cards;

public enum Rank {
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A");

    private final int strength;
    private final String label;

    Rank(int strength, String label) {
        this.strength = strength;
        this.label = label;
    }

    /** Poker strength: 2..14, the ace is high. */
    public int strength() {
        return strength;
    }

    public String label() {
        return label;
    }

    /** Blackjack value with the ace counted as 1; the hand decides when it is worth 11. */
    public int blackjackValue() {
        if (this == ACE) {
            return 1;
        }
        return Math.min(strength, 10);
    }
}
