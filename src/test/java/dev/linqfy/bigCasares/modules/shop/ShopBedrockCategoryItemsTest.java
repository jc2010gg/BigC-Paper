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

    private static List<ShopViewItem> items(int lots, boolean selling, double funds, int breadCarried) {
        return ShopModule.bedrockCategoryItems(FOOD, lots, selling, funds,
            entry -> entry.id().equals("bread") ? breadCarried : 0, amount -> "$" + (int) amount);
    }

    @Test
    void quantityAndModeComeFirstThenTheProductsInOrder() {
        List<ShopViewItem> items = items(1, false, 1000, 0);
        assertEquals(ShopModule.HEADER_BUTTONS, 2);
        assertEquals(ShopModule.QUANTITY_BUTTON, items.get(0).id());
        assertEquals("§bCantidad: x1 §7(tocá para cambiar)", items.get(0).name());
        assertEquals(ShopModule.MODE_BUTTON, items.get(1).id());
        assertEquals("§aModo: comprar §7(tocá para vender)", items.get(1).name());
        assertEquals(List.of("bread", "nuke"), items.subList(2, 4).stream().map(ShopViewItem::id).toList());
        assertEquals("&6Pan §7x16", items.get(2).name());
        assertEquals("$40", items.get(2).price());
        assertEquals("textures/items/bread", items.get(2).iconPath());
        assertNull(items.get(3).iconPath(), "a custom item has no vanilla icon");
    }

    @Test
    void buyingFourLotsShowsTheirCountPriceAndWhatIsMissing() {
        List<ShopViewItem> items = items(4, false, 200, 0);
        assertEquals("&6Pan §7x64", items.get(2).name());
        assertEquals("$160", items.get(2).price());
        assertTrue(items.get(2).available());
        assertEquals("$2000 (faltan $1800)", items.get(3).price());
        assertFalse(items.get(3).available());
    }

    @Test
    void sellingShowsTheSalePriceAndWhatThePlayerCarries() {
        List<ShopViewItem> items = items(2, true, 0, 20);
        assertEquals("§6Modo: vender §7(tocá para comprar)", items.get(1).name());
        assertEquals("$4 (tenés 20)", items.get(2).price(), "two lots are 32 breads; 20 are not enough");
        assertFalse(items.get(2).available());
        assertEquals("no se compra acá", items.get(3).price(), "the nuke has no sale price");
        assertFalse(items.get(3).available());

        assertTrue(items(2, true, 0, 40).get(2).available());
        assertEquals("$4", items(2, true, 0, 40).get(2).price());
    }
}
