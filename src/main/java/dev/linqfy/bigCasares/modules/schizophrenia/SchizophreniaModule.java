package dev.linqfy.bigCasares.modules.schizophrenia;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

/**
 * Esquizofrenia: lonely players in the dark (or anyone an admin picks) start hearing and seeing
 * things only they perceive. It escalates until someone cures them with a hit of the Lobotomizador.
 */
public final class SchizophreniaModule implements PluginModule {

    public static final String MODULE_ID = "schizophrenia";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private SchizophreniaRuntime runtime;

    public SchizophreniaModule(BigCasares plugin) {
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
        if (plugin == null) {
            return;
        }
        SchizophreniaSettings settings = SchizophreniaSettingsLoader.load(plugin.getConfig(),
            warning -> plugin.getLogger().warning("[Esquizofrenia] " + warning));
        VoidRealm voidRealm = settings.voidPlace().enabled()
            ? new VoidRealm(plugin, settings.voidPlace().worldName()) : null;
        if (voidRealm != null && voidRealm.world() == null) {
            plugin.getLogger().warning("[Esquizofrenia] No se pudo crear el mundo vacío; sin visitas al lugar negro.");
            voidRealm = null;
        }
        runtime = new SchizophreniaRuntime(plugin, settings, System::currentTimeMillis, voidRealm);
        SchizophreniaRuntime ownedRuntime = runtime;
        LobotomizerItem lobotomizer = new LobotomizerItem(plugin);
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.ownCleanup("schizophrenia-runtime", ownedRuntime::shutdown);
        ownedRuntime.removeStrayPhantoms();

        NamespacedKey recipeKey = null;
        if (settings.lobotomizer().recipeEnabled()) {
            recipeKey = lobotomizer.recipeKey();
            registrations.registerRecipe("lobotomizer-recipe", recipeKey, lobotomizer.recipe());
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.discoverRecipe(recipeKey);
            }
        }
        registrations.registerListener("schizophrenia-listener",
            new SchizophreniaListener(ownedRuntime, lobotomizer, recipeKey));
        registrations.scheduleRepeating("schizophrenia-tick", ownedRuntime::tick,
            SchizophreniaRuntime.TICK_PERIOD, SchizophreniaRuntime.TICK_PERIOD);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ownedRuntime.resume(player);
        }

        PluginCommand pluginCommand = plugin.getCommand("esquizo");
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: esquizo");
        }
        SchizophreniaCommand command = new SchizophreniaCommand(plugin.getServer(), ownedRuntime, lobotomizer);
        registrations.bindCommand("schizophrenia-command", pluginCommand, command, command);
        plugin.getLogger().info("[Esquizofrenia] Módulo activo.");
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.shutdown();
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        runtime = null;
    }
}
