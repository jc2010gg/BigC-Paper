package dev.linqfy.bigCasares.modules.schizophrenia;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The Lobotomizador: an iron spear underneath. Any player speared with it by another player is
 * lobotomized; for someone in an episode that is the cure.
 */
final class LobotomizerItem {

    static final String ITEM_ID = "lobotomizador";
    static final String RECIPE_KEY = "lobotomizador";
    /** The vanilla spear shape with an amethyst shard in place of the middle stick. */
    static final List<String> SHAPE = List.of("  I", " A ", "S  ");

    private final NamespacedKey idKey;
    private final NamespacedKey recipeKey;

    LobotomizerItem(Plugin plugin) {
        this.idKey = new NamespacedKey(plugin, "lobotomizer_item");
        this.recipeKey = new NamespacedKey(plugin, RECIPE_KEY);
    }

    NamespacedKey recipeKey() {
        return recipeKey;
    }

    ItemStack create() {
        ItemStack stack = new ItemStack(Material.IRON_SPEAR);
        stack.editMeta(meta -> {
            meta.itemName(Component.text("Lobotomizador", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            meta.lore(List.of(
                line("Un golpe en la cabeza y se acabaron las voces.", NamedTextColor.GRAY),
                line("Lobotomiza al jugador que golpeás. Cura la esquizofrenia.", NamedTextColor.DARK_GRAY),
                line("Efectos secundarios: sí.", NamedTextColor.DARK_GRAY)));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, ITEM_ID);
        });
        return stack;
    }

    ShapedRecipe recipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, create());
        recipe.shape(SHAPE.toArray(String[]::new));
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('A', Material.AMETHYST_SHARD);
        recipe.setIngredient('S', Material.STICK);
        return recipe;
    }

    boolean isLobotomizer(ItemStack stack) {
        if (stack == null || stack.getType() != Material.IRON_SPEAR || !stack.hasItemMeta()) {
            return false;
        }
        return ITEM_ID.equals(stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
