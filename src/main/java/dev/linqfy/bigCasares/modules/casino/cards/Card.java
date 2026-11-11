package dev.linqfy.bigCasares.modules.casino.cards;

import java.util.Locale;
import java.util.Objects;

public record Card(Rank rank, Suit suit) {

    /** Model code of the face-down card in the resource pack. */
    public static final String BACK_CODE = "back";

    public Card {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(suit, "suit");
    }

    /** Resource-pack code, for example {@code as}, {@code 10h} or {@code qd}. */
    public String code() {
        return rank.label().toLowerCase(Locale.ROOT) + suit.code();
    }

    /** Chat label with legacy colors, for example {@code §cA♥}. */
    public String label() {
        return (suit.red() ? "§c" : "§f") + rank.label() + suit.symbol();
    }

    public static Card parse(String code) {
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            throw new IllegalArgumentException("Invalid card code: " + code);
        }
        char suitCode = normalized.charAt(normalized.length() - 1);
        String rankLabel = normalized.substring(0, normalized.length() - 1);
        Suit suit = null;
        for (Suit candidate : Suit.values()) {
            if (candidate.code() == suitCode) {
                suit = candidate;
            }
        }
        Rank rank = null;
        for (Rank candidate : Rank.values()) {
            if (candidate.label().equalsIgnoreCase(rankLabel)) {
                rank = candidate;
            }
        }
        if (suit == null || rank == null) {
            throw new IllegalArgumentException("Invalid card code: " + code);
        }
        return new Card(rank, suit);
    }
}
