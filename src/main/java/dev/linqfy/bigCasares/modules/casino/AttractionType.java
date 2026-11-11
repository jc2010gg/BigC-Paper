package dev.linqfy.bigCasares.modules.casino;

import java.util.Locale;
import java.util.Optional;

public enum AttractionType {
    BLACKJACK("blackjack", "Mesa de Blackjack"),
    POKER("poker", "Mesa de Póker"),
    ROULETTE("ruleta", "La Gran Ruleta"),
    SLOT("tragamonedas", "Tragamonedas"),
    FIGHT_PIT("foso", "Foso de Riñas"),
    LOAN_SHARK("usurero", "La Cueva de la Usura"),
    PAWN_SHOP("empenos", "Casa de Empeños"),
    BAR("bar", "El Bar del Vicio"),
    SIGN("cartel", "Cartel de la gerencia"),
    GRIME("mugre", "Colillas y manchas");

    private final String id;
    private final String displayName;

    AttractionType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<AttractionType> byId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        for (AttractionType type : values()) {
            if (type.id.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
