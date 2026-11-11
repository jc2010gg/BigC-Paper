package dev.linqfy.bigCasares.modules.casino.slots;

public enum SlotSymbol {
    EMERALD("Esmeralda", "§a"),
    GOLD("Lingote de oro", "§6"),
    DIAMOND("Diamante", "§b"),
    COAL("Carbón", "§8"),
    SEVEN("7", "§c");

    private final String label;
    private final String color;

    SlotSymbol(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return color + label;
    }
}
