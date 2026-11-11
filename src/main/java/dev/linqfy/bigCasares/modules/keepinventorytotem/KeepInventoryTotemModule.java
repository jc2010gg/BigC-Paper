package dev.linqfy.bigCasares.modules.keepinventorytotem;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.NamespacedKey;

import java.util.UUID;

public final class KeepInventoryTotemModule implements PluginModule {

    public static final String MODULE_ID = "keep-inventory-totem";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private KeepInventoryTotemListener listener;

    public KeepInventoryTotemModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        KeepInventoryTotemSettings settings = KeepInventoryTotemSettings.load(plugin.getConfig());
        KeepInventoryTotemItem item = new KeepInventoryTotemItem(
            plugin.getCustomItemRegistry(), new NamespacedKey(plugin, KeepInventoryTotemItem.ID));
        KeepInventoryTotemListener created = new KeepInventoryTotemListener(
            item,
            settings,
            new NamespacedKey(plugin, "keep_inventory_totem_last_use"),
            registrations,
            System::currentTimeMillis
        );

        plugin.getCustomItemRegistry().register(item);
        scope.register("custom-item", () -> plugin.getCustomItemRegistry().unregister(KeepInventoryTotemItem.ID));
        scope.register("totem-runtime", created::shutdown);
        registrations.registerListener("totem-listener", created);
        listener = created;
    }

    @Override
    public void onDisable() {
        KeepInventoryTotemListener current = listener;
        listener = null;
        if (current != null) {
            current.shutdown();
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    /** Whether the death being processed right now was saved by a totem; the tombstone must then skip it. */
    public boolean protectsDeath(UUID playerId) {
        KeepInventoryTotemListener current = listener;
        return current != null && playerId != null && current.protectsDeath(playerId);
    }
}
