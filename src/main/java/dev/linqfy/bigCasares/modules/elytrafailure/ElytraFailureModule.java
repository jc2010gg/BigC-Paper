package dev.linqfy.bigCasares.modules.elytrafailure;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

import java.util.concurrent.ThreadLocalRandom;

public final class ElytraFailureModule implements PluginModule {

    public static final String MODULE_ID = "elytra-failure";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private ElytraFailureRuntime runtime;

    public ElytraFailureModule(BigCasares plugin) {
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
        ElytraFailureSettings settings = ElytraFailureSettings.load(plugin.getConfig());
        ElytraFailureRuntime created = new ElytraFailureRuntime(
            settings,
            new NamespacedKey(plugin, "elytra_broken_wing"),
            () -> ThreadLocalRandom.current().nextDouble()
        );
        scope.register("elytra-failure-runtime", created::shutdown);
        registrations.registerListener("elytra-failure-listener", new ElytraFailureListener(created));
        registrations.scheduleRepeating("elytra-failure-check",
            () -> created.check(Bukkit.getOnlinePlayers()), settings.checkIntervalTicks(), settings.checkIntervalTicks());
        registrations.scheduleRepeating("elytra-failure-drift", created::tickDrifts, 1L, 1L);
        runtime = created;
    }

    @Override
    public void onDisable() {
        ElytraFailureRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.shutdown();
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }
}
