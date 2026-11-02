package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Optional Tideframe screens for BigCasares (module {@value #ID}). Tideframe stays a separate plugin with its own pack:
 * this module only finds its API service, installs BigCasares' {@code bigc_} screens once per version and relays clicks
 * to the features. Without Tideframe (or with an API that does not match) {@link #ui()} is {@link TideframeUi#NONE} and
 * every feature keeps its inventory, form or chat menu. Config: {@code tideframe-ui.missions} and {@code .shop}.
 */
public final class TideframeUiModule implements PluginModule {
    public static final String ID = "tideframe-ui";
    static final String PLUGIN = "Tideframe";
    /** The screens BigCasares installs into Tideframe (src/main/resources/tideframe/screens). */
    static final List<String> SCREENS = List.of("bigc_misiones", "bigc_tienda");

    private final JavaPlugin plugin;
    private final ClientPlatformGateway platforms;
    private volatile TideframeUi ui = TideframeUi.NONE;
    private volatile boolean missions = true;
    private volatile boolean shop = true;
    private BukkitRuntimeRegistrations registrations;
    private RuntimeRegistrationScope compatibilityScope;
    private TideframeUiService service;
    private Listener relay;
    private long sequence;

    public TideframeUiModule(JavaPlugin plugin, ClientPlatformGateway platforms) {
        this.plugin = plugin;
        this.platforms = Objects.requireNonNull(platforms, "platforms");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        this.registrations = new BukkitRuntimeRegistrations(plugin, scope);
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(ID);
        this.missions = config == null || config.getBoolean("missions", true);
        this.shop = config == null || config.getBoolean("shop", true);
        scope.register("tideframe-connection", this::disconnect);
        registrations.registerListener("tideframe-plugin-state", new PluginState());
        registrations.registerListener("tideframe-players", new Players());
        connect();
    }

    @Override
    public void onDisable() {
        disconnect();
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    /**
     * From BigCasares' onLoad, before any plugin enables. Tideframe reads its screens folder while it enables (on another
     * thread, and starts watching the folder only afterwards), so screens first written by {@link #onEnable} could miss
     * its first pack until /tideframe reload or the next restart. The module switch is read straight from config.yml so
     * that the plugin's configuration is not loaded before onEnable.
     */
    public static void installBeforeTideframeStarts(JavaPlugin plugin) {
        Plugin tideframe = plugin.getServer().getPluginManager().getPlugin(PLUGIN);
        if (tideframe == null) {
            return;
        }
        File config = new File(plugin.getDataFolder(), "config.yml");
        if (config.isFile() && !YamlConfiguration.loadConfiguration(config).getBoolean("modules." + ID + ".enabled", true)) {
            return;
        }
        installScreens(plugin, tideframe, true);
    }

    /** Tideframe for the missions menu, or {@link TideframeUi#NONE} (also when switched off in config). */
    public TideframeUi missions() {
        return missions ? ui : TideframeUi.NONE;
    }

    /** Tideframe for the shop, or {@link TideframeUi#NONE} (also when switched off in config). */
    public TideframeUi shop() {
        return shop ? ui : TideframeUi.NONE;
    }

    /** Navigation safety stays active even when an individual feature uses only its classic menu. */
    public TideframeUi navigation() {
        return ui;
    }

    private synchronized void connect() {
        if (service != null || registrations == null) {
            return;
        }
        Plugin tideframe = plugin.getServer().getPluginManager().getPlugin(PLUGIN);
        if (tideframe == null || !tideframe.isEnabled()) {
            plugin.getLogger().info("Tideframe no esta instalado: misiones y tienda usan sus menus de siempre.");
            return;
        }
        TideframeBridge bridge;
        Listener createdRelay;
        TideframeUiService created;
        try {
            bridge = TideframeLink.bridge(plugin.getServer());
            if (bridge == null) {
                plugin.getLogger().warning("Tideframe no registro su servicio TideframeApi: se usan los menus de siempre.");
                return;
            }
            BukkitRuntimeRegistrations owner = registrations;
            TideframePackSetting ownPack = new TideframePackSetting(tideframe.getDataFolder().toPath().resolve("config.yml"));
            if (!ownPack.getAsBoolean()) {
                plugin.getLogger().warning("Tideframe tiene pack.send: false: cualquier pack cargado cuenta como el suyo y no se puede"
                    + " comprobar que el jugador tenga sus recursos, asi que misiones y tienda usan sus menus de siempre.");
            }
            created = new TideframeUiService(bridge, platforms, ownPack, task -> owner.scheduleImmediate("tideframe-open", task),
                this::incompatible);
            createdRelay = TideframeLink.relay(created);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException incompatible) {
            plugin.getLogger().log(Level.WARNING, "La API de Tideframe instalada no coincide con la que espera BigCasares: se usan los menus de siempre.", incompatible);
            return;
        }
        installScreens(plugin, tideframe, false);
        registrations.registerListener("tideframe-events-" + (++sequence), createdRelay);
        this.relay = createdRelay;
        this.service = created;
        this.ui = created;
        plugin.getLogger().info("Tideframe conectado: misiones y tienda se abren con sus pantallas para jugadores de Java con su pack cargado.");
    }

    /** BigCasares goes away: its own screens close; Tideframe and other plugins' screens stay. */
    private synchronized void disconnect() {
        TideframeUiService current = service;
        drop();
        if (current != null) {
            try {
                current.closeOwnScreens(plugin.getServer().getOnlinePlayers());
            } catch (RuntimeException | LinkageError closing) {
                plugin.getLogger().log(Level.FINE, "Could not close BigCasares Tideframe screens", closing);
            }
        }
    }

    /** A call found Tideframe's API different after all: forget it, so every feature uses its own menus again. */
    private void incompatible(LinkageError changed) {
        plugin.getLogger().log(Level.WARNING, "La API de Tideframe no coincide con la que espera BigCasares: se vuelven a usar los menus de siempre.", changed);
        drop();
    }

    /** Tideframe itself goes away: forget it without calling it. */
    private synchronized void drop() {
        ui = TideframeUi.NONE;
        service = null;
        Listener current = relay;
        relay = null;
        if (current != null) {
            HandlerList.unregisterAll(current);
        }
    }

    /**
     * Writes the screens that changed. {@code beforeStart}: Tideframe has not enabled yet and bakes them into its first
     * pack; otherwise it loads them when it reloads its screens (by itself with auto-reload, or /tideframe reload).
     */
    private static void installScreens(JavaPlugin plugin, Plugin tideframe, boolean beforeStart) {
        List<TideframeScreenInstaller.BundledScreen> bundled = new ArrayList<>();
        for (String id : SCREENS) {
            try (InputStream in = plugin.getResource("tideframe/screens/" + id + ".json")) {
                if (in == null) {
                    plugin.getLogger().warning("Falta la pantalla " + id + " dentro del JAR de BigCasares.");
                    continue;
                }
                bundled.add(new TideframeScreenInstaller.BundledScreen(id, in.readAllBytes()));
            } catch (IOException failure) {
                plugin.getLogger().log(Level.WARNING, "No se pudo leer la pantalla " + id, failure);
            }
        }
        Path screens = tideframe.getDataFolder().toPath().resolve("screens");
        try {
            for (var result : TideframeScreenInstaller.install(screens, plugin.getDataFolder().toPath().resolve("tideframe"), bundled)) {
                switch (result.outcome()) {
                    case INSTALLED, UPDATED -> plugin.getLogger().info("Pantalla de Tideframe " + result.id() + ": "
                        + (result.outcome() == TideframeScreenInstaller.Outcome.INSTALLED ? "instalada" : "actualizada")
                        + (beforeStart ? "; entra en el pack que arma Tideframe al arrancar."
                            : ". Tideframe la carga al recargar sus pantallas (solo, o con /tideframe reload)."));
                    case KEPT_EDITED -> plugin.getLogger().info("Pantalla de Tideframe " + result.id()
                        + ": se conserva la version editada; la nueva queda en plugins/BigCasares/tideframe/" + result.id() + ".bundled.json.");
                    case UNCHANGED -> { }
                }
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron instalar las pantallas de BigCasares en Tideframe", failure);
        }
    }

    private final class Players implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            TideframeUiService current = service;
            if (current != null) {
                current.quit(event.getPlayer());
            }
        }
    }

    private final class PluginState implements Listener {
        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            if (PLUGIN.equals(event.getPlugin().getName())) {
                connect();
            }
        }

        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            if (PLUGIN.equals(event.getPlugin().getName())) {
                drop();
            }
        }
    }
}
