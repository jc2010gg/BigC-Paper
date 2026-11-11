package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Material;

import java.util.List;

public record ShopEntry(
    String id,
    int slot,
    Material material,
    String customItemId,
    int amount,
    double buyPrice,
    double sellPrice,
    String displayName,
    List<String> lore
) {
}
