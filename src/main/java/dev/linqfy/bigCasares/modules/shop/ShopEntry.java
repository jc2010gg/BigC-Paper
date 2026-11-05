package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Material;

import java.util.List;

/** {@code dailyLimit}: lots one player may buy per day ({@code limite-diario} in shop.yml); 0 for no limit. */
public record ShopEntry(
    String id,
    int slot,
    Material material,
    String customItemId,
    int amount,
    double buyPrice,
    double sellPrice,
    String displayName,
    List<String> lore,
    int dailyLimit
) {
    public ShopEntry(String id, int slot, Material material, String customItemId, int amount, double buyPrice,
                     double sellPrice, String displayName, List<String> lore) {
        this(id, slot, material, customItemId, amount, buyPrice, sellPrice, displayName, lore, 0);
    }
}
