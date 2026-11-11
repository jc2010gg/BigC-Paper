package dev.linqfy.bigCasares.modules.casino.table;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** A floating button shown only to the seated player. */
public record TableButton(String label, int backgroundArgb, Consumer<Player> action) {

    public static final int GREEN = 0xCC1E7A34;
    public static final int RED = 0xCC9E1B1B;
    public static final int GOLD = 0xCCB8860B;
    public static final int BLUE = 0xCC1F4E9E;
    public static final int GREY = 0xCC333333;
}
