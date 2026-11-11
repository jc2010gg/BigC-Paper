package dev.linqfy.bigCasares.modules.casino.slots;

public enum SlotOutcome {
    /** Three emeralds. */
    EMERALDS(SlotSymbol.EMERALD),
    /** Three gold ingots. */
    GOLD(SlotSymbol.GOLD),
    /** Three diamonds. */
    DIAMONDS(SlotSymbol.DIAMOND),
    /** Three coals: the machine shocks the player and keeps the bet. */
    COAL(SlotSymbol.COAL),
    /** 7-7-7: the progressive jackpot. */
    JACKPOT(SlotSymbol.SEVEN),
    LOSE(null);

    private final SlotSymbol symbol;

    SlotOutcome(SlotSymbol symbol) {
        this.symbol = symbol;
    }

    /** Symbol repeated on the payline, or {@code null} for a plain loss. */
    public SlotSymbol symbol() {
        return symbol;
    }
}
