package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.bounties.BountyEconomyGateway;
import dev.linqfy.bigCasares.platform.BedrockItemIcons;
import dev.linqfy.bigCasares.platform.FeedbackSounds;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ShopModule implements PluginModule {

    private final BigCasares plugin;

    private ShopCatalog catalog = new ShopCatalog(java.util.List.of());
    private ShopService service;
    private ShopEconomyGateway economyGateway;
    private ShopGuiController guiController;
    private ShopNpcFactory npcFactory;
    private ShopNpcListener npcListener;
    private final List<org.bukkit.entity.Entity> npcs = new ArrayList<>();
    private ClientPlatformGateway platformGateway = ignored -> ClientPlatform.JAVA;
    private BedrockFormSender bedrockFormSender;
    private java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframe =
        () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE;
    private ShopTideframeMenu tideframeMenu;
    private java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframeNavigation =
        () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE;
    private final ShopFormGeneration formGeneration = new ShopFormGeneration();
    /** The id of the Bedrock form's quantity button; never a catalog entry id (those come from shop.yml keys). */
    static final String QUANTITY_BUTTON = "#cantidad";
    /** Each Bedrock player's chosen quantity of lots per tap (1 when not set). */
    private final Map<UUID, Integer> bedrockLots = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean enabled;
    private BukkitRuntimeRegistrations registrations;
    private RuntimeRegistrationScope compatibilityScope;
    private long runtimeResourceSequence;

    public ShopModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "entity-shop-system";
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
        scope.register("module-state", this::clearRuntimeState);
        plugin.saveResource("shop.yml", false);
        reload();
    }

    @Override
    public void onDisable() {
        enabled = false;
        formGeneration.invalidate();
        if (guiController != null) {
            guiController.closeAll();
            org.bukkit.event.HandlerList.unregisterAll(guiController);
        }
        if (npcListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(npcListener);
            npcListener = null;
        }
        despawnNpcs();
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    public BigCasares plugin() {
        return plugin;
    }

    public void reload() {
        enabled = false;
        formGeneration.invalidate();
        Economy economy = BountyEconomyGateway.resolveOrThrow(plugin);
        File file = new File(plugin.getDataFolder(), "shop.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        this.catalog = new ShopCatalogLoader().load(configuration);
        this.economyGateway = new ShopEconomyGateway(plugin, economy);
        this.service = new ShopService(
            new ShopItemResolver(plugin.getCustomItemRegistry()),
            economyGateway,
            plugin::enforceInventoryLimits
        );
        if (guiController != null) {
            guiController.closeAll();
            org.bukkit.event.HandlerList.unregisterAll(guiController);
        }
        this.guiController = new ShopGuiController(this, catalog, service);
        ShopGuiController ownedGuiController = guiController;
        registrations.registerListener(nextResourceId("shop-gui-listener"), ownedGuiController);
        registrations.ownCleanup(nextResourceId("shop-gui-windows"), ownedGuiController::closeAll);
        // Java players with Tideframe get the same shop on its screen: same catalog, same service, one call per click.
        if (tideframeMenu != null) {
            tideframeMenu.close();
        }
        ShopService ownedService = service;
        ShopEconomyGateway ownedEconomy = economyGateway;
        ShopCatalog ownedCatalog = catalog;
        ShopTideframeMenu menu = new ShopTideframeMenu(() -> ownedCatalog, new ShopTideframeMenu.Actions() {
            @Override public ShopTransactionResult buy(Player player, ShopEntry entry, int lots) {
                return withSound(player, ownedService.buy(player, entry, lots));
            }
            @Override public ShopTransactionResult sell(Player player, ShopEntry entry, int lots) {
                return withSound(player, ownedService.sell(player, entry, lots));
            }
            @Override public String balance(Player player) { return ownedEconomy.format(ownedEconomy.balance(player)); }
            @Override public double funds(Player player) { return ownedEconomy.balance(player); }
            @Override public int owned(Player player, ShopEntry entry) { return ownedService.owned(player, entry); }
            @Override public String format(double amount) { return ownedEconomy.format(amount); }
        }, () -> tideframe.get());
        this.tideframeMenu = menu;
        registrations.ownCleanup(nextResourceId("shop-tideframe"), menu::close);
        if (npcListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(npcListener);
        }
        despawnNpcs();
        this.npcFactory = new ShopNpcFactory(plugin);
        this.npcListener = new ShopNpcListener(npcFactory, this::openNpcShop);
        registrations.registerListener(nextResourceId("shop-npc-listener"), npcListener);
        spawnConfiguredNpcs(configuration);
        this.enabled = true;
        formGeneration.activateNext();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void openMainMenu(Player player) {
        if (!enabled || guiController == null) {
            player.sendMessage("§cEl shop no esta disponible.");
            return;
        }
        if (platformGateway.resolvePlatform(player.getUniqueId()) == ClientPlatform.BEDROCK
            && bedrockFormSender != null) {
            openBedrockCategories(player);
            return;
        }
        ShopTideframeMenu menu = tideframeMenu;
        if (menu != null && menu.openCategories(player, () -> openClassicCategories(player))) {
            return;
        }
        openClassicCategories(player);
    }

    public void configurePlatform(ClientPlatformGateway platformGateway, BedrockFormSender bedrockFormSender) {
        this.platformGateway = platformGateway == null ? ignored -> ClientPlatform.JAVA : platformGateway;
        this.bedrockFormSender = bedrockFormSender;
    }

    /** Where the Tideframe screens come from (the Tideframe module, or none). Bedrock keeps its forms either way. */
    public void configureTideframe(java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframe) {
        this.tideframe = tideframe == null ? () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE : tideframe;
    }

    /** The inventory menu, when Tideframe could not open after all. */
    private void openClassicCategories(Player player) {
        if (enabled && guiController != null && player.isOnline()) {
            tideframeNavigation.get().openClassic(player, () -> guiController.openCategories(player));
        }
    }

    public void configureTideframeNavigation(java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> navigation) {
        this.tideframeNavigation = navigation;
    }

    private void openClassicCategory(Player player, ShopCategory category) {
        if (enabled && guiController != null && player.isOnline()) {
            tideframeNavigation.get().openClassic(player, () -> guiController.openCategory(player, category));
        }
    }

    private void openNpcShop(Player player, String shopId) {
        ShopCategory category = catalog.category(shopId).orElse(null);
        if (category == null) {
            openMainMenu(player);
            return;
        }
        if (platformGateway.resolvePlatform(player.getUniqueId()) == ClientPlatform.BEDROCK
            && bedrockFormSender != null) {
            openBedrockCategory(player, category);
        } else {
            ShopTideframeMenu menu = tideframeMenu;
            if (menu == null || !menu.openCategory(player, category, () -> openClassicCategory(player, category))) {
                openClassicCategory(player, category);
            }
        }
    }

    private void openBedrockCategories(Player player) {
        UUID playerId = player.getUniqueId();
        long generation = formGeneration.current();
        ShopView view = new ShopView(
            "categories",
            "§6§lTienda BigCasares",
            "§7Elegí una categoría",
            economyGateway.format(economyGateway.balance(player)),
            catalog.categories().stream()
                .map(category -> new ShopViewItem(category.id(), category.name(), "", "", null, true,
                    BedrockItemIcons.path(category.icon()).orElse(null)))
                .toList()
        );
        BukkitRuntimeRegistrations activeRegistrations = registrations;
        boolean sent = bedrockFormSender.send(playerId, view,
            index -> activeRegistrations.guard(
                () -> handleBedrockCategoryResponse(playerId, generation, view, index)).run());
        if (!sent) {
            new ChatFallbackShopUi(plugin.getServer()).openShop(player.getUniqueId(), view);
        }
    }

    private void openBedrockCategory(Player player, ShopCategory category) {
        UUID playerId = player.getUniqueId();
        long generation = formGeneration.current();
        int lots = bedrockLots.getOrDefault(playerId, 1);
        double funds = economyGateway.balance(player);
        List<ShopViewItem> items = bedrockCategoryItems(category, lots, funds, economyGateway::format);
        ShopView view = new ShopView(category.id(), category.name(), "§7Tocá un objeto para comprarlo",
            economyGateway.format(funds), items);
        BukkitRuntimeRegistrations activeRegistrations = registrations;
        boolean sent = bedrockFormSender.send(playerId, view,
            index -> activeRegistrations.guard(
                () -> handleBedrockPurchaseResponse(playerId, generation, category.id(), view, lots, index)).run());
        if (!sent) {
            new ChatFallbackShopUi(plugin.getServer()).openShop(player.getUniqueId(), new ShopView(view.id(), view.title(),
                view.description(), view.balance(), items.subList(1, items.size())));
        }
    }

    private void handleBedrockCategoryResponse(UUID playerId, long generation, ShopView view, int index) {
        Player player = resolveActiveFormPlayer(playerId, generation);
        if (player == null || index < 0 || index >= view.items().size()) {
            return;
        }
        String categoryId = view.items().get(index).id();
        catalog.category(categoryId).ifPresent(category -> openBedrockCategory(player, category));
    }

    /**
     * The Bedrock category form's buttons: first the quantity a tap buys (as the Java screen's "Cant.", x1, x4, x16 in
     * turn), then each product with the name and price of that quantity, red with what is missing when the balance
     * does not cover it, and its vanilla Bedrock icon.
     */
    static List<ShopViewItem> bedrockCategoryItems(ShopCategory category, int lots, double funds,
                                                   java.util.function.DoubleFunction<String> money) {
        List<ShopViewItem> items = new ArrayList<>();
        items.add(new ShopViewItem(QUANTITY_BUTTON, "§bCantidad: x" + lots + " §7(tocá para cambiar)", "", "", null, true));
        for (ShopEntry entry : category.entries()) {
            double price = entry.buyPrice() * lots;
            int count = entry.amount() * lots;
            String name = entry.displayName() == null ? entry.id() : entry.displayName();
            items.add(new ShopViewItem(
                entry.id(),
                count > 1 ? name + " §7x" + count : name,
                String.join("\n", entry.lore()),
                money.apply(price) + (funds < price ? " (faltan " + money.apply(price - funds) + ")" : ""),
                null,
                funds >= price,
                // A custom item has no vanilla icon on Bedrock: its row keeps the name and price only.
                entry.customItemId() == null ? BedrockItemIcons.path(entry.material()).orElse(null) : null
            ));
        }
        return items;
    }

    /**
     * A tap on the Bedrock category form: the quantity button switches to the next quantity and shows the form again;
     * a product row buys the quantity that form showed, as one operation.
     */
    private void handleBedrockPurchaseResponse(UUID playerId, long generation, String categoryId, ShopView view, int lots,
                                               int index) {
        Player player = resolveActiveFormPlayer(playerId, generation);
        if (player == null || index < 0 || index >= view.items().size()) {
            return;
        }
        if (QUANTITY_BUTTON.equals(view.items().get(index).id())) {
            bedrockLots.put(playerId, ShopTideframeMenu.next(lots));
            catalog.category(categoryId).ifPresent(category -> openBedrockCategory(player, category));
            return;
        }
        ShopPurchaseRequest request = new BedrockShopFormMapper().purchaseForButton(playerId, view, index);
        catalog.entry(request.shopId(), request.entryId()).ifPresent(entry -> {
            ShopService activeService = service;
            if (activeService == null) {
                return;
            }
            ShopTransactionResult result = withSound(player, activeService.buy(player, entry, lots));
            player.sendMessage((result.success() ? "§a" : "§c") + result.message());
        });
    }

    /** The sound of a purchase or sale (ok or refused) for the player who made it. */
    private static ShopTransactionResult withSound(Player player, ShopTransactionResult result) {
        FeedbackSounds.result(player, result.success());
        return result;
    }

    private Player resolveActiveFormPlayer(UUID playerId, long generation) {
        Player player = plugin.getServer().getPlayer(playerId);
        boolean online = player != null && player.isOnline();
        return enabled && formGeneration.accepts(generation, online) ? player : null;
    }

    private void spawnConfiguredNpcs(YamlConfiguration configuration) {
        // Purge existing NPCs in worlds to avoid duplicates
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (npcFactory.isShopNpc(entity)) {
                    entity.remove();
                }
            }
        }

        ConfigurationSection shops = configuration.getConfigurationSection("shops");
        if (shops == null) {
            return;
        }
        for (String shopId : shops.getKeys(false)) {
            ConfigurationSection npc = shops.getConfigurationSection(shopId + ".npc");
            if (npc == null || !npc.getBoolean("enabled", false)) {
                continue;
            }
            ConfigurationSection location = npc.getConfigurationSection("location");
            if (location == null) {
                plugin.getLogger().warning("Shop NPC " + shopId + " has no location.");
                continue;
            }
            org.bukkit.World world = Bukkit.getWorld(location.getString("world", "world"));
            if (world == null) {
                plugin.getLogger().warning("Shop NPC world is not loaded for " + shopId);
                continue;
            }
            ShopNpcDefinition definition = loadNpcDefinition(shopId, npc);
            Location spawn = new Location(
                world,
                location.getDouble("x"), location.getDouble("y"), location.getDouble("z"),
                (float) location.getDouble("yaw"), (float) location.getDouble("pitch")
            );
            try {
                java.util.List<org.bukkit.entity.Entity> spawnedEntities = npcFactory.spawn(spawn, definition);
                for (org.bukkit.entity.Entity e : spawnedEntities) {
                    npcs.add(e);
                    registrations.ownCleanup(nextResourceId("shop-npc"), () -> removeNpc(e));
                }
            } catch (IllegalArgumentException | IllegalStateException ex) {
                plugin.getLogger().warning("No se pudo crear el NPC de shop " + shopId + ": " + ex.getMessage());
            }
        }
    }

    private ShopNpcDefinition loadNpcDefinition(String shopId, ConfigurationSection npc) {
        ShopNpcType type = ShopNpcType.valueOf(npc.getString("type", "VILLAGER").toUpperCase(Locale.ROOT));
        ConfigurationSection skin = npc.getConfigurationSection("skin");
        ShopSkinSource skinSource = type == ShopNpcType.PLAYER_MODEL
            ? ShopSkinSource.valueOf(skin == null ? "PLAYER_NAME" : skin.getString("source", "PLAYER_NAME").toUpperCase(Locale.ROOT))
            : null;
        String skinValue = skin == null ? null : skin.getString("value", skin.getString("player-name"));
        Map<String, String> equipment = new LinkedHashMap<>();
        ConfigurationSection equipmentSection = npc.getConfigurationSection("equipment");
        if (equipmentSection != null) {
            equipmentSection.getKeys(false).forEach(key -> equipment.put(key, equipmentSection.getString(key, "AIR")));
        }
        return new ShopNpcDefinition(
            shopId,
            type,
            npc.getString("display-name", "§6§lMercader"),
            skinSource,
            skinValue,
            Pose.valueOf(npc.getString("pose", "STANDING").toUpperCase(Locale.ROOT)),
            npc.getBoolean("look-at-player", true),
            npc.getBoolean("immovable", true),
            equipment,
            npc.getString("profession", "none"),
            npc.getString("biome-type", "plains"),
            npc.getInt("level", 1),
            npc.getBoolean("baby", false)
        );
    }

    private void despawnNpcs() {
        npcs.forEach(this::removeNpc);
        npcs.clear();
    }

    private void removeNpc(org.bukkit.entity.Entity entity) {
        if (entity != null) {
            ClientEntityPresentationRegistry.unregister(entity.getUniqueId());
        }
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private String nextResourceId(String prefix) {
        return prefix + "-" + ++runtimeResourceSequence;
    }

    private void clearRuntimeState() {
        enabled = false;
        formGeneration.invalidate();
        bedrockLots.clear();
        npcs.clear();
        guiController = null;
        npcListener = null;
        npcFactory = null;
        service = null;
        economyGateway = null;
        registrations = null;
    }
}
