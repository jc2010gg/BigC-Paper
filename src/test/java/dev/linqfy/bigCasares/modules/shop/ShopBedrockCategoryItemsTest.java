package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopBedrockCategoryItemsTest {
    private static final ShopCategory FOOD = new ShopCategory("food", "&cComida", Material.BREAD, 13, List.of(
        new ShopEntry("bread", 10, Material.BREAD, null, 16, 40.0, 2.0, "&6Pan", List.of("&7Pack")),
        new ShopEntry("nuke", 11, null, "nuke_shot", 1, 500.0, 0.0, "Nuke", List.of())));

    private static List<ShopViewItem> items(int lots, double funds) {
        return ShopModule.bedrockCategoryItems(FOOD, lots, funds, amount -> "$" + (int) amount);
    }

    @Test
    void theFirstButtonIsTheQuantityAndProductsFollowInOrder() {
        List<ShopViewItem> items = items(1, 1000);
        assertEquals(ShopModule.QUANTITY_BUTTON, items.get(0).id());
        assertEquals("§bCantidad: x1 §7(tocá para cambiar)", items.get(0).name());
        assertEquals(List.of("bread", "nuke"), items.subList(1, 3).stream().map(ShopViewItem::id).toList());
        assertEquals("&6Pan §7x16", items.get(1).name());
        assertEquals("$40", items.get(1).price());
        assertEquals("textures/items/bread", items.get(1).iconPath());
        assertNull(items.get(2).iconPath(), "a custom item has no vanilla icon");
    }

    @Test
    void fourLotsShowTheirCountPriceAndWhatIsMissing() {
        List<ShopViewItem> items = items(4, 200);
        assertEquals("§bCantidad: x4 §7(tocá para cambiar)", items.get(0).name());
        assertEquals("&6Pan §7x64", items.get(1).name());
        assertEquals("$160", items.get(1).price());
        assertTrue(items.get(1).available());
        assertEquals("$2000 (faltan $1800)", items.get(2).price());
        assertFalse(items.get(2).available());
    }
}
