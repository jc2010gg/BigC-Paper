package dev.linqfy.bigCasares.modules.casino.bar;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.world.CasinoMenu;
import dev.linqfy.bigCasares.modules.casino.world.CasinoItems;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.DisplayKit;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Dark-wood bar with a bartender who sells the house's questionable drinks. */
public final class CasinoBar extends Attraction {

    private final DrinkItems drinks;

    public CasinoBar(CasinoContext context, Installation installation, Frame frame, DrinkItems drinks) {
        super(context, installation, frame);
        this.drinks = drinks;
    }

    @Override
    public void spawn() {
        DisplayKit kit = context.kit();
        kit.block(props, frame.at(0, 0, 0.9), Material.DARK_OAK_PLANKS.createBlockData(), 5.0f, 1.05f, 0.7f);
        kit.block(props, frame.at(0, 1.05, 0.95), Material.STRIPPED_DARK_OAK_WOOD.createBlockData(), 5.2f, 0.08f, 0.9f);
        kit.block(props, frame.at(0, 0, 0.55), Material.POLISHED_BLACKSTONE.createBlockData(), 5.0f, 0.1f, 0.1f);
        // Back shelf with bottles.
        kit.block(props, frame.at(0, 0, -0.9), Material.DARK_OAK_PLANKS.createBlockData(), 5.0f, 0.9f, 0.4f);
        kit.block(props, frame.at(0, 1.5, -0.95), Material.DARK_OAK_SLAB.createBlockData(), 5.0f, 0.06f, 0.35f);
        kit.block(props, frame.at(0, 2.1, -0.95), Material.DARK_OAK_SLAB.createBlockData(), 5.0f, 0.06f, 0.35f);
        DrinkType[] types = DrinkType.values();
        for (int index = 0; index < 14; index++) {
            double right = -2.2 + index * 0.33;
            double up = index % 2 == 0 ? 1.56 : 2.16;
            kit.item(props, frame.at(right, up + 0.15, -0.95), drinks.create(types[index % types.length]), 0.35f,
                DisplayKit.NONE);
        }
        for (int stool = 0; stool < 4; stool++) {
            double right = -1.8 + stool * 1.2;
            kit.block(props, frame.at(right, 0, 1.75), Material.DARK_OAK_FENCE.createBlockData(), 0.2f, 0.6f, 0.2f);
            kit.block(props, frame.at(right, 0.6, 1.75), Material.RED_WOOL.createBlockData(), 0.5f, 0.1f, 0.5f);
        }
        Mannequin barman = kit.npc(props, frame.at(0, 0, -0.2, 0), "§6§lEl Barman", "§7\"¿Lo de siempre?\"", "");
        context.router().bind(barman, this::open);
        Interaction counter = kit.interaction(props, frame.at(0, 0, 1.0), 5.0f, 1.3f);
        context.router().bind(counter, this::open);
        kit.text(props, frame.at(0, 3.0, -0.6), CasinoText.legacy("§6§lEL BAR DEL VICIO\n§7Se sirve lo que hay"), 0.9f,
            true, 0x99000000);
    }

    private void open(Player player) {
        CasinoMenu menu = new CasinoMenu(3, "§6El Bar del Vicio");
        int slot = 10;
        for (DrinkType type : DrinkType.values()) {
            long price = context.settings().drinkPrices().getOrDefault(type, 100L);
            ItemStack shown = drinks.create(type);
            ItemMeta meta = shown.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(CasinoText.legacy("§6Precio: §e" + context.chips(price)));
            lore.add(CasinoText.legacy("§a§lClick para pedir"));
            meta.lore(lore);
            shown.setItemMeta(meta);
            menu.set(slot, shown, (clicker, click) -> buy(clicker, type, price));
            slot += 2;
        }
        menu.set(22, CasinoItems.button(Material.OAK_SIGN, "§7La gerencia no se responsabiliza",
            "§7por pérdida de órganos."), null);
        menu.fill(CasinoItems.filler());
        menu.open(player);
    }

    private void buy(Player player, DrinkType type, long price) {
        if (!context.bank().withdraw(player.getUniqueId(), price)) {
            player.sendMessage("§6[Barman] §cSin plata no hay trago, campeón.");
            return;
        }
        player.getInventory().addItem(drinks.create(type)).values()
            .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage("§6[Barman] §fAhí tenés tu " + type.displayName() + "§f. Salud.");
        context.sound(player.getLocation(), Sound.BLOCK_GLASS_PLACE, 0.8f, 1.4f);
    }
}
