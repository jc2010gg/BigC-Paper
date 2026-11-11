package dev.linqfy.bigCasares.modules.casino.cards;

public enum Suit {
    SPADES('s', "♠", false),
    HEARTS('h', "♥", true),
    DIAMONDS('d', "♦", true),
    CLUBS('c', "♣", false);

    private final char code;
    private final String symbol;
    private final boolean red;

    Suit(char code, String symbol, boolean red) {
        this.code = code;
        this.symbol = symbol;
        this.red = red;
    }

    public char code() {
        return code;
    }

    public String symbol() {
        return symbol;
    }

    public boolean red() {
        return red;
    }
}
