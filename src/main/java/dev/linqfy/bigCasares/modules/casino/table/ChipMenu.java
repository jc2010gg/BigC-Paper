package dev.linqfy.bigCasares.modules.casino.table;

import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.BiConsumer;

/** Menu to stack chips on a bet: one click per chip, plus a button to take everything back. */
public final class ChipMenu {

    private ChipMenu() {
    }

    public static void open(CasinoContext context, Player player, String title, List<Long> chips,
                            LongSupplier current, BiConsumer<Player, Long> add, Runnable clear) {
        CasinoMenu menu = new CasinoMenu(3, title);
        render(menu, context, player, chips, current, add, clear);
        menu.open(player);
    }

    /** Fills the menu; buttons refresh it in place instead of reopening it. */
    private static void render(CasinoMenu menu, CasinoContext context, Player player, List<Long> chips,
                               LongSupplier current, BiConsumer<Player, Long> add, Runnable clear) {
        int slot = 10;
        for (long chip : chips) {
            if (slot > 16) {
                break;
            }
            menu.set(slot++, CasinoItems.chipButton(chip, false), (clicker, click) -> {
                add.accept(clicker, chip);
                render(menu, context, clicker, chips, current, add, clear);
            });
        }
        menu.set(22, CasinoItems.button(Material.GOLD_INGOT, "§6Apuesta actual: §e" + context.chips(current.getAsLong()),
            "§7Saldo: §f" + context.chips(context.bank().balance(player.getUniqueId()))), null);
        menu.set(18, CasinoItems.button(Material.BARRIER, "§cRetirar mi apuesta"), (clicker, click) -> {
            clear.run();
            render(menu, context, clicker, chips, current, add, clear);
        });
        menu.set(26, CasinoItems.button(Material.LIME_DYE, "§aListo"), (clicker, click) -> clicker.closeInventory());
        menu.fill(CasinoItems.filler());
    }
}
