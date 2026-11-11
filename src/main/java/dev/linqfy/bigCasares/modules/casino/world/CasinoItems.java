package dev.linqfy.bigCasares.modules.casino.world;

import dev.linqfy.bigCasares.modules.casino.CasinoFormat;
import dev.linqfy.bigCasares.modules.casino.cards.Card;
import dev.linqfy.bigCasares.modules.casino.slots.SlotSymbol;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Items used as props (cards, chips, reel symbols) and as menu buttons. */
public final class CasinoItems {

    public static final Key CARD_MODEL = Key.key("bigcasares", "casino_card");
    public static final Key CHIP_MODEL = Key.key("bigcasares", "casino_chip");
    public static final Key SEVEN_MODEL = Key.key("bigcasares", "casino_seven");
    /** Item definition that renders nothing, used by the white powder drink. */
    public static final Key HIDDEN_MODEL = Key.key("bigcasares", "casino_hidden");
    /** Chip colours available in the resource pack, smallest first. */
    public static final List<Long> CHIP_FACES = List.of(10L, 50L, 100L, 500L, 1000L, 5000L);

    private CasinoItems() {
    }

    public static ItemStack card(Card card) {
        return modelled(Material.PAPER, CARD_MODEL, card.code());
    }

    public static ItemStack cardBack() {
        return modelled(Material.PAPER, CARD_MODEL, Card.BACK_CODE);
    }

    /** Chip prop of the largest face not above {@code amount}. */
    public static ItemStack chip(long amount) {
        long face = CHIP_FACES.getFirst();
        for (long candidate : CHIP_FACES) {
            if (candidate <= amount) {
                face = candidate;
            }
        }
        return modelled(Material.SUNFLOWER, CHIP_MODEL, Long.toString(face));
    }

    public static ItemStack symbol(SlotSymbol symbol) {
        return switch (symbol) {
            case EMERALD -> new ItemStack(Material.EMERALD);
            case GOLD -> new ItemStack(Material.GOLD_INGOT);
            case DIAMOND -> new ItemStack(Material.DIAMOND);
            case COAL -> new ItemStack(Material.COAL);
            case SEVEN -> {
                ItemStack seven = new ItemStack(Material.RED_DYE);
                seven.setData(DataComponentTypes.ITEM_MODEL, SEVEN_MODEL);
                yield seven;
            }
        };
    }

    public static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(CasinoText.legacy(name));
        meta.lore(java.util.Arrays.stream(lore).map(CasinoText::legacy).toList());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack chipButton(long amount, boolean selected) {
        ItemStack chip = chip(amount);
        ItemMeta meta = chip.getItemMeta();
        meta.displayName(CasinoText.legacy((selected ? "§a§l▶ " : "§e") + CasinoFormat.chips(amount)));
        meta.lore(List.of(CasinoText.legacy(selected ? "§aFicha seleccionada" : "§7Click para usar esta ficha")));
        meta.setEnchantmentGlintOverride(selected);
        chip.setItemMeta(meta);
        return chip;
    }

    public static ItemStack filler() {
        return button(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    private static ItemStack modelled(Material base, Key model, String variant) {
        ItemStack item = new ItemStack(base);
        item.setData(DataComponentTypes.ITEM_MODEL, model);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(variant).build());
        return item;
    }
}
