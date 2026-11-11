package dev.linqfy.bigCasares.modules.casino.bar;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum DrinkType {
    WHISKY_NETHERRACK("§6Whisky de Netherrack", 0x8B3A1A,
        List.of("§7Destilado en el Nether, filtrado por un zombi.", "§7Saturación, lentitud y niebla por 1 min.")),
    CERVEZA_CALIENTE("§eCerveza Caliente", 0xD9A520,
        List.of("§7Estuvo al sol desde la última luna de sangre.", "§7Náuseas y lengua trabada por 20 s.")),
    COCTEL_RUINA("§dEl Cóctel Ruina", 0x9B30FF,
        List.of("§7Mitad gloria, mitad vómito.", "§750%: Prisa minera III por 5 min.", "§750%: Ceguera y arcadas.")),
    VASO_POLVO_BLANCO("§fVaso de polvo blanco", 0xF2F2F2,
        List.of("§7Nadie sabe qué es. Nadie pregunta.", "§7Velocidad III por 5 min, pero no ves tu inventario",
            "§7y empezás a ver cosas que no están."));

    private final String displayName;
    private final int color;
    private final List<String> lore;

    DrinkType(String displayName, int color, List<String> lore) {
        this.displayName = displayName;
        this.color = color;
        this.lore = lore;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }

    public List<String> lore() {
        return lore;
    }

    public static Optional<DrinkType> byId(String id) {
        for (DrinkType type : values()) {
            if (type.id().equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
