package dev.linqfy.bigCasares.modules.casino.poker;

public enum HandCategory {
    HIGH_CARD("Carta alta"),
    PAIR("Par"),
    TWO_PAIR("Doble par"),
    THREE_OF_A_KIND("Trío"),
    STRAIGHT("Escalera"),
    FLUSH("Color"),
    FULL_HOUSE("Full"),
    FOUR_OF_A_KIND("Póker"),
    STRAIGHT_FLUSH("Escalera de color");

    private final String label;

    HandCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
