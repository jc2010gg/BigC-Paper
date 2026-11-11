package dev.linqfy.bigCasares.modules.casino.world;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class CasinoKeys {

    /** Marks every entity the casino spawns, so stray copies can be found and removed. */
    public final NamespacedKey entity;
    public final NamespacedKey drink;
    public final NamespacedKey hiddenModel;
    public final NamespacedKey hiddenTooltip;
    public final NamespacedKey ticket;

    public CasinoKeys(Plugin plugin) {
        this.entity = new NamespacedKey(plugin, "casino_entity");
        this.drink = new NamespacedKey(plugin, "casino_drink");
        this.hiddenModel = new NamespacedKey(plugin, "casino_hidden_model");
        this.hiddenTooltip = new NamespacedKey(plugin, "casino_hidden_tooltip");
        this.ticket = new NamespacedKey(plugin, "casino_ticket");
    }
}
